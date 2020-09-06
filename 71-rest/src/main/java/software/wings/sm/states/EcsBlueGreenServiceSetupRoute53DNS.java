package software.wings.sm.states;

import static com.google.common.collect.Maps.newHashMap;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;
import static java.util.Collections.singletonList;
import static software.wings.beans.TaskType.GIT_FETCH_FILES_TASK;
import static software.wings.beans.command.PcfDummyCommandUnit.FetchFiles;
import static software.wings.delegatetasks.GitFetchFilesTask.GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT;
import static software.wings.service.impl.aws.model.AwsConstants.ECS_SERVICE_SETUP_SWEEPING_OUTPUT_NAME;
import static software.wings.sm.StateType.ECS_BG_SERVICE_SETUP_ROUTE53;

import com.google.inject.Inject;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.k8s.model.ImageDetails;
import io.harness.logging.CommandExecutionStatus;
import io.harness.tasks.Cd1SetupFields;
import io.harness.tasks.ResponseData;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.wings.api.CommandStateExecutionData;
import software.wings.api.ContainerServiceElement;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.ecs.EcsBGRoute53SetupStateExecutionData;
import software.wings.api.ecs.EcsSetupStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.Environment;
import software.wings.beans.FeatureName;
import software.wings.beans.GitFetchFilesTaskParams;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.ResizeStrategy;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData;
import software.wings.beans.command.EcsSetupParams;
import software.wings.beans.container.AwsAutoScalarConfig;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.helpers.ext.ecs.request.EcsBGRoute53ServiceSetupRequest;
import software.wings.helpers.ext.ecs.response.EcsBGRoute53ServiceSetupResponse;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.FeatureFlagService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateExecutionData;
import software.wings.sm.WorkflowStandardParams;
import software.wings.utils.ApplicationManifestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class EcsBlueGreenServiceSetupRoute53DNS extends State {
  public static final String ECS_SERVICE_SETUP_COMMAND_ROUTE53 = "ECS Service Setup Route 53";

  @Getter @Setter private String roleArn;
  @Getter @Setter private String targetPort;
  @Getter @Setter private String maxInstances;
  @Getter @Setter private String fixedInstances;
  @Getter @Setter private String ecsServiceName;
  @Getter @Setter private String targetContainerName;
  @Getter @Setter private String desiredInstanceCount;
  @Getter @Setter private int serviceSteadyStateTimeout;
  @Getter @Setter private ResizeStrategy resizeStrategy;
  @Getter @Setter private List<AwsAutoScalarConfig> awsAutoScalarConfigs;

  @Getter @Setter private String parentRecordName;
  @Getter @Setter private String parentRecordHostedZoneId;
  @Getter @Setter private String serviceDiscoveryService1JSON;
  @Getter @Setter private String serviceDiscoveryService2JSON;

  @Inject private SecretManager secretManager;
  @Inject private EcsStateHelper ecsStateHelper;
  @Inject private ActivityService activityService;
  @Inject private AppService appService;
  @Inject private SettingsService settingsService;
  @Inject private DelegateService delegateService;
  @Inject private ArtifactCollectionUtils artifactCollectionUtils;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private ApplicationManifestUtils applicationManifestUtils;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private LogService logService;

  public EcsBlueGreenServiceSetupRoute53DNS(String name) {
    super(name, ECS_BG_SERVICE_SETUP_ROUTE53.name());
  }

  private ExecutionResponse executeInternal(ExecutionContext context) {
    boolean valuesInGit = false;
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();

    EcsSetUpDataBag dataBag = ecsStateHelper.prepareBagForEcsSetUp(context, serviceSteadyStateTimeout,
        artifactCollectionUtils, serviceResourceService, infrastructureMappingService, settingsService, secretManager);

    if (featureFlagService.isEnabled(FeatureName.ECS_REMOTE_MANIFEST, context.getAccountId())) {
      appManifestMap = applicationManifestUtils.getApplicationManifests(context, AppManifestKind.K8S_MANIFEST);
      valuesInGit = ecsStateHelper.isRemoteManifest(appManifestMap);
    }

    Activity activity = ecsStateHelper.createActivity(context, ECS_SERVICE_SETUP_COMMAND_ROUTE53, getStateType(),
        CommandUnitType.AWS_ECS_SERVICE_SETUP_ROUTE53, activityService);

    if (valuesInGit) {
      return executeGitFetchFilesTask(context, appManifestMap, activity.getUuid(), dataBag);
    } else {
      return executeEcsBGRoute53Task(context, appManifestMap, activity.getUuid(), dataBag);
    }
  }

  private ExecutionResponse executeEcsBGRoute53Task(ExecutionContext context,
      Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap, String activityId,
      EcsSetUpDataBag ecsSetUpDataBag) {
    Application app = appService.get(context.getAppId());

    ecsStateHelper.setUpRemoteContainerTaskAndServiceSpecIfRequired(context, ecsSetUpDataBag, logger);

    EcsSetupParams ecsSetupParams = (EcsSetupParams) ecsStateHelper.buildContainerSetupParams(context,
        EcsSetupStateConfig.builder()
            .useRoute53DNSSwap(true)
            .serviceDiscoveryService1JSON(serviceDiscoveryService1JSON)
            .serviceDiscoveryService2JSON(serviceDiscoveryService2JSON)
            .parentRecordHostedZoneId(parentRecordHostedZoneId)
            .parentRecordName(parentRecordName)
            .blueGreen(true)
            .app(ecsSetUpDataBag.getApplication())
            .env(ecsSetUpDataBag.getEnvironment())
            .service(ecsSetUpDataBag.getService())
            .infrastructureMapping(ecsSetUpDataBag.getEcsInfrastructureMapping())
            .clusterName(ecsSetUpDataBag.getEcsInfrastructureMapping().getClusterName())
            .containerTask(ecsSetUpDataBag.getContainerTask())
            .ecsServiceName(ecsServiceName)
            .imageDetails(ecsSetUpDataBag.getImageDetails())
            .roleArn(roleArn)
            .serviceSteadyStateTimeout(ecsSetUpDataBag.getServiceSteadyStateTimeout())
            .targetContainerName(targetContainerName)
            .targetPort(targetPort)
            .useLoadBalancer(false)
            .serviceName(ecsSetUpDataBag.getService().getName())
            .ecsServiceSpecification(ecsSetUpDataBag.getServiceSpecification())
            .isDaemonSchedulingStrategy(false)
            .awsAutoScalarConfigs(awsAutoScalarConfigs)
            .build());

    CommandStateExecutionData stateExecutionData = ecsStateHelper.getStateExecutionData(
        ecsSetUpDataBag, ECS_SERVICE_SETUP_COMMAND_ROUTE53, ecsSetupParams, activityId);

    EcsSetupContextVariableHolder variables = ecsStateHelper.renderEcsSetupContextVariables(context);

    EcsBGRoute53ServiceSetupRequest request =
        EcsBGRoute53ServiceSetupRequest.builder()
            .ecsSetupParams(ecsSetupParams)
            .awsConfig(ecsSetUpDataBag.getAwsConfig())
            .clusterName(ecsSetupParams.getClusterName())
            .region(ecsSetupParams.getRegion())
            .accountId(ecsSetUpDataBag.getApplication().getAccountId())
            .appId(ecsSetUpDataBag.getApplication().getUuid())
            .commandName(ECS_SERVICE_SETUP_COMMAND_ROUTE53)
            .activityId(activityId)
            .safeDisplayServiceVariables(variables.getSafeDisplayServiceVariables())
            .serviceVariables(variables.getServiceVariables())
            .build();

    DelegateTask task = ecsStateHelper.createAndQueueDelegateTaskForEcsServiceSetUp(
        request, ecsSetUpDataBag, activityId, delegateService);
    appendDelegateTaskDetails(context, task);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(activityId))
        .stateExecutionData(stateExecutionData)
        .build();
  }

  private ExecutionResponse executeGitFetchFilesTask(ExecutionContext context,
      Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap, String activityId,
      EcsSetUpDataBag ecsSetUpDataBag) {
    ManagerExecutionLogCallback executionLogCallback =
        ecsStateHelper.getExecutionLogCallback(context, activityId, ECS_SERVICE_SETUP_COMMAND_ROUTE53, logService);

    String logMessage = "Creating a task to fetch files from git.";
    executionLogCallback.saveExecutionLog(logMessage, CommandExecutionStatus.RUNNING);
    final DelegateTask gitFetchFileTask = createGitFetchFileAsyncTask(context, applicationManifestMap, activityId);
    delegateService.queueTask(gitFetchFileTask);

    EcsBGRoute53SetupStateExecutionData stateExecutionData =
        createStateExecutionData(activityId, applicationManifestMap, context, ecsSetUpDataBag);
    appendDelegateTaskDetails(context, gitFetchFileTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Collections.singletonList(gitFetchFileTask.getWaitId()))
        .stateExecutionData(stateExecutionData)
        .build();
  }

  private EcsBGRoute53SetupStateExecutionData createStateExecutionData(String activityId,
      Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap, ExecutionContext context,
      EcsSetUpDataBag ecsSetUpDataBag) {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    ServiceElement serviceElement = phaseElement.getServiceElement();
    Application app = appService.get(context.getAppId());

    return EcsBGRoute53SetupStateExecutionData.builder()
        .activityId(activityId)
        .accountId(ecsSetUpDataBag.getApplication().getAccountId())
        .appId(app.getUuid())
        .commandName(ECS_SERVICE_SETUP_COMMAND_ROUTE53)
        .taskType(GIT_FETCH_FILES_TASK)
        .applicationManifestMap(applicationManifestMap)
        .ecsSetUpDataBag(ecsSetUpDataBag)
        .roleArn(roleArn)
        .targetPort(targetPort)
        .maxInstances(maxInstances)
        .fixedInstances(fixedInstances)
        .ecsServiceName(ecsServiceName)
        .targetContainerName(targetContainerName)
        .desiredInstanceCount(desiredInstanceCount)
        .serviceSteadyStateTimeout(serviceSteadyStateTimeout)
        .resizeStrategy(resizeStrategy)
        .awsAutoScalarConfigs(awsAutoScalarConfigs)
        .parentRecordName(parentRecordName)
        .parentRecordHostedZoneId(parentRecordHostedZoneId)
        .serviceDiscoveryService1JSON(serviceDiscoveryService1JSON)
        .serviceDiscoveryService2JSON(serviceDiscoveryService2JSON)
        .build();
  }

  private DelegateTask createGitFetchFileAsyncTask(
      ExecutionContext context, Map<K8sValuesLocation, ApplicationManifest> appManifestMap, String activityId) {
    Application app = context.getApp();
    Environment env = ((ExecutionContextImpl) context).getEnv();
    notNullCheck("Environment is null", env, USER);
    InfrastructureMapping infraMapping = infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());
    notNullCheck("InfraStructureMapping is null", infraMapping, USER);
    GitFetchFilesTaskParams fetchFilesTaskParams =
        applicationManifestUtils.createGitFetchFilesTaskParams(context, app, appManifestMap);
    fetchFilesTaskParams.setActivityId(activityId);
    fetchFilesTaskParams.setFinalState(true);
    fetchFilesTaskParams.setAppManifestKind(AppManifestKind.K8S_MANIFEST);
    fetchFilesTaskParams.setExecutionLogName(FetchFiles);

    String waitId = generateUuid();
    return DelegateTask.builder()
        .accountId(app.getAccountId())
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, app.getUuid())
        .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, env.getUuid())
        .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infraMapping.getUuid())
        .waitId(waitId)
        .data(TaskData.builder()
                  .async(true)
                  .taskType(GIT_FETCH_FILES_TASK.name())
                  .parameters(new Object[] {fetchFilesTaskParams})
                  .timeout(TimeUnit.MINUTES.toMillis(GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT))
                  .build())
        .build();
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      return executeInternal(context);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(getMessage(e), e);
    }
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      return handleAsyncInternal(context, response);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(getMessage(e), e);
    }
  }

  @Override
  public Integer getTimeoutMillis() {
    if (serviceSteadyStateTimeout == 0) {
      return null;
    }
    return ecsStateHelper.getTimeout(serviceSteadyStateTimeout);
  }

  private ExecutionResponse handleAsyncInternal(ExecutionContext context, Map<String, ResponseData> response) {
    StateExecutionData stateExecutionData = context.getStateExecutionData();

    if (stateExecutionData instanceof EcsSetupStateExecutionData) {
      return handleAsyncInternalGitTask(context, response);
    } else if (stateExecutionData instanceof CommandStateExecutionData) {
      return handleAsyncInternalECSBGRoute53Task(context, response);
    } else {
      throw new InvalidRequestException("Unhandled task type for state " + stateExecutionData.getStateName()
          + " and class " + stateExecutionData.getClass());
    }
  }

  private ExecutionResponse handleAsyncInternalGitTask(ExecutionContext context, Map<String, ResponseData> response) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String appId = workflowStandardParams.getAppId();
    EcsBGRoute53SetupStateExecutionData ecsSetupStateExecutionData =
        (EcsBGRoute53SetupStateExecutionData) context.getStateExecutionData();

    String activityId = ecsSetupStateExecutionData.getActivityId();
    ManagerExecutionLogCallback executionLogCallback =
        ecsStateHelper.getExecutionLogCallback(context, activityId, ECS_SERVICE_SETUP_COMMAND_ROUTE53, logService);

    GitCommandExecutionResponse executionResponse = (GitCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus =
        executionResponse.getGitCommandStatus() == GitCommandExecutionResponse.GitCommandStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;

    if (ExecutionStatus.FAILED == executionStatus) {
      activityService.updateStatus(activityId, appId, executionStatus);
      executionLogCallback.saveExecutionLog("Failed to download files from Git.", CommandExecutionStatus.FAILURE);
      return ExecutionResponse.builder().executionStatus(executionStatus).build();
    }

    String logMessage = "SuccessFully Downloaded files from Git!";
    executionLogCallback.saveExecutionLog(logMessage, CommandExecutionStatus.RUNNING);

    restoreStateDataAfterGitFetch((EcsBGRoute53SetupStateExecutionData) context.getStateExecutionData());
    ecsSetupStateExecutionData.setFetchFilesResult(
        (GitFetchFilesFromMultipleRepoResult) executionResponse.getGitCommandResult());

    return executeEcsBGRoute53Task(context, ecsSetupStateExecutionData.getApplicationManifestMap(), activityId,
        ecsSetupStateExecutionData.getEcsSetUpDataBag());
  }

  private ExecutionResponse handleAsyncInternalECSBGRoute53Task(
      ExecutionContext context, Map<String, ResponseData> response) {
    String activityId = response.keySet().iterator().next();
    EcsCommandExecutionResponse executionResponse = (EcsCommandExecutionResponse) response.values().iterator().next();
    ExecutionStatus executionStatus =
        CommandExecutionStatus.SUCCESS == executionResponse.getCommandExecutionStatus() ? SUCCESS : FAILED;
    activityService.updateStatus(activityId, context.getAppId(), executionStatus);

    EcsBGRoute53ServiceSetupResponse ecsServiceSetupResponse =
        (EcsBGRoute53ServiceSetupResponse) executionResponse.getEcsCommandResponse();
    ContainerSetupCommandUnitExecutionData setupExecutionData = ecsServiceSetupResponse.getSetupData();

    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String serviceId = phaseElement.getServiceElement().getUuid();
    Artifact artifact = ((DeploymentExecutionContext) context).getDefaultArtifactForService(serviceId);
    if (artifact == null) {
      throw new InvalidArgumentsException(Pair.of("args", "Artifact is null"));
    }

    ImageDetails imageDetails =
        artifactCollectionUtils.fetchContainerImageDetails(artifact, context.getWorkflowExecutionId());
    ContainerServiceElement containerServiceElement = ecsStateHelper.buildContainerServiceElement(context,
        setupExecutionData, executionStatus, imageDetails, getMaxInstances(), getFixedInstances(),
        getDesiredInstanceCount(), getResizeStrategy(), getServiceSteadyStateTimeout(), logger);

    CommandStateExecutionData executionData = (CommandStateExecutionData) context.getStateExecutionData();
    ecsStateHelper.populateFromDelegateResponse(setupExecutionData, executionData, containerServiceElement);
    sweepingOutputService.save(
        context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
            .name(ecsStateHelper.getSweepingOutputName(context, false, ECS_SERVICE_SETUP_SWEEPING_OUTPUT_NAME))
            .value(containerServiceElement)
            .build());

    executionData.setDelegateMetaInfo(executionResponse.getDelegateMetaInfo());
    return ExecutionResponse.builder()
        .stateExecutionData(context.getStateExecutionData())
        .executionStatus(executionStatus)
        .build();
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> invalidFields = newHashMap();
    if (isEmpty(serviceDiscoveryService1JSON)) {
      invalidFields.put("serviceDiscoveryService1JSON", "Specification - 1 of Service Discovery should not be empty");
    }
    if (isEmpty(serviceDiscoveryService2JSON)) {
      invalidFields.put("serviceDiscoveryService2JSON", "Specification - 2 of Service Discovery should not be empty");
    }
    if (isEmpty(parentRecordName)) {
      invalidFields.put("parentRecordName", "Canonical Name should not be empty");
    }
    if (isEmpty(parentRecordHostedZoneId)) {
      invalidFields.put("parentRecordHostedZoneId", "Zone hosting alias should not be empty");
    }
    return invalidFields;
  }

  public void restoreStateDataAfterGitFetch(EcsBGRoute53SetupStateExecutionData stateExecutionData) {
    this.roleArn = stateExecutionData.getRoleArn();
    this.awsAutoScalarConfigs = stateExecutionData.getAwsAutoScalarConfigs();
    this.desiredInstanceCount = stateExecutionData.getDesiredInstanceCount();
    this.ecsServiceName = stateExecutionData.getEcsServiceName();
    this.fixedInstances = stateExecutionData.getFixedInstances();
    this.maxInstances = stateExecutionData.getMaxInstances();
    this.serviceSteadyStateTimeout = stateExecutionData.getServiceSteadyStateTimeout();
    this.targetContainerName = stateExecutionData.getTargetContainerName();
    this.targetPort = stateExecutionData.getTargetPort();
    this.resizeStrategy = stateExecutionData.getResizeStrategy();
    this.parentRecordName = stateExecutionData.getParentRecordName();
    this.parentRecordHostedZoneId = stateExecutionData.getParentRecordHostedZoneId();
    this.serviceDiscoveryService1JSON = stateExecutionData.getServiceDiscoveryService1JSON();
    this.serviceDiscoveryService2JSON = stateExecutionData.getServiceDiscoveryService2JSON();
  }
}