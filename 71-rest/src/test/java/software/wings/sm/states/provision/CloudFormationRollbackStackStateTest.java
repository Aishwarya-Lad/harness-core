package software.wings.sm.states.provision;

import static io.harness.delegate.command.CommandExecutionResult.CommandExecutionStatus.SUCCESS;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest.CloudFormationCommandType.CREATE_STACK;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.PROVISIONER_ID;

import com.google.common.collect.ImmutableMap;

import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.ResponseData;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mongodb.morphia.query.MorphiaIterator;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import software.wings.WingsBaseTest;
import software.wings.api.ScriptStateExecutionData;
import software.wings.api.cloudformation.CloudFormationRollbackInfoElement;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.Environment;
import software.wings.beans.SettingAttribute;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCreateStackRequest;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCreateStackResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationRollbackInfo;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.WorkflowStandardParams;

import java.util.Map;

public class CloudFormationRollbackStackStateTest extends WingsBaseTest {
  @Mock private ActivityService mockActivityService;
  @Mock private DelegateService mockDelegateService;
  @Mock private SettingsService mockSettingsService;
  @Mock private AppService mockAppService;
  @Mock private SecretManager mockSecretManager;
  @Mock private InfrastructureProvisionerService mockInfrastructureProvisionerService;
  @Mock private LogService mockLogService;
  @Mock private WingsPersistence mockWingsPersistence;

  @InjectMocks private CloudFormationRollbackStackState state = new CloudFormationRollbackStackState("stateName");

  @Test
  @Category(UnitTests.class)
  public void testExecute() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    Application app = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    doReturn(app).when(mockContext).getApp();
    Environment env = anEnvironment().appId(APP_ID).uuid(ENV_ID).name(ENV_NAME).build();

    EmbeddedUser mockCurrentUser = mock(EmbeddedUser.class);
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    doReturn(mockCurrentUser).when(mockParams).getCurrentUser();
    doReturn(mockParams).when(mockContext).getContextElement(ContextElementType.STANDARD);
    doReturn(env).when(mockParams).getEnv();

    Query mockQuery = mock(Query.class);
    doReturn(mockQuery).when(mockWingsPersistence).createQuery(any());
    doReturn(mockQuery).when(mockQuery).filter(anyString(), anyString());
    doReturn(mockQuery).when(mockQuery).order(any(Sort[].class));
    MorphiaIterator mockIterator = mock(MorphiaIterator.class);
    doReturn(mockIterator).when(mockQuery).fetch();
    doReturn(false).when(mockIterator).hasNext();
    doReturn(env).when(mockContext).getEnv();
    Activity activity = Activity.builder().build();
    activity.setUuid(ACTIVITY_ID);
    doReturn(activity).when(mockActivityService).save(any());
    SettingAttribute awsConfig = aSettingAttribute().withValue(AwsConfig.builder().build()).build();
    doReturn(awsConfig).when(mockSettingsService).get(anyString());
    CloudFormationRollbackInfoElement stackElement = CloudFormationRollbackInfoElement.builder()
                                                         .stackExisted(true)
                                                         .oldStackBody("oldBody")
                                                         .provisionerId(PROVISIONER_ID)
                                                         .oldStackParameters(ImmutableMap.of("oldKey", "oldVal"))
                                                         .build();
    doReturn(singletonList(stackElement)).when(mockContext).getContextElementList(any());
    state.setProvisionerId(PROVISIONER_ID);
    state.setTimeoutMillis(1000);
    ExecutionResponse response = state.execute(mockContext);
    assertEquals(ExecutionStatus.SUCCESS, response.getExecutionStatus());
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockDelegateService).queueTask(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertNotNull(delegateTask);
    assertNotNull(delegateTask.getData().getParameters());
    assertEquals(delegateTask.getData().getParameters().length, 2);
    assertThat(delegateTask.getData().getParameters()[0] instanceof CloudFormationCreateStackRequest).isTrue();
    CloudFormationCreateStackRequest createStackRequest =
        (CloudFormationCreateStackRequest) delegateTask.getData().getParameters()[0];
    assertEquals(createStackRequest.getCommandType(), CREATE_STACK);
    assertEquals(createStackRequest.getData(), "oldBody");
    assertEquals(createStackRequest.getTimeoutInMs(), 1000);
    Map<String, String> stackParam = createStackRequest.getVariables();
    assertNotNull(stackParam);
    assertEquals(stackParam.size(), 1);
    assertEquals(stackParam.get("oldKey"), "oldVal");
  }

  @Test
  @Category(UnitTests.class)
  public void testHandleAsync() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doReturn(APP_ID).when(mockContext).getAppId();
    CloudFormationRollbackInfoElement stackElement =
        CloudFormationRollbackInfoElement.builder().stackExisted(true).provisionerId(PROVISIONER_ID).build();
    doReturn(stackElement).when(mockContext).getContextElement(any());
    doReturn(ScriptStateExecutionData.builder().build()).when(mockContext).getStateExecutionData();
    Map<String, ResponseData> delegateResponse = ImmutableMap.of(ACTIVITY_ID,
        CloudFormationCommandExecutionResponse.builder()
            .commandExecutionStatus(SUCCESS)
            .commandResponse(CloudFormationCreateStackResponse.builder()
                                 .cloudFormationOutputMap(ImmutableMap.of("k1", "v1"))
                                 .rollbackInfo(CloudFormationRollbackInfo.builder().build())
                                 .commandExecutionStatus(SUCCESS)
                                 .build())
            .build());
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    doReturn(mockParams).when(mockContext).getContextElement(ContextElementType.STANDARD);
    Environment env = anEnvironment().appId(APP_ID).uuid(ENV_ID).name(ENV_NAME).build();
    doReturn(env).when(mockParams).getEnv();

    ExecutionResponse response = state.handleAsyncResponse(mockContext, delegateResponse);
    assertEquals(ExecutionStatus.SUCCESS, response.getExecutionStatus());
    verify(mockActivityService).updateStatus(eq(ACTIVITY_ID), eq(APP_ID), eq(ExecutionStatus.SUCCESS));
    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
    verify(mockInfrastructureProvisionerService)
        .regenerateInfrastructureMappings(anyString(), any(), captor.capture(), any(), any());
    Map<String, Object> map = captor.getValue();
    assertEquals(map.size(), 1);
    assertEquals(map.get("k1"), "v1");
  }
}
