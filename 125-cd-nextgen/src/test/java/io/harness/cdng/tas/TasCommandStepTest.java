/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.beans.FileData;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.yaml.TasManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.tas.outcome.TanzuCommandOutcome;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.instancesync.info.TasServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.request.CfRunPluginCommandRequestNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.delegate.task.pcf.response.TasRunPluginResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.pcf.CfCommandUnitConstants;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pcf.model.CfCliVersionNG;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.powermock.api.mockito.PowerMockito;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasCommandStepTest extends CDNGTestBase {
  @Spy private CDStepHelper cdStepHelper;
  @Mock private StepHelper stepHelper;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private TasStepHelper tasStepHelper;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private OutcomeService outcomeService;
  @Mock private TasEntityHelper tasEntityHelper;
  @Mock private io.harness.cdng.expressions.CDExpressionResolver cdExpressionResolver;
  @Mock private LogCallback mockLogCallback;
  @InjectMocks private TasCommandStep tasCommandStep;

  private final TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
      TanzuApplicationServiceInfrastructureOutcome.builder()
          .connectorRef("account.tas")
          .organization("dev-org")
          .space("dev-space")
          .build();
  private final TasCommandStepParameters parameters =
      TasCommandStepParameters.infoBuilder()
          .delegateSelectors(ParameterField.createValueField(List.of(new TaskSelectorYaml("selector-1"))))
          .build();

  private final Ambiance ambiance = getAmbiance();

  @Before
  public void setup() {
    ILogStreamingStepClient logStreamingStepClient;
    logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
    doReturn(infrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(ambiance);
    doReturn(mockLogCallback).when(cdStepHelper).getLogCallback(anyString(), eq(ambiance), anyBoolean());

    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testValidateResourcesFFEnabled() {
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.NG_SVC_ENV_REDESIGN));
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().type("BasicAppSetup").spec(parameters).build();
    tasCommandStep.validateResources(ambiance, stepElementParameters);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testValidateResourcesFFDisabled() {
    doReturn(false).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.NG_SVC_ENV_REDESIGN));
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().type("BasicAppSetup").spec(parameters).build();
    assertThatThrownBy(() -> tasCommandStep.validateResources(ambiance, stepElementParameters))
        .hasMessage("NG_SVC_ENV_REDESIGN FF is not enabled for this account. Please contact harness customer care.");
  }

  private Ambiance getAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "account");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project");

    return Ambiance.newBuilder()
        .putAllSetupAbstractions(setupAbstractions)
        .setStageExecutionId("stageExecutionId")
        .build();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(tasCommandStep.getStepParametersClass()).isEqualTo(StepBaseParameters.class);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testExecuteTasTask() {
    StepElementParameters stepElementParameters = getStepElementParameters(null, null);
    InfrastructureOutcome infrastructureOutcome = TanzuApplicationServiceInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(ambiance);
    Map<String, String> allFilesFetched = new HashMap<>();
    CfCliVersionNG cliVersionNG = CfCliVersionNG.V7;
    CfCliVersion cfCliVersion = CfCliVersion.V7;
    List<FileData> fileDataList = Collections.emptyList();
    TasExecutionPassThroughData tasExecutionPassThroughData =
        TasExecutionPassThroughData.builder().allFilesFetched(allFilesFetched).cfCliVersion(cliVersionNG).build();
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    List<UnitProgress> unitProgresses = new ArrayList<>();
    unitProgresses.add(UnitProgress.newBuilder()
                           .setUnitName(CfCommandUnitConstants.ResolveInputOutputVariables)
                           .setStatus(UnitStatus.SUCCESS)
                           .build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    doReturn(tasInfraConfig).when(cdStepHelper).getTasInfraConfig(infrastructureOutcome, ambiance);
    doReturn(cfCliVersion).when(tasStepHelper).cfCliVersionNGMapper(cliVersionNG);

    CfRunPluginCommandRequestNG cfRunPluginCommandRequestNG = getCfRunPluginCommandRequestNG(cliVersionNG, fileDataList,
        tasExecutionPassThroughData, tasInfraConfig, unitProgressData, Collections.emptyMap(), Collections.emptyList());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito
        .when(TaskRequestsUtils.prepareCDTaskRequest(
            any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);

    TasManifestOutcome tasManifestOutcome = TasManifestOutcome.builder().build();
    TaskChainResponse taskChainResponse = tasCommandStep.executeTasTask(
        tasManifestOutcome, ambiance, stepElementParameters, tasExecutionPassThroughData, true, unitProgressData);

    CfRunPluginCommandRequestNG taskData =
        (CfRunPluginCommandRequestNG) taskDataArgumentCaptor.getValue().getParameters()[0];
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any());

    assertThat(taskChainResponse.isChainEnd()).isTrue();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(TasExecutionPassThroughData.class);
    assertThat((TasExecutionPassThroughData) taskChainResponse.getPassThroughData())
        .isEqualTo(tasExecutionPassThroughData);
    assertThat(taskData).isEqualToIgnoringGivenFields(cfRunPluginCommandRequestNG, "commandUnitsProgress");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testExecuteTasTaskWithException() {
    StepElementParameters stepElementParameters =
        getStepElementParameters(getStepParametersInputVariablesMap(), getStepParametersOutputVariablesMap());
    InfrastructureOutcome infrastructureOutcome = TanzuApplicationServiceInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(ambiance);
    Map<String, String> allFilesFetched = new HashMap<>();
    CfCliVersionNG cliVersionNG = CfCliVersionNG.V7;
    CfCliVersion cfCliVersion = CfCliVersion.V7;
    TasExecutionPassThroughData tasExecutionPassThroughData =
        TasExecutionPassThroughData.builder().allFilesFetched(allFilesFetched).cfCliVersion(cliVersionNG).build();
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    doReturn(tasInfraConfig).when(cdStepHelper).getTasInfraConfig(infrastructureOutcome, ambiance);
    doReturn(cfCliVersion).when(tasStepHelper).cfCliVersionNGMapper(cliVersionNG);

    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito
        .when(TaskRequestsUtils.prepareCDTaskRequest(
            any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);

    TasManifestOutcome tasManifestOutcome = TasManifestOutcome.builder().build();
    assertThatThrownBy(()
                           -> tasCommandStep.executeTasTask(tasManifestOutcome, ambiance, stepElementParameters,
                               tasExecutionPassThroughData, true, unitProgressData))
        .hasMessage("Output variable [Output5] value found to be empty");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testExecuteTasTaskWithOutputException() {
    Map<String, Object> outputVariables = getStepParametersOutputVariablesMap();
    outputVariables.put("Output5", ParameterField.createValueField("OutputValue5"));
    StepElementParameters stepElementParameters = getStepElementParameters(null, outputVariables);
    InfrastructureOutcome infrastructureOutcome = TanzuApplicationServiceInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(ambiance);
    Map<String, String> allFilesFetched = new HashMap<>();
    CfCliVersionNG cliVersionNG = CfCliVersionNG.V7;
    CfCliVersion cfCliVersion = CfCliVersion.V7;
    TasExecutionPassThroughData tasExecutionPassThroughData =
        TasExecutionPassThroughData.builder().allFilesFetched(allFilesFetched).cfCliVersion(cliVersionNG).build();
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    doReturn(tasInfraConfig).when(cdStepHelper).getTasInfraConfig(infrastructureOutcome, ambiance);
    doReturn(cfCliVersion).when(tasStepHelper).cfCliVersionNGMapper(cliVersionNG);

    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito
        .when(TaskRequestsUtils.prepareCDTaskRequest(
            any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);

    TasManifestOutcome tasManifestOutcome = TasManifestOutcome.builder().build();
    assertThatThrownBy(()
                           -> tasCommandStep.executeTasTask(tasManifestOutcome, ambiance, stepElementParameters,
                               tasExecutionPassThroughData, true, unitProgressData))
        .hasMessage("Output variable [Output6] value found to be empty");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testExecuteTasTaskWithVariables() {
    Map<String, Object> inputVariables = getStepParametersInputVariablesMap();
    Map<String, String> inputVariablesStringMap = getStepParametersStringInputVariablesMap();
    inputVariables.put("Input6", ParameterField.createValueField("InputValue6"));
    inputVariablesStringMap.put("Input6", "InputValue6");
    Map<String, Object> outputVariables = getStepParametersOutputVariablesMap();
    Map<String, String> outputVariablesStringMap = getStepParametersStringOutputVariablesMap();
    outputVariables.put("Output5", ParameterField.createValueField("OutputValue5"));
    outputVariablesStringMap.put("Output5", "OutputValue5");
    outputVariables.put("Output6", ParameterField.createValueField("OutputValue6"));
    outputVariablesStringMap.put("Output6", "OutputValue6");
    StepElementParameters stepElementParameters = getStepElementParameters(inputVariables, outputVariables);
    InfrastructureOutcome infrastructureOutcome = TanzuApplicationServiceInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(ambiance);
    Map<String, String> allFilesFetched = new HashMap<>();
    CfCliVersionNG cliVersionNG = CfCliVersionNG.V7;
    CfCliVersion cfCliVersion = CfCliVersion.V7;
    List<FileData> fileDataList = Collections.emptyList();
    TasExecutionPassThroughData tasExecutionPassThroughData = TasExecutionPassThroughData.builder()
                                                                  .resolvedOutputVariables(outputVariablesStringMap)
                                                                  .allFilesFetched(allFilesFetched)
                                                                  .cfCliVersion(cliVersionNG)
                                                                  .build();
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    doReturn(tasInfraConfig).when(cdStepHelper).getTasInfraConfig(infrastructureOutcome, ambiance);
    doReturn(cfCliVersion).when(tasStepHelper).cfCliVersionNGMapper(cliVersionNG);

    CfRunPluginCommandRequestNG cfRunPluginCommandRequestNG = getCfRunPluginCommandRequestNG(cliVersionNG, fileDataList,
        tasExecutionPassThroughData, tasInfraConfig, unitProgressData, inputVariablesStringMap,
        Arrays.asList("OutputValue2", "OutputValue3", "OutputValue1", "OutputValue2", "OutputValue5", "OutputValue6"));
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito
        .when(TaskRequestsUtils.prepareCDTaskRequest(
            any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);

    TasManifestOutcome tasManifestOutcome = TasManifestOutcome.builder().build();
    TaskChainResponse taskChainResponse = tasCommandStep.executeTasTask(
        tasManifestOutcome, ambiance, stepElementParameters, tasExecutionPassThroughData, true, unitProgressData);

    CfRunPluginCommandRequestNG taskData =
        (CfRunPluginCommandRequestNG) taskDataArgumentCaptor.getValue().getParameters()[0];
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any());

    assertThat(taskChainResponse.isChainEnd()).isTrue();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(TasExecutionPassThroughData.class);
    assertThat((TasExecutionPassThroughData) taskChainResponse.getPassThroughData())
        .isEqualTo(tasExecutionPassThroughData);
    assertThat(taskData).isEqualToIgnoringGivenFields(cfRunPluginCommandRequestNG, "commandUnitsProgress");
  }

  private CfRunPluginCommandRequestNG getCfRunPluginCommandRequestNG(CfCliVersionNG cliVersionNG,
      List<FileData> fileDataList, TasExecutionPassThroughData tasExecutionPassThroughData,
      TasInfraConfig tasInfraConfig, UnitProgressData unitProgressData, Map<String, String> inputVariables,
      List<String> outputVariables) {
    return CfRunPluginCommandRequestNG.builder()
        .cfCliVersion(tasStepHelper.cfCliVersionNGMapper(cliVersionNG))
        .cfCommandTypeNG(CfCommandTypeNG.TANZU_COMMAND)
        .fileDataList(fileDataList)
        .tasInfraConfig(tasInfraConfig)
        .accountId("account")
        .timeoutIntervalInMin(10)
        .commandName(CfCommandTypeNG.TANZU_COMMAND.name())
        .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
        .filePathsInScript(tasExecutionPassThroughData.getPathsFromScript())
        .repoRoot(tasExecutionPassThroughData.getRepoRoot())
        .inputVariables(inputVariables)
        .outputVariables(outputVariables)
        .renderedScriptString(tasExecutionPassThroughData.getRawScript())
        .useCfCLI(true)
        .build();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbac() {
    TaskChainResponse taskChainResponse = mock(TaskChainResponse.class);
    doReturn(taskChainResponse).when(tasStepHelper).startChainLinkForCommandStep(any(), any(), any());
    tasCommandStep.startChainLinkAfterRbac(
        ambiance, StepElementParameters.builder().build(), StepInputPackage.builder().build());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextWhenSuccess() throws Exception {
    StepElementParameters stepElementParameters = getStepElementParameters(null, getStepParametersOutputVariablesMap());
    String instanceIndex = "1";
    String appId = "id";
    String displayName = "displayName";
    String org = "org";
    String space = "space";
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    TasServerInstanceInfo tasServerInstanceInfo = TasServerInstanceInfo.builder()
                                                      .id(appId + ":" + instanceIndex)
                                                      .instanceIndex(instanceIndex)
                                                      .tasApplicationName(displayName)
                                                      .tasApplicationGuid(appId)
                                                      .organization(org)
                                                      .space(space)
                                                      .build();
    TasRunPluginResponse responseData = getTasRunPluginResponse(unitProgressData, null);
    TanzuApplicationServiceInfrastructureOutcome tanzuApplicationServiceInfrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().organization(org).space(space).build();
    doReturn(tanzuApplicationServiceInfrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    StepResponse.StepOutcome stepOutcome = StepResponse.StepOutcome.builder().name("name").build();
    doReturn(stepOutcome)
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(ambiance, List.of(tasServerInstanceInfo));

    StepResponse stepResponse1 =
        tasCommandStep.finalizeExecutionWithSecurityContextAndNodeInfo(ambiance, stepElementParameters,
            TasExecutionPassThroughData.builder()
                .resolvedOutputVariables(getStepParametersStringOutputVariablesMap())
                .build(),
            () -> responseData);
    assertThat(stepResponse1.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse1.getStepOutcomes()).isEmpty();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextWhenSuccessWithOutputVariables() throws Exception {
    StepElementParameters stepElementParameters = getStepElementParameters(null, getStepParametersOutputVariablesMap());
    String instanceIndex = "1";
    String appId = "id";
    String displayName = "displayName";
    String org = "org";
    String space = "space";
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    TasServerInstanceInfo tasServerInstanceInfo = TasServerInstanceInfo.builder()
                                                      .id(appId + ":" + instanceIndex)
                                                      .instanceIndex(instanceIndex)
                                                      .tasApplicationName(displayName)
                                                      .tasApplicationGuid(appId)
                                                      .organization(org)
                                                      .space(space)
                                                      .build();
    TasRunPluginResponse responseData = getTasRunPluginResponse(unitProgressData, getResponseOutputVariables());

    TanzuApplicationServiceInfrastructureOutcome tanzuApplicationServiceInfrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().organization(org).space(space).build();
    doReturn(tanzuApplicationServiceInfrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    StepResponse.StepOutcome stepOutcome = StepResponse.StepOutcome.builder().name("name").build();
    doReturn(stepOutcome)
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(ambiance, List.of(tasServerInstanceInfo));

    StepResponse stepResponse1 =
        tasCommandStep.finalizeExecutionWithSecurityContextAndNodeInfo(ambiance, stepElementParameters,
            TasExecutionPassThroughData.builder()
                .resolvedOutputVariables(getStepParametersStringOutputVariablesMap())
                .build(),
            () -> responseData);
    assertThat(stepResponse1.getStatus()).isEqualTo(Status.SUCCEEDED);
    Map<String, String> outputVariablesStepParamReq = new HashMap<>();
    outputVariablesStepParamReq.put("Output1", "Value1");
    outputVariablesStepParamReq.put("Output2", "testing 123");
    outputVariablesStepParamReq.put("Output3", "testing 123");
    outputVariablesStepParamReq.put("Output4", "Value2-hello");
    outputVariablesStepParamReq.put("Output5", null);
    TanzuCommandOutcome tanzuCommandOutcome =
        TanzuCommandOutcome.builder().outputVariables(outputVariablesStepParamReq).build();
    assertThat(stepResponse1.getStepOutcomes())
        .isEqualTo(List.of(StepResponse.StepOutcome.builder()
                               .outcome(tanzuCommandOutcome)
                               .name(OutputExpressionConstants.OUTPUT)
                               .build()));
  }

  @NotNull
  private Map<String, String> getResponseOutputVariables() {
    Map<String, String> outputVariables = new HashMap<>();
    outputVariables.put("OutputValue1", "Value1");
    outputVariables.put("OutputValue2", "testing 123");
    outputVariables.put("OutputValue3", "Value2-hello");
    outputVariables.put("OutputValue4", null);
    outputVariables.put("OutputValue5", "");
    outputVariables.put("OutputValue7", "hello");
    return outputVariables;
  }

  @NotNull
  private Map<String, Object> getStepParametersInputVariablesMap() {
    Map<String, Object> outputVariablesStepParam = new HashMap<>();
    outputVariablesStepParam.put("Input1", ParameterField.createValueField("InputValue1"));
    outputVariablesStepParam.put("Input2", ParameterField.createValueField("InputValue2"));
    outputVariablesStepParam.put("Input3", ParameterField.createValueField("InputValue2"));
    outputVariablesStepParam.put("Input4", ParameterField.createValueField("InputValue3"));
    outputVariablesStepParam.put("Input5", ParameterField.createValueField(""));
    outputVariablesStepParam.put("Input6", ParameterField.createValueField(null));
    return outputVariablesStepParam;
  }

  @NotNull
  private Map<String, String> getStepParametersStringInputVariablesMap() {
    Map<String, String> outputVariablesStepParam = new HashMap<>();
    outputVariablesStepParam.put("Input1", "InputValue1");
    outputVariablesStepParam.put("Input2", "InputValue2");
    outputVariablesStepParam.put("Input3", "InputValue2");
    outputVariablesStepParam.put("Input4", "InputValue3");
    outputVariablesStepParam.put("Input5", "");
    outputVariablesStepParam.put("Input6", null);
    return outputVariablesStepParam;
  }

  @NotNull
  private Map<String, Object> getStepParametersOutputVariablesMap() {
    Map<String, Object> outputVariablesStepParam = new HashMap<>();
    outputVariablesStepParam.put("Output1", ParameterField.createValueField("OutputValue1"));
    outputVariablesStepParam.put("Output2", ParameterField.createValueField("OutputValue2"));
    outputVariablesStepParam.put("Output3", ParameterField.createValueField("OutputValue2"));
    outputVariablesStepParam.put("Output4", ParameterField.createValueField("OutputValue3"));
    outputVariablesStepParam.put("Output5", ParameterField.createValueField(""));
    outputVariablesStepParam.put("Output6", ParameterField.createValueField(null));
    return outputVariablesStepParam;
  }

  @NotNull
  private Map<String, String> getStepParametersStringOutputVariablesMap() {
    Map<String, String> outputVariablesStepParam = new HashMap<>();
    outputVariablesStepParam.put("Output1", "OutputValue1");
    outputVariablesStepParam.put("Output2", "OutputValue2");
    outputVariablesStepParam.put("Output3", "OutputValue2");
    outputVariablesStepParam.put("Output4", "OutputValue3");
    outputVariablesStepParam.put("Output5", "");
    outputVariablesStepParam.put("Output6", null);
    return outputVariablesStepParam;
  }

  private TasRunPluginResponse getTasRunPluginResponse(
      UnitProgressData unitProgressData, Map<String, String> outputVariables) {
    return TasRunPluginResponse.builder()
        .unitProgressData(unitProgressData)
        .outputVariables(outputVariables)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }

  private StepElementParameters getStepElementParameters(
      Map<String, Object> inputVariables, Map<String, Object> outputVariables) {
    TasCommandStepParameters tasCommandStepParameters =
        TasCommandStepParameters.infoBuilder().inputVariables(inputVariables).outputVariables(outputVariables).build();
    return StepElementParameters.builder()
        .spec(tasCommandStepParameters)
        .timeout(ParameterField.createValueField("10m"))
        .build();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextWhenFailure() throws Exception {
    StepElementParameters stepElementParameters = getStepElementParameters(null, null);
    String instanceIndex = "1";
    String appId = "id";
    String displayName = "displayName";
    String org = "org";
    String space = "space";
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    TasServerInstanceInfo tasServerInstanceInfo = TasServerInstanceInfo.builder()
                                                      .id(appId + ":" + instanceIndex)
                                                      .instanceIndex(instanceIndex)
                                                      .tasApplicationName(displayName)
                                                      .tasApplicationGuid(appId)
                                                      .organization(org)
                                                      .space(space)
                                                      .build();
    TasRunPluginResponse responseData = TasRunPluginResponse.builder()
                                            .unitProgressData(unitProgressData)
                                            .errorMessage("error")
                                            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                            .build();

    doReturn(unitProgressData).when(tasStepHelper).completeUnitProgressData(any(), any(), any());

    TanzuApplicationServiceInfrastructureOutcome tanzuApplicationServiceInfrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().organization(org).space(space).build();
    doReturn(tanzuApplicationServiceInfrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    StepResponse.StepOutcome stepOutcome = StepResponse.StepOutcome.builder().name("name").build();
    doReturn(stepOutcome)
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(ambiance, List.of(tasServerInstanceInfo));

    StepResponse stepResponse1 = tasCommandStep.finalizeExecutionWithSecurityContextAndNodeInfo(
        ambiance, stepElementParameters, TasExecutionPassThroughData.builder().build(), () -> responseData);
    assertThat(stepResponse1.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextWhenStepException() throws Exception {
    StepElementParameters stepElementParameters = getStepElementParameters(null, null);
    String instanceIndex = "1";
    String appId = "id";
    String displayName = "displayName";
    String org = "org";
    String space = "space";
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    TasServerInstanceInfo tasServerInstanceInfo = TasServerInstanceInfo.builder()
                                                      .id(appId + ":" + instanceIndex)
                                                      .instanceIndex(instanceIndex)
                                                      .tasApplicationName(displayName)
                                                      .tasApplicationGuid(appId)
                                                      .organization(org)
                                                      .space(space)
                                                      .build();
    TasRunPluginResponse responseData = TasRunPluginResponse.builder()
                                            .unitProgressData(unitProgressData)
                                            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                            .build();

    doReturn(unitProgressData).when(tasStepHelper).completeUnitProgressData(any(), any(), any());

    TanzuApplicationServiceInfrastructureOutcome tanzuApplicationServiceInfrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().organization(org).space(space).build();
    doReturn(tanzuApplicationServiceInfrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    StepResponse.StepOutcome stepOutcome = StepResponse.StepOutcome.builder().name("name").build();
    doReturn(stepOutcome)
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(ambiance, List.of(tasServerInstanceInfo));

    StepResponse stepResponse1 =
        tasCommandStep.finalizeExecutionWithSecurityContextAndNodeInfo(ambiance, stepElementParameters,
            StepExceptionPassThroughData.builder().unitProgressData(unitProgressData).errorMessage("error").build(),
            () -> responseData);
    assertThat(stepResponse1.getStatus()).isEqualTo(Status.FAILED);
  }
}
