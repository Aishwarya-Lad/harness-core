/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.metrics.services.impl;

import static io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus.QUEUED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static dev.morphia.aggregation.Accumulator.accumulator;
import static dev.morphia.aggregation.Group.grouping;
import static dev.morphia.aggregation.Group.id;

import io.harness.CVNGPrometheusExporterUtils;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskKeys;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.analysis.entities.VerificationTaskBase.VerificationTaskBaseKeys;
import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.cdng.entities.CVNGStepTask;
import io.harness.cvng.cdng.entities.CVNGStepTask.CVNGStepTaskKeys;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.DataCollectionTask.DataCollectionTaskKeys;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine.AnalysisStateMachineKeys;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceKeys;
import io.harness.metrics.AutoMetricContext;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.metrics.beans.AccountMetricContext;
import io.harness.metrics.beans.MetricConfiguration;
import io.harness.metrics.service.api.MetricDefinitionInitializer;
import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.api.MetricsPublisher;
import io.harness.mongo.metrics.HarnessConnectionPoolListener;
import io.harness.mongo.metrics.HarnessConnectionPoolStatistics;
import io.harness.mongo.metrics.MongoMetricsContext;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.mongodb.AggregationOptions;
import com.mongodb.connection.ServerId;
import dev.morphia.aggregation.AggregationPipeline;
import dev.morphia.annotations.Id;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class CVNGMetricsPublisher implements MetricsPublisher, MetricDefinitionInitializer {
  private static final String METRIC_PREFIX_FOR_DB = "io_harness_cvng_mongodb_";
  private static final Pattern METRIC_NAME_RE = Pattern.compile("[^a-zA-Z0-9_]");
  private static final String CONNECTION_POOL_SIZE = "connection_pool_size";
  private static final String CONNECTIONS_CHECKED_OUT = "connections_checked_out";
  private static final String CONNECTION_POOL_MAX_SIZE = "connection_pool_max_size";
  private static final String NAMESPACE = System.getenv("NAMESPACE");
  private static final String CONTAINER_NAME = System.getenv("CONTAINER_NAME");
  private static final Map<Class<? extends PersistentEntity>, QueryParams> TASKS_INFO = new HashMap<>();
  private static final String ENV = isEmpty(System.getenv("ENV")) ? "localhost" : System.getenv("ENV");

  private static final Double SNAPSHOT_FACTOR = 1.0D / (double) TimeUnit.SECONDS.toNanos(1L);
  public static final String MEAN_SUFFIX = "_mean";

  private static final Map<String, Boolean> LE_TASKS_METRICS_TO_BE_RECORDED = new HashMap<>();
  @Inject private Clock clock;
  @Inject private HarnessMetricRegistry metricRegistry;
  @Inject private HarnessConnectionPoolListener harnessConnectionPoolListener;

  static {
    TASKS_INFO.put(CVNGStepTask.class,
        QueryParams.builder()
            .nonFinalStatuses(CVNGStepTask.getNonFinalStatues())
            .statusField(CVNGStepTaskKeys.status)
            .name("cvng_step_task")
            .build());
    TASKS_INFO.put(DataCollectionTask.class,
        QueryParams.builder()
            .nonFinalStatuses(DataCollectionExecutionStatus.getNonFinalStatuses())
            .statusField(DataCollectionTaskKeys.status)
            .name("data_collection_task")
            .build());
    TASKS_INFO.put(VerificationJobInstance.class,
        QueryParams.builder()
            .nonFinalStatuses(VerificationJobInstance.ExecutionStatus.nonFinalStatuses())
            .statusField(VerificationJobInstanceKeys.executionStatus)
            .name("verification_job_instance")
            .build());
    TASKS_INFO.put(LearningEngineTask.class,
        QueryParams.builder()
            .nonFinalStatuses(LearningEngineTask.ExecutionStatus.getNonFinalStatues())
            .statusField(LearningEngineTaskKeys.taskStatus)
            .name("learning_engine_task")
            .build());
    TASKS_INFO.put(AnalysisStateMachine.class,
        QueryParams.builder()
            .nonFinalStatuses(AnalysisStatus.getCountMetricsNonFinalStatuses())
            .statusField(AnalysisStateMachineKeys.status)
            .name("analysis_state_machine")
            .build());
    LE_TASKS_METRICS_TO_BE_RECORDED.put("learning_engine_service_health_task", false);
    LE_TASKS_METRICS_TO_BE_RECORDED.put("learning_engine_deployment_task", true);
  }
  @Inject private MetricService metricService;
  @Inject private HPersistence hPersistence;

  @Override
  public void recordMetrics() {
    try {
      sendTaskStatusMetrics();
      sendLETaskStatusMetrics();
      sendLEAutoscaleMetrics();
      recordDBMetrics();
      recordMeanForTimers();
    } catch (Exception ignored) {
      log.warn("Metric could not be recorded.");
    }
  }

  public void recordDBMetrics() {
    ConcurrentMap<ServerId, HarnessConnectionPoolStatistics> map = harnessConnectionPoolListener.getStatistics();
    map.forEach((serverId, harnessConnectionPoolStatistics) -> {
      String serverAddress = sanitizeName(serverId.getAddress().toString());
      String clientDescription = sanitizeName(serverId.getClusterId().getDescription());
      try (MongoMetricsContext ignore =
               new MongoMetricsContext(NAMESPACE, CONTAINER_NAME, serverAddress, clientDescription)) {
        metricRegistry.recordGaugeValue(METRIC_PREFIX_FOR_DB + CONNECTION_POOL_MAX_SIZE,
            CVNGPrometheusExporterUtils.contextLabels.values().toArray(new String[0]),
            harnessConnectionPoolStatistics.getMaxSize());
        metricRegistry.recordGaugeValue(METRIC_PREFIX_FOR_DB + CONNECTION_POOL_SIZE,
            CVNGPrometheusExporterUtils.contextLabels.values().toArray(new String[0]),
            harnessConnectionPoolStatistics.getSize());
        metricRegistry.recordGaugeValue(METRIC_PREFIX_FOR_DB + CONNECTIONS_CHECKED_OUT,
            CVNGPrometheusExporterUtils.contextLabels.values().toArray(new String[0]),
            harnessConnectionPoolStatistics.getCheckedOutCount());
      }
    });
  }

  public void recordMeanForTimers() {
    Set<Map.Entry<String, Timer>> webMetricsTimerSet =
        metricRegistry.getMetricRegistry()
            .getTimers(MetricFilter.startsWith("io.dropwizard.jetty.MutableServletContextHandler"))
            .entrySet();
    if (!metricRegistry.getNamesToCollectors().containsKey(
            "io_harness_custom_metric_io_dropwizard_jetty_MutableServletContextHandler_requests" + MEAN_SUFFIX)) {
      webMetricsTimerSet.forEach(entry
          -> metricRegistry.registerGaugeMetric(sanitizeName(entry.getKey()) + MEAN_SUFFIX,
              CVNGPrometheusExporterUtils.contextLabels.keySet().toArray(new String[0]),
              "Metrics from CVNG for Web server Servlet"));
    }
    webMetricsTimerSet.forEach(entry -> recordTimerMeanWithMetricRegistry(entry.getKey(), entry.getValue()));
  }

  private void recordTimerMeanWithMetricRegistry(String metricName, Timer timer) {
    metricRegistry.recordGaugeValue(sanitizeName(metricName) + MEAN_SUFFIX,
        CVNGPrometheusExporterUtils.contextLabels.values().toArray(new String[0]),
        timer.getSnapshot().getMean() * SNAPSHOT_FACTOR);
  }

  private static String sanitizeName(String labelName) {
    if (StringUtils.isEmpty(labelName)) {
      return labelName;
    }
    String name = METRIC_NAME_RE.matcher(labelName).replaceAll("_");
    if (!name.isEmpty() && Character.isDigit(name.charAt(0))) {
      name = "_" + name;
    }
    return name;
  }

  @VisibleForTesting
  void sendTaskStatusMetrics() {
    TASKS_INFO.forEach((clazz, queryParams) -> {
      Query<? extends PersistentEntity> query =
          hPersistence.createQuery(clazz).field(queryParams.getStatusField()).in(queryParams.getNonFinalStatuses());
      log.info("Starting getting tasks status based metrics {}", clazz.getSimpleName());
      long startTime = clock.instant().toEpochMilli();
      int limit = hPersistence.getMaxDocumentLimit(clazz);
      AggregationPipeline aggregationPipeline =
          hPersistence.getDatastore(clazz).createAggregation(clazz).match(query).group(
              id(grouping("accountId", "accountId")), grouping("count", accumulator("$sum", 1)));
      if (limit > 0) {
        aggregationPipeline.limit(limit);
      }
      aggregationPipeline
          .aggregate(InstanceCount.class,
              AggregationOptions.builder().maxTime(hPersistence.getMaxTimeMs(clazz), TimeUnit.MILLISECONDS).build())
          .forEachRemaining(instanceCount -> {
            try (AccountMetricContext accountMetricContext = new AccountMetricContext(instanceCount.id.accountId)) {
              metricService.recordMetric(getNonFinalStatusMetricName(queryParams.getName()), instanceCount.count);
            }
          });
      queryParams.getNonFinalStatuses().forEach(status -> {
        AggregationPipeline aggregatePipeline =
            hPersistence.getDatastore(clazz)
                .createAggregation(clazz)
                .match(hPersistence.createQuery(clazz).field(queryParams.getStatusField()).equal(status))
                .group(id(grouping("accountId", "accountId")), grouping("count", accumulator("$sum", 1)));
        if (limit > 0) {
          aggregatePipeline.limit(limit);
        }
        aggregatePipeline
            .aggregate(InstanceCount.class,
                AggregationOptions.builder().maxTime(hPersistence.getMaxTimeMs(clazz), TimeUnit.MILLISECONDS).build())
            .forEachRemaining(instanceCount -> {
              try (AutoMetricContext accountMetricContext = new AccountMetricContext(instanceCount.id.accountId)) {
                metricService.recordMetric(getStatusMetricName(queryParams, status.toString()), instanceCount.count);
              }
            });
      });
      log.info("Total time taken to collect metrics for class {} {} (ms)", clazz.getSimpleName(),
          clock.instant().toEpochMilli() - startTime);
    });
  }

  @VisibleForTesting
  void sendLETaskStatusMetrics() {
    Class<LearningEngineTask> clazz = LearningEngineTask.class;
    LE_TASKS_METRICS_TO_BE_RECORDED.forEach((metricName, isDeploymentTask) -> {
      Query<? extends PersistentEntity> query = hPersistence.createQuery(clazz);
      if (Boolean.TRUE.equals(isDeploymentTask)) {
        query = query.field(LearningEngineTaskKeys.analysisType).in(LearningEngineTaskType.getDeploymentTaskTypes());

      } else {
        query = query.field(LearningEngineTaskKeys.analysisType).notIn(LearningEngineTaskType.getDeploymentTaskTypes());
      }
      query =
          query.field(LearningEngineTaskKeys.taskStatus).in(LearningEngineTask.ExecutionStatus.getNonFinalStatues());
      log.info("Starting getting tasks status based metrics {}", metricName);
      long startTime = clock.instant().toEpochMilli();
      int limit = hPersistence.getMaxDocumentLimit(clazz);
      AggregationPipeline aggregationPipeline =
          hPersistence.getDatastore(clazz).createAggregation(clazz).match(query).group(
              id(grouping("accountId", "accountId")), grouping("count", accumulator("$sum", 1)));
      if (limit > 0) {
        aggregationPipeline.limit(limit);
      }
      aggregationPipeline
          .aggregate(InstanceCount.class,
              AggregationOptions.builder().maxTime(hPersistence.getMaxTimeMs(clazz), TimeUnit.MILLISECONDS).build())
          .forEachRemaining(instanceCount -> {
            try (AccountMetricContext accountMetricContext = new AccountMetricContext(instanceCount.id.accountId)) {
              metricService.recordMetric(getNonFinalStatusMetricName(metricName), instanceCount.count);
              metricService.recordMetric(getNonFinalStatusMetricNameWithEnv(metricName, ENV), instanceCount.count);
            }
          });
      LearningEngineTask.ExecutionStatus.getNonFinalStatues().forEach(status -> {
        AggregationPipeline aggregatePipeline = hPersistence.getDatastore(clazz).createAggregation(clazz).match(
            hPersistence.createQuery(clazz).field(LearningEngineTaskKeys.taskStatus).equal(status));
        if (isDeploymentTask) {
          aggregatePipeline = aggregatePipeline.match(hPersistence.createQuery(clazz)
                                                          .field(LearningEngineTaskKeys.analysisType)
                                                          .in(LearningEngineTaskType.getDeploymentTaskTypes()));
        } else {
          aggregatePipeline = aggregatePipeline.match(hPersistence.createQuery(clazz)
                                                          .field(LearningEngineTaskKeys.analysisType)
                                                          .notIn(LearningEngineTaskType.getDeploymentTaskTypes()));
        }
        aggregatePipeline =
            aggregatePipeline.group(id(grouping("accountId", "accountId")), grouping("count", accumulator("$sum", 1)));
        if (limit > 0) {
          aggregatePipeline.limit(limit);
        }
        aggregatePipeline
            .aggregate(InstanceCount.class,
                AggregationOptions.builder().maxTime(hPersistence.getMaxTimeMs(clazz), TimeUnit.MILLISECONDS).build())
            .forEachRemaining(instanceCount -> {
              try (AutoMetricContext accountMetricContext = new AccountMetricContext(instanceCount.id.accountId)) {
                metricService.recordMetric(getStatusMetricName(metricName, status.toString()), instanceCount.count);
                metricService.recordMetric(
                    getStatusMetricNameWithEnv(metricName, status.toString(), ENV), instanceCount.count);
              }
            });
      });
      log.info("Total time taken to collect metrics for class {} {} (ms)", metricName,
          clock.instant().toEpochMilli() - startTime);
    });
  }

  @VisibleForTesting
  void sendLEAutoscaleMetrics() {
    long now = clock.instant().toEpochMilli();
    recordLEMaxQueuedTime(now);
    recordLEDeploymentMaxQueuedTime(now);
    recordLEServiceHealthMaxQueuedTime(now);
  }

  private void recordLEServiceHealthMaxQueuedTime(long now) {
    LearningEngineTask earliestLiveHealthTask = hPersistence.createQuery(LearningEngineTask.class)
                                                    .filter(LearningEngineTaskKeys.taskStatus, QUEUED)
                                                    .field(LearningEngineTaskKeys.analysisType)
                                                    .notIn(LearningEngineTaskType.getDeploymentTaskTypes())
                                                    .order(Sort.ascending(VerificationTaskBaseKeys.lastUpdatedAt))
                                                    .get();

    long timeSinceTask = earliestLiveHealthTask == null ? 0L : now - earliestLiveHealthTask.getLastUpdatedAt();
    Duration maxTime = Duration.ofMillis(timeSinceTask);
    metricService.recordMetric("learning_engine_service_health_max_queued_time_" + ENV, timeSinceTask);
    metricRegistry.recordGaugeValue(ENV + "_"
            + "ng_le_service_health_max_queued_time_sec",
        null, maxTime.getSeconds());
  }

  private void recordLEDeploymentMaxQueuedTime(long now) {
    LearningEngineTask earliestDeploymentTask = hPersistence.createQuery(LearningEngineTask.class)
                                                    .filter(LearningEngineTaskKeys.taskStatus, QUEUED)
                                                    .field(LearningEngineTaskKeys.analysisType)
                                                    .in(LearningEngineTaskType.getDeploymentTaskTypes())
                                                    .order(Sort.ascending(VerificationTaskBaseKeys.lastUpdatedAt))
                                                    .get();
    long timeSinceDeploymentTask =
        earliestDeploymentTask == null ? 0L : now - earliestDeploymentTask.getLastUpdatedAt();
    Duration deploymentMaxTime = Duration.ofMillis(timeSinceDeploymentTask);
    metricService.recordMetric("learning_engine_deployment_max_queued_time_" + ENV, timeSinceDeploymentTask);
    metricRegistry.recordGaugeValue(ENV + "_"
            + "ng_le_deployment_max_queued_time_sec",
        null, deploymentMaxTime.getSeconds());
  }

  private void recordLEMaxQueuedTime(long now) {
    LearningEngineTask earliestTask = hPersistence.createQuery(LearningEngineTask.class)
                                          .filter(LearningEngineTaskKeys.taskStatus, QUEUED)
                                          .order(Sort.ascending(VerificationTaskBaseKeys.lastUpdatedAt))
                                          .get();
    long timeSinceTask = earliestTask == null ? 0L : now - earliestTask.getLastUpdatedAt();
    metricService.recordMetric("learning_engine_max_queued_time_" + ENV, timeSinceTask);
  }

  @Override
  public List<MetricConfiguration> getMetricConfiguration() {
    List<MetricConfiguration.Metric> metrics = new ArrayList<>();
    LE_TASKS_METRICS_TO_BE_RECORDED.forEach((metricName, isDeploymentTask) -> {
      metrics.add(MetricConfiguration.Metric.builder()
                      .metricName(getNonFinalStatusMetricName(metricName))
                      .type("LastValue")
                      .unit("1")
                      .metricDefinition(metricName + " non final status count")
                      .build());
      LearningEngineTask.ExecutionStatus.getNonFinalStatues().forEach(status
          -> metrics.add(MetricConfiguration.Metric.builder()
                             .metricName(getStatusMetricName(metricName, status.toString()))
                             .type("LastValue")
                             .unit("1")
                             .metricDefinition(metricName + " " + status + " count")
                             .build()));
      metrics.add(MetricConfiguration.Metric.builder()
                      .metricName(getNonFinalStatusMetricNameWithEnv(metricName, ENV))
                      .type("LastValue")
                      .unit("1")
                      .metricDefinition(metricName + " non final status " + ENV + "count")
                      .build());
      LearningEngineTask.ExecutionStatus.getNonFinalStatues().forEach(status
          -> metrics.add(MetricConfiguration.Metric.builder()
                             .metricName(getStatusMetricNameWithEnv(metricName, status.toString(), ENV))
                             .type("LastValue")
                             .unit("1")
                             .metricDefinition(metricName + " " + status + ENV + " count")
                             .build()));
    });
    TASKS_INFO.forEach((clazz, queryParam) -> {
      metrics.add(MetricConfiguration.Metric.builder()
                      .metricName(getNonFinalStatusMetricName(queryParam.getName()))
                      .type("LastValue")
                      .unit("1")
                      .metricDefinition(clazz.getSimpleName() + " non final status count")
                      .build());
      queryParam.getNonFinalStatuses().forEach(status -> {
        metrics.add(MetricConfiguration.Metric.builder()
                        .metricName(getStatusMetricName(queryParam, status.toString()))
                        .type("LastValue")
                        .unit("1")
                        .metricDefinition(clazz.getSimpleName() + " " + status + " count")
                        .build());
      });
    });
    MetricConfiguration metricConfiguration = MetricConfiguration.builder()
                                                  .metricGroup("account")
                                                  .identifier("cvng_tasks_status_counts")
                                                  .name("CVNG tasks status count")
                                                  .metrics(metrics)
                                                  .build();
    /*
    TODO: Uncomment this to write file to generate dashboard. This is kind of manual now. We need to automate dashboard
    creation. ObjectMapper mapper = new ObjectMapper(new
    YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)); try { mapper.writeValue(new
    File("~/workspace/portal/300-cv-nextgen/src/scripts/runtime.yaml"), metricConfiguration); } catch (IOException e) {
      e.printStackTrace();
    }
    */
    return Collections.singletonList(metricConfiguration);
  }

  @NotNull
  private String getNonFinalStatusMetricName(String name) {
    return name + "_non_final_status_count";
  }

  private String getNonFinalStatusMetricNameWithEnv(String name, String env) {
    return name + "_non_final_status_" + env + "_count";
  }
  @NotNull
  private String getStatusMetricName(QueryParams queryParams, String s) {
    return queryParams.getName() + "_" + s.toLowerCase() + "_count";
  }

  private String getStatusMetricName(String name, String s) {
    return name + "_" + s.toLowerCase() + "_count";
  }
  private String getStatusMetricNameWithEnv(String name, String status, String env) {
    return name + "_" + status.toLowerCase() + "_" + env + "_count";
  }

  @Value
  @Builder
  private static class QueryParams {
    List<?> nonFinalStatuses;
    String statusField;
    String name;
  }
  @Data
  @NoArgsConstructor
  private static class InstanceCount {
    @Id ID id;
    int count;
  }
  @Data
  @NoArgsConstructor
  private static class ID {
    String accountId;
  }
}
