/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.entityusageactivity.EntityUsageTypes.PIPELINE_EXECUTION;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskChainExecutable;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.k8s.K8sApplyBaseStepInfo.K8sApplyBaseStepInfoKeys;
import io.harness.cdng.k8s.beans.CustomFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.k8s.K8sApplyRequest;
import io.harness.delegate.task.k8s.K8sApplyRequest.K8sApplyRequestBuilder;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.delegate.task.localstore.ManifestFiles;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.EntityUsageDetailProto;
import io.harness.eventsframework.schemas.entity.PipelineExecutionUsageDataProto;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.exception.KubernetesExceptionMessages;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.secretusage.SecretRuntimeUsageService;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.telemetry.helpers.StepExecutionTelemetryEventDTO;
import io.harness.walktree.visitor.entityreference.beans.VisitedSecretReference;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class K8sApplyStep extends CdTaskChainExecutable implements K8sStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.K8S_APPLY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  public static final String MANIFEST_SOURCE = "manifest_source";
  public static final String FILE_PATHS = "file_paths";
  public static final String SKIP_RENDERING = "skip_rendering";
  public static final String SKIP_DRY_RUN = "skip_dry_run";
  public static final String SKIP_STEADY_STATE_CHECK = "skip_steady_state_check";
  private final String K8S_APPLY_COMMAND_NAME = "K8s Apply";

  @Inject private K8sStepHelper k8sStepHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private SecretRuntimeUsageService secretRuntimeUsageService;
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {
    // Noop
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepBaseParameters stepElementParameters, StepInputPackage inputPackage) {
    K8sApplyStepParameters k8sApplyStepParameters = (K8sApplyStepParameters) stepElementParameters.getSpec();
    if (isNotEmpty(k8sApplyStepParameters.getOverrides())) {
      k8sStepHelper.resolveManifestsConfigExpressions(ambiance, k8sApplyStepParameters.getOverrides());
    }

    publishSecretRuntimeUsage(ambiance, k8sApplyStepParameters);
    if (k8sApplyStepParameters.getManifestSource() != null) {
      validateManifestSource(ambiance, k8sApplyStepParameters);
    } else {
      validateFilePaths(k8sApplyStepParameters);
    }
    validateManifestType(ambiance);
    return k8sStepHelper.startChainLink(this, ambiance, stepElementParameters);
  }

  private void validateManifestType(Ambiance ambiance) {
    ManifestsOutcome manifestsOutcomes = k8sStepHelper.resolveManifestsOutcome(ambiance);
    ManifestOutcome manifestOutcome = k8sStepHelper.getK8sSupportedManifestOutcome(manifestsOutcomes.values());
    if (ManifestType.OpenshiftTemplate.equals(manifestOutcome.getType())) {
      throw new UnsupportedOperationException(format("Unsupported Manifest type: [%s]", manifestOutcome.getType()));
    }
  }

  private void validateFilePaths(K8sApplyStepParameters k8sApplyStepParameters) {
    if (ParameterField.isNull(k8sApplyStepParameters.getFilePaths())) {
      throw new InvalidRequestException("File/Folder path must be present");
    }

    if (isEmpty(getParameterFieldValue(k8sApplyStepParameters.getFilePaths()))) {
      throw new InvalidRequestException("File/Folder path must be present");
    }

    List<String> filePaths = getParameterFieldValue(k8sApplyStepParameters.getFilePaths());
    for (String filePath : filePaths) {
      if (isEmpty(filePath)) {
        throw new InvalidRequestException("File/Folder path must be present");
      }
    }
  }

  private void validateManifestSource(Ambiance ambiance, K8sApplyStepParameters k8sApplyStepParameters) {
    if (!ManifestType.K8Manifest.equals(k8sApplyStepParameters.getManifestSource().getType().getDisplayName())) {
      throw new UnsupportedOperationException(
          format("K8s Apply step manifest source only supports manifests of type: [%s], and [%s] is provided",
              ManifestType.K8Manifest, k8sApplyStepParameters.getManifestSource().getType()));
    }
    k8sStepHelper.resolveManifestsSourceExpressions(ambiance, k8sApplyStepParameters.getManifestSource());
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepBaseParameters stepElementParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    return k8sStepHelper.executeNextLink(this, ambiance, stepElementParameters, passThroughData, responseSupplier);
  }

  @Override
  public TaskChainResponse executeK8sTask(ManifestOutcome k8sManifestOutcome, Ambiance ambiance,
      StepBaseParameters stepElementParameters, List<String> manifestOverrideContents,
      K8sExecutionPassThroughData executionPassThroughData, boolean shouldOpenFetchFilesLogStream,
      UnitProgressData unitProgressData) {
    InfrastructureOutcome infrastructure = executionPassThroughData.getInfrastructure();
    String releaseName = cdStepHelper.getReleaseName(ambiance, infrastructure);
    K8sApplyStepParameters k8sApplyStepParameters = (K8sApplyStepParameters) stepElementParameters.getSpec();
    boolean skipDryRun = CDStepHelper.getParameterFieldBooleanValue(
        k8sApplyStepParameters.getSkipDryRun(), K8sApplyBaseStepInfoKeys.skipDryRun, stepElementParameters);
    boolean skipSteadyStateCheck =
        CDStepHelper.getParameterFieldBooleanValue(k8sApplyStepParameters.getSkipSteadyStateCheck(),
            K8sApplyBaseStepInfoKeys.skipSteadyStateCheck, stepElementParameters);
    boolean skipRendering = CDStepHelper.getParameterFieldBooleanValue(
        k8sApplyStepParameters.getSkipRendering(), K8sApplyBaseStepInfoKeys.skipRendering, stepElementParameters);
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    K8sApplyRequestBuilder applyRequestBuilder =
        K8sApplyRequest.builder()
            .skipDryRun(skipDryRun)
            .releaseName(releaseName)
            .commandName(K8S_APPLY_COMMAND_NAME)
            .taskType(K8sTaskType.APPLY)
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .valuesYamlList(k8sStepHelper.renderValues(k8sManifestOutcome, ambiance, manifestOverrideContents))
            .k8sInfraDelegateConfig(cdStepHelper.getK8sInfraDelegateConfig(infrastructure, ambiance))
            .kustomizePatchesList(k8sStepHelper.renderPatches(k8sManifestOutcome, ambiance, manifestOverrideContents))
            .manifestDelegateConfig(
                k8sStepHelper.getManifestDelegateConfigWrapper(executionPassThroughData.getZippedManifestId(),
                    k8sManifestOutcome, ambiance, executionPassThroughData.getManifestFiles()))
            .accountId(accountId)
            .deprecateFabric8Enabled(true)
            .skipSteadyStateCheck(skipSteadyStateCheck)
            .shouldOpenFetchFilesLogStream(shouldOpenFetchFilesLogStream)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .useLatestKustomizeVersion(cdStepHelper.isUseLatestKustomizeVersion(accountId))
            .useNewKubectlVersion(cdStepHelper.isUseNewKubectlVersion(accountId))
            .useK8sApiForSteadyStateCheck(cdStepHelper.shouldUseK8sApiForSteadyStateCheck(accountId))
            .skipRendering(skipRendering);

    applyRequestBuilder.serviceHooks(k8sStepHelper.getServiceHooks(ambiance));

    if (k8sManifestOutcome != null && k8sManifestOutcome.getStore() != null
        && HARNESS_STORE_TYPE.equals(k8sManifestOutcome.getStore().getKind())
        && isNotEmpty(getParameterFieldValue(k8sApplyStepParameters.getFilePaths()))) {
      // if Harness Store has been used for Manifest all specified file paths should be present in the manifestFiles
      // should only cover if the manifest is specified in the service definition
      validateFilePathsAgainstManifestFiles(
          getParameterFieldValue(k8sApplyStepParameters.getFilePaths()), executionPassThroughData.getManifestFiles());
    }

    setFilePathsInRequest(k8sManifestOutcome, k8sApplyStepParameters, applyRequestBuilder, accountId);

    Map<String, String> k8sCommandFlag =
        k8sStepHelper.getDelegateK8sCommandFlag(k8sApplyStepParameters.getCommandFlags(), ambiance);
    applyRequestBuilder.k8sCommandFlags(k8sCommandFlag);

    K8sApplyRequest k8sApplyRequest = applyRequestBuilder.build();
    k8sStepHelper.publishReleaseNameStepDetails(ambiance, releaseName);

    return k8sStepHelper.queueK8sTask(stepElementParameters, k8sApplyRequest, ambiance, executionPassThroughData);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepBaseParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof CustomFetchResponsePassThroughData) {
      return k8sStepHelper.handleCustomTaskFailure((CustomFetchResponsePassThroughData) passThroughData);
    }

    if (passThroughData instanceof GitFetchResponsePassThroughData) {
      return cdStepHelper.handleGitTaskFailure((GitFetchResponsePassThroughData) passThroughData);
    }

    if (passThroughData instanceof HelmValuesFetchResponsePassThroughData) {
      return k8sStepHelper.handleHelmValuesFetchFailure((HelmValuesFetchResponsePassThroughData) passThroughData);
    }

    if (passThroughData instanceof StepExceptionPassThroughData) {
      return cdStepHelper.handleStepExceptionFailure((StepExceptionPassThroughData) passThroughData);
    }

    K8sDeployResponse k8sTaskExecutionResponse;
    try {
      k8sTaskExecutionResponse = (K8sDeployResponse) responseDataSupplier.get();
    } catch (Exception e) {
      log.error("Error while processing K8s Task response: {}", e.getMessage(), e);
      return k8sStepHelper.handleTaskException(ambiance, (K8sExecutionPassThroughData) passThroughData, e);
    }

    StepResponseBuilder stepResponseBuilder =
        StepResponse.builder().unitProgressList(k8sTaskExecutionResponse.getCommandUnitsProgress().getUnitProgresses());

    if (k8sTaskExecutionResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return K8sStepHelper.getFailureResponseBuilder(k8sTaskExecutionResponse, stepResponseBuilder).build();
    }
    return stepResponseBuilder.status(Status.SUCCEEDED).build();
  }

  @Override
  protected StepExecutionTelemetryEventDTO getStepExecutionTelemetryEventDTO(
      Ambiance ambiance, StepBaseParameters stepParameters, PassThroughData passThroughData) {
    K8sApplyStepParameters k8sApplyStepParameters = (K8sApplyStepParameters) stepParameters.getSpec();
    HashMap<String, Object> telemetryProperties = new HashMap<>();
    if (k8sApplyStepParameters.getManifestSource() != null) {
      telemetryProperties.put(MANIFEST_SOURCE, true);
    } else {
      telemetryProperties.put(FILE_PATHS, true);
    }
    telemetryProperties.put(
        SKIP_RENDERING, ParameterFieldHelper.getBooleanParameterFieldValue(k8sApplyStepParameters.getSkipRendering()));
    telemetryProperties.put(
        SKIP_DRY_RUN, ParameterFieldHelper.getBooleanParameterFieldValue(k8sApplyStepParameters.getSkipDryRun()));
    telemetryProperties.put(SKIP_STEADY_STATE_CHECK,
        ParameterFieldHelper.getBooleanParameterFieldValue(k8sApplyStepParameters.getSkipSteadyStateCheck()));

    return StepExecutionTelemetryEventDTO.builder()
        .stepType(STEP_TYPE.getType())
        .properties(telemetryProperties)
        .build();
  }

  private void publishSecretRuntimeUsage(Ambiance ambiance, K8sApplyStepParameters stepParameters) {
    Set<VisitedSecretReference> secretReferences = new HashSet<>();
    if (isNotEmpty(stepParameters.getOverrides())) {
      stepParameters.getOverrides()
          .stream()
          .map(override -> entityReferenceExtractorUtils.extractReferredSecrets(ambiance, override))
          .filter(EmptyPredicate::isNotEmpty)
          .forEach(secretReferences::addAll);
    }

    if (stepParameters.getManifestSource() != null) {
      Set<VisitedSecretReference> manifestSourceSecretRefs =
          entityReferenceExtractorUtils.extractReferredSecrets(ambiance, stepParameters.getManifestSource());
      if (isNotEmpty(manifestSourceSecretRefs)) {
        secretReferences.addAll(manifestSourceSecretRefs);
      }
    }

    if (isEmpty(secretReferences)) {
      return;
    }

    secretRuntimeUsageService.createSecretRuntimeUsage(secretReferences,
        EntityUsageDetailProto.newBuilder()
            .setPipelineExecutionUsageData(PipelineExecutionUsageDataProto.newBuilder()
                                               .setPlanExecutionId(ambiance.getPlanExecutionId())
                                               .setStageExecutionId(ambiance.getStageExecutionId())
                                               .build())
            .setUsageType(PIPELINE_EXECUTION)
            .setEntityType(EntityTypeProtoEnum.PIPELINES)
            .setIdentifierRef(IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
                AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
                AmbianceUtils.getProjectIdentifier(ambiance), AmbianceUtils.getPipelineIdentifier(ambiance)))
            .build());
  }

  private void setFilePathsInRequest(ManifestOutcome k8sManifestOutcome, K8sApplyStepParameters k8sApplyStepParameters,
      K8sApplyRequestBuilder applyRequestBuilder, String accountId) {
    if (!isEmpty(getParameterFieldValue(k8sApplyStepParameters.getFilePaths()))) {
      applyRequestBuilder.filePaths(k8sApplyStepParameters.getFilePaths().getValue());
    } else if (cdFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_K8S_APPLY_MANIFEST_WITHOUT_SERVICE_NG)) {
      applyRequestBuilder.filePaths(k8sManifestOutcome.getStore().retrieveFilePaths());
      applyRequestBuilder.useManifestSource(true);
    } else {
      throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.APPLY_NO_FILEPATH_SPECIFIED,
          KubernetesExceptionExplanation.APPLY_NO_FILEPATH_SPECIFIED,
          new KubernetesTaskException(KubernetesExceptionMessages.APPLY_NO_FILEPATH_SPECIFIED));
    }
  }

  private void validateFilePathsAgainstManifestFiles(List<String> filePaths, List<ManifestFiles> manifestFiles) {
    if (manifestFiles != null) {
      Set<String> manifestPaths =
          manifestFiles.stream().map(ManifestFiles::getFilePath).filter(Objects::nonNull).collect(Collectors.toSet());
      Set<String> normalizedFilePaths =
          filePaths.stream().map(path -> path.charAt(0) == '/' ? path : "/" + path).collect(Collectors.toSet());

      normalizedFilePaths.removeAll(manifestPaths);
      if (isNotEmpty(normalizedFilePaths)) {
        throw new KubernetesTaskException(
            format(KubernetesExceptionMessages.PROVIDED_PATHS_ARE_NOT_PART_OF_THE_MANIFEST,
                String.join("\n- ", normalizedFilePaths)));
      }
    }
  }
}
