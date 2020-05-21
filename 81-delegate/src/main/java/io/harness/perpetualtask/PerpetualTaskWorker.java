package io.harness.perpetualtask;

import static io.harness.delegate.service.DelegateAgentServiceImpl.getDelegateId;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.util.Durations;

import io.grpc.StatusRuntimeException;
import io.harness.flow.BackoffScheduler;
import io.harness.logging.AutoLogContext;
import io.harness.logging.LoggingListener;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.perpetualtask.grpc.PerpetualTaskServiceGrpcClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Singleton
public class PerpetualTaskWorker {
  @Getter private Set<PerpetualTaskAssignDetails> assignedTasks;
  @Getter private final Map<PerpetualTaskId, PerpetualTaskAssignRecord> runningTaskMap = new ConcurrentHashMap<>();

  private ScheduledExecutorService scheduledService;

  private final BackoffScheduler backoffScheduler;
  private final TimeLimiter timeLimiter;
  private final PerpetualTaskServiceGrpcClient perpetualTaskServiceGrpcClient;
  private Map<String, PerpetualTaskExecutor> factoryMap;

  private AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicReference<PerpetualTaskWorkerService> svcHolder = new AtomicReference<>();

  private class PerpetualTaskWorkerService extends AbstractScheduledService {
    PerpetualTaskWorkerService() {
      addListener(new LoggingListener(this), MoreExecutors.directExecutor());
    }

    @Override
    protected void runOneIteration() {
      handleTasks();
    }

    @Override
    protected Scheduler scheduler() {
      return backoffScheduler;
    }
  }

  @Inject
  public PerpetualTaskWorker(PerpetualTaskServiceGrpcClient perpetualTaskServiceGrpcClient,
      Map<String, PerpetualTaskExecutor> factoryMap, TimeLimiter timeLimiter) {
    this.factoryMap = factoryMap;
    this.timeLimiter = timeLimiter;
    this.perpetualTaskServiceGrpcClient = perpetualTaskServiceGrpcClient;
    scheduledService = new ManagedScheduledExecutorService("perpetual-task-worker");
    backoffScheduler = new BackoffScheduler(getClass().getSimpleName(), Duration.ofMinutes(4), Duration.ofMinutes(14));
  }

  private void handleTasks() {
    try {
      updateAssignedTaskIds();
      stopCancelledTasks();
      startAssignedTasks();
      backoffScheduler.recordSuccess();
    } catch (StatusRuntimeException ex) {
      logger.error("Grpc status exception in perpetual task worker. Backing off...", ex);
      backoffScheduler.recordFailure();
    } catch (Exception ex) {
      logger.error("Exception in perpetual task worker ", ex);
    }
  }

  void updateAssignedTaskIds() {
    String delegateId = getDelegateId().orElse("UNREGISTERED");
    assignedTasks = new HashSet<>(perpetualTaskServiceGrpcClient.perpetualTaskList(delegateId));
    logger.debug("Refreshed list of assigned perpetual tasks {}", assignedTasks);
  }

  @VisibleForTesting
  void startTask(PerpetualTaskAssignDetails task) {
    try (AutoLogContext ignore1 = new PerpetualTaskLogContext(task.getTaskId().getId(), OVERRIDE_ERROR)) {
      try {
        logger.info("Starting perpetual task with id: {}.", task.getTaskId().getId());
        PerpetualTaskContext context = perpetualTaskServiceGrpcClient.perpetualTaskContext(task.getTaskId());
        PerpetualTaskSchedule schedule = context.getTaskSchedule();
        long intervalSeconds = Durations.toSeconds(schedule.getInterval());

        PerpetualTaskLifecycleManager perpetualTaskLifecycleManager = new PerpetualTaskLifecycleManager(
            task.getTaskId(), context, factoryMap, perpetualTaskServiceGrpcClient, timeLimiter);
        ScheduledFuture<?> taskHandle = scheduledService.scheduleWithFixedDelay(
            perpetualTaskLifecycleManager::startTask, 0, intervalSeconds, TimeUnit.SECONDS);

        PerpetualTaskHandle perpetualTaskHandle = new PerpetualTaskHandle(taskHandle, perpetualTaskLifecycleManager);

        PerpetualTaskAssignRecord perpetualTaskAssignRecord = PerpetualTaskAssignRecord.builder()
                                                                  .perpetualTaskHandle(perpetualTaskHandle)
                                                                  .perpetualTaskAssignDetails(task)
                                                                  .build();

        runningTaskMap.put(task.getTaskId(), perpetualTaskAssignRecord);
      } catch (Exception ex) {
        logger.error("Exception in starting perpetual task ", ex);
      }
    }
  }

  @VisibleForTesting
  void startAssignedTasks() {
    if (assignedTasks == null) {
      return;
    }
    for (PerpetualTaskAssignDetails task : assignedTasks) {
      // start the task if the task is not currently running
      if (!runningTaskMap.containsKey(task.getTaskId())) {
        startTask(task);
      }
    }
  }

  @VisibleForTesting
  void stopTask(PerpetualTaskId taskId) {
    try (AutoLogContext ignore1 = new PerpetualTaskLogContext(taskId.getId(), OVERRIDE_ERROR)) {
      PerpetualTaskAssignRecord perpetualTaskAssignRecord = runningTaskMap.get(taskId);
      if (perpetualTaskAssignRecord == null) {
        logger.error(
            "The request to delete a task with id={} cannot be fulfilled since such task does not exist.", taskId);
        return;
      }
      perpetualTaskAssignRecord.getPerpetualTaskHandle().getTaskLifecycleManager().stopTask();
      perpetualTaskAssignRecord.getPerpetualTaskHandle().getTaskHandle().cancel(true);
      runningTaskMap.remove(taskId);
    }
  }

  private void stopCancelledTasks() {
    for (PerpetualTaskId taskId : runningTaskMap.keySet()) {
      Optional<PerpetualTaskAssignDetails> perpetualTaskAssignDetailsOptional =
          assignedTasks.stream().filter(taskDetails -> taskId.equals(taskDetails.getTaskId())).findFirst();

      if (!perpetualTaskAssignDetailsOptional.isPresent()) {
        logger.info("Stopping the task with id: {}", taskId.getId());
        stopTask(taskId);
      }
    }
  }

  public void start() {
    if (running.compareAndSet(false, true)) {
      PerpetualTaskWorkerService perpetualTaskWorkerService = new PerpetualTaskWorkerService();
      perpetualTaskWorkerService.startAsync();
      this.svcHolder.set(perpetualTaskWorkerService);
    }
  }
  public void stop() {
    if (running.compareAndSet(true, false)) {
      PerpetualTaskWorkerService perpetualTaskWorkerService = this.svcHolder.get();
      perpetualTaskWorkerService.stopAsync().awaitTerminated();
      this.assignedTasks = Collections.emptySet();
      this.stopCancelledTasks();
    }
  }
}
