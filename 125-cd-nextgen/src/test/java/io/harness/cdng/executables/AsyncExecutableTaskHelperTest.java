/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.executables;

import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.AccountId;
import io.harness.delegate.SubmitTaskRequest;
import io.harness.delegate.TaskDetails;
import io.harness.delegate.TaskLogAbstractions;
import io.harness.delegate.TaskMode;
import io.harness.delegate.TaskSetupAbstractions;
import io.harness.delegate.TaskType;
import io.harness.delegate.beans.TaskData;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.tasks.DelegateTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.helpers.ext.k8s.request.K8sApplyTaskParameters;

import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class AsyncExecutableTaskHelperTest extends CategoryTest {
  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks private AsyncExecutableTaskHelper asyncExecutableTaskHelper;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private AsyncWaitEngine asyncWaitEngine;

  private AutoCloseable mocks;

  private static final String PARAMS = "PARAMS";
  private static final String JSON_PARAMS = "{name : value}";
  private final String accountId = "accountId";
  private final Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testExtractTaskRequest() {
    TaskDetails taskDetails = TaskDetails.newBuilder()
                                  .setKryoParameters(ByteString.copyFrom(PARAMS.getBytes()))
                                  .setType(TaskType.newBuilder().setType("SOME_TYPE").build())
                                  .setExecutionTimeout(Duration.newBuilder().setSeconds(3).build())
                                  .setMode(TaskMode.ASYNC)
                                  .build();
    when(kryoSerializer.asInflatedObject(any())).thenReturn(PARAMS);

    TaskData taskData = asyncExecutableTaskHelper.extractTaskRequest(taskDetails);

    assertThat(taskData.getParameters()[0]).isEqualTo(PARAMS);
    assertThat(taskData.getData()).isEqualTo(PARAMS.getBytes());
    assertThat(taskData.getTaskType()).isEqualTo("SOME_TYPE");
    assertThat(taskData.getTimeout()).isEqualTo(3000);
    assertThat(taskData.isAsync()).isTrue();
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testExtractTaskRequestWithJsonPrams() {
    TaskDetails taskDetails = TaskDetails.newBuilder()
                                  .setJsonParameters(ByteString.copyFrom(JSON_PARAMS.getBytes()))
                                  .setType(TaskType.newBuilder().setType("SOME_TYPE").build())
                                  .setExecutionTimeout(Duration.newBuilder().setSeconds(3).build())
                                  .setMode(TaskMode.ASYNC)
                                  .build();

    TaskData taskData = asyncExecutableTaskHelper.extractTaskRequest(taskDetails);

    assertThat(taskData.getParameters()).isNull();
    assertThat(taskData.getData()).isEqualTo(JSON_PARAMS.getBytes());
    assertThat(taskData.getTaskType()).isEqualTo("SOME_TYPE");
    assertThat(taskData.getTimeout()).isEqualTo(3000);
    assertThat(taskData.isAsync()).isTrue();
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testMapTaskRequestToDelegateTaskRequest() {
    TaskRequest taskRequest =
        TaskRequest.newBuilder()
            .setDelegateTaskRequest(
                DelegateTaskRequest.newBuilder()
                    .setRequest(SubmitTaskRequest.newBuilder()
                                    .setAccountId(AccountId.newBuilder().setId("accountId").build())
                                    .setSetupAbstractions(TaskSetupAbstractions.newBuilder()
                                                              .putAllValues(Map.of("key1", "value1", "key2", "value2"))
                                                              .build())
                                    .setLogAbstractions(TaskLogAbstractions.newBuilder()
                                                            .putAllValues(Map.of("ab1", "cd1", "ab2", "cd2"))
                                                            .build())
                                    .setForceExecute(false)
                                    .addAllEligibleToExecuteDelegateIds(List.of("delegate1", "delegate2"))
                                    .setExecuteOnHarnessHostedDelegates(true)
                                    .setEmitEvent(false)
                                    .setStageId("stageId")
                                    .build())
                    .build())
            .build();
    Set<String> taskSelectors = Set.of("selector1", "selector2");
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {K8sApplyTaskParameters.builder().build()})
                            .taskType("SOME_TYPE")
                            .parked(false)
                            .timeout(3000)
                            .expressionFunctorToken(23)
                            .build();

    io.harness.beans.DelegateTaskRequest delegateTaskRequest =
        asyncExecutableTaskHelper.mapTaskRequestToDelegateTaskRequest(
            taskRequest, taskData, taskSelectors, "baseLogKey", true);

    assertThat(delegateTaskRequest.getAccountId()).isEqualTo("accountId");
    assertThat(delegateTaskRequest.getTaskSetupAbstractions()).isEqualTo(Map.of("key1", "value1", "key2", "value2"));
    assertThat(delegateTaskRequest.getLogStreamingAbstractions()).isEqualTo(Map.of("ab1", "cd1", "ab2", "cd2"));
    assertThat(delegateTaskRequest.isForceExecute()).isFalse();
    assertThat(delegateTaskRequest.getEligibleToExecuteDelegateIds()).isEqualTo(List.of("delegate1", "delegate2"));
    assertThat(delegateTaskRequest.isExecuteOnHarnessHostedDelegates()).isTrue();
    assertThat(delegateTaskRequest.isEmitEvent()).isFalse();
    assertThat(delegateTaskRequest.getStageId()).isEqualTo("stageId");
    assertThat(delegateTaskRequest.getTaskSelectors().size()).isEqualTo(2);
    assertThat(delegateTaskRequest.getTaskParameters()).isInstanceOf(K8sApplyTaskParameters.class);
    assertThat(delegateTaskRequest.getTaskType()).isEqualTo("SOME_TYPE");
    assertThat(delegateTaskRequest.isParked()).isFalse();
    assertThat(delegateTaskRequest.getExecutionTimeout().getSeconds()).isEqualTo(3);
    assertThat(delegateTaskRequest.getExpressionFunctorToken()).isEqualTo(23);
    assertThat(delegateTaskRequest.getBaseLogKey()).isEqualTo("baseLogKey");
    assertThat(delegateTaskRequest.isShouldSkipOpenStream()).isTrue();
    assertThat(delegateTaskRequest.isSelectionLogsTrackingEnabled()).isTrue();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetAsyncExecutableResponse() {
    TaskDetails taskDetails = TaskDetails.newBuilder()
                                  .setKryoParameters(ByteString.copyFrom("test", Charset.defaultCharset()))
                                  .setType(TaskType.newBuilder().setType("TRAFFIC_ROUTING").build())
                                  .setExecutionTimeout(Duration.newBuilder().setSeconds(10).build())
                                  .setExpressionFunctorToken(1)
                                  .setParked(false)
                                  .build();

    SubmitTaskRequest submitTaskRequest = SubmitTaskRequest.newBuilder().setDetails(taskDetails).build();

    DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.newBuilder()
                                                  .addLogKeys("lk1")
                                                  .addLogKeys("lk2")
                                                  .addLogKeys("lk3")
                                                  .addLogKeys("lk4")
                                                  .addUnits("u1")
                                                  .addUnits("u2")
                                                  .addUnits("u3")
                                                  .addUnits("u4")
                                                  .setRequest(submitTaskRequest)
                                                  .setTaskName("task1")
                                                  .build();

    TaskRequest taskRequest = TaskRequest.newBuilder().setDelegateTaskRequest(delegateTaskRequest).build();

    doReturn("taskId").when(delegateGrpcClientWrapper).submitAsyncTaskV2(any(), any());

    AsyncExecutableResponse asyncExecutableResponse =
        asyncExecutableTaskHelper.getAsyncExecutableResponse(ambiance, taskRequest);
    ArgumentCaptor<AsyncDelegateResumeCallback> asyncDelegateResumeCallbackArgumentCaptor =
        ArgumentCaptor.forClass(AsyncDelegateResumeCallback.class);
    ArgumentCaptor<List<String>> correlationIdsCaptor = ArgumentCaptor.forClass(List.class);

    Mockito.verify(asyncWaitEngine, Mockito.times(1))
        .waitForAllOn(
            asyncDelegateResumeCallbackArgumentCaptor.capture(), any(), correlationIdsCaptor.capture(), anyLong());

    assertThat(asyncExecutableResponse.getLogKeys(0)).isEqualTo("lk1");
    assertThat(asyncExecutableResponse.getLogKeys(1)).isEqualTo("lk2");
    assertThat(asyncExecutableResponse.getLogKeys(2)).isEqualTo("lk3");
    assertThat(asyncExecutableResponse.getLogKeys(3)).isEqualTo("lk4");
    assertThat(asyncExecutableResponse.getUnits(0)).isEqualTo("u1");
    assertThat(asyncExecutableResponse.getUnits(1)).isEqualTo("u2");
    assertThat(asyncExecutableResponse.getUnits(2)).isEqualTo("u3");
    assertThat(asyncExecutableResponse.getUnits(3)).isEqualTo("u4");
    assertThat(asyncExecutableResponse.getCallbackIds(0)).isEqualTo("taskId");
    assertThat(asyncExecutableResponse.getTimeout()).isEqualTo(10000);
  }
}
