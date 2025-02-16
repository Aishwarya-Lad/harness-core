/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.CDS_DIVIDE_SDK_RESPONSE_EVENTS_IN_DIFF_STREAMS;
import static io.harness.beans.FeatureName.CDS_INPUT_YAML_IN_WEBHOOK_NOTIFICATION;
import static io.harness.beans.FeatureName.CDS_NG_BARRIER_STEPS_WITHIN_LOOPING_STRATEGIES;
import static io.harness.beans.FeatureName.CDS_NG_STRATEGY_IDENTIFIER_POSTFIX_TRUNCATION_REFACTOR;
import static io.harness.beans.FeatureName.CDS_REMOVE_RESUME_EVENT_FOR_ASYNC_AND_ASYNCCHAIN_MODE;
import static io.harness.beans.FeatureName.PIE_EXPRESSION_CONCATENATION;
import static io.harness.beans.FeatureName.PIE_EXPRESSION_DISABLE_COMPLEX_JSON_SUPPORT;
import static io.harness.beans.FeatureName.PIE_SECRETS_OBSERVER;
import static io.harness.beans.FeatureName.PIE_SIMPLIFY_LOG_BASE_KEY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.ngsettings.SettingIdentifiers.RUN_RBAC_VALIDATION_BEFORE_EXECUTING_INLINE_PIPELINES;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;
import static io.harness.utils.ExecutionModeUtils.isRollbackMode;

import static java.lang.String.format;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.OrchestrationService;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.executions.retry.RetryExecutionParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.WingsException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.Builder;
import io.harness.execution.RetryStagesMetadata;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.logging.AutoLogContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.notification.bean.NotificationRules;
import io.harness.opaclient.model.OpaConstants;
import io.harness.plan.Node;
import io.harness.plan.Plan;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStoreType;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.pms.contracts.plan.RerunInfo;
import io.harness.pms.contracts.plan.RetryExecutionInfo;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.exception.PmsExceptionUtils;
import io.harness.pms.expression.helper.InputSetMergeHelperV1;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.helpers.TriggeredByHelper;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetSanitizer;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.api.PipelinesApiUtils;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.mappers.ExecutionGraphMapper;
import io.harness.pms.pipeline.mappers.PipelineExecutionSummaryDtoMapper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.pipeline.service.PipelineEnforcementService;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.pipelinestage.helper.PipelineStageHelper;
import io.harness.pms.plan.creation.NodeTypeLookupService;
import io.harness.pms.plan.creation.PlanCreatorMergeService;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.plan.execution.beans.ExecArgs;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineMetadataInternalDTO;
import io.harness.pms.plan.execution.beans.ProcessStageExecutionInfoResult;
import io.harness.pms.plan.execution.beans.StagesExecutionInfo;
import io.harness.pms.plan.execution.beans.dto.ChildExecutionDetailDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionDetailDTO;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.validator.PipelineRbacService;
import io.harness.pms.stages.StagesExpressionExtractor;
import io.harness.pms.utils.NGPipelineSettingsConstant;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.preprocess.YamlPreProcessorFactory;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.template.yaml.TemplateRefHelper;
import io.harness.threading.Morpheus;
import io.harness.utils.PmsFeatureFlagHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.ws.rs.InternalServerErrorException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class ExecutionHelper {
  NGSettingsClient settingsClient;
  PMSPipelineService pmsPipelineService;
  PipelineMetadataService pipelineMetadataService;
  PMSPipelineServiceHelper pmsPipelineServiceHelper;
  PipelineGovernanceService pipelineGovernanceService;
  TriggeredByHelper triggeredByHelper;
  PlanExecutionService planExecutionService;
  PrincipalInfoHelper principalInfoHelper;
  PmsGitSyncHelper pmsGitSyncHelper;
  PMSYamlSchemaService pmsYamlSchemaService;
  PipelineRbacService pipelineRbacServiceImpl;
  RetryExecutionHelper retryExecutionHelper;
  PlanCreatorMergeService planCreatorMergeService;
  OrchestrationService orchestrationService;
  PmsExecutionSummaryRepository pmsExecutionSummaryRespository;
  PlanService planService;
  PlanExecutionMetadataService planExecutionMetadataService;
  PMSPipelineTemplateHelper pipelineTemplateHelper;
  PipelineEnforcementService pipelineEnforcementService;
  PmsFeatureFlagHelper featureFlagService;
  PMSExecutionService pmsExecutionService;
  AccessControlClient accessControlClient;
  PipelineStageHelper pipelineStageHelper;
  NodeExecutionService nodeExecutionService;
  RollbackModeExecutionHelper rollbackModeExecutionHelper;
  RollbackGraphGenerator rollbackGraphGenerator;
  YamlPreProcessorFactory yamlPreProcessorFactory;
  NodeTypeLookupService nodeTypeLookupService;
  MetricService metricService;

  // Add all FFs to this list that we want to use during pipeline execution
  public final List<FeatureName> featureNames =
      List.of(PIE_EXPRESSION_CONCATENATION, PIE_EXPRESSION_DISABLE_COMPLEX_JSON_SUPPORT, PIE_SIMPLIFY_LOG_BASE_KEY,
          CDS_NG_BARRIER_STEPS_WITHIN_LOOPING_STRATEGIES, CDS_REMOVE_RESUME_EVENT_FOR_ASYNC_AND_ASYNCCHAIN_MODE,
          PIE_SECRETS_OBSERVER, CDS_DIVIDE_SDK_RESPONSE_EVENTS_IN_DIFF_STREAMS, CDS_INPUT_YAML_IN_WEBHOOK_NOTIFICATION,
          CDS_NG_STRATEGY_IDENTIFIER_POSTFIX_TRUNCATION_REFACTOR);
  public static final String PMS_EXECUTION_SETTINGS_GROUP_IDENTIFIER = "pms_execution_settings";
  public static final String PLAN_CREATION_TIME_METRIC_NAME = "plan_creation_time";

  public PipelineEntity fetchPipelineEntity(@NotNull String accountId, @NotNull String orgIdentifier,
      @NotNull String projectIdentifier, @NotNull String pipelineIdentifier) {
    Optional<PipelineEntity> pipelineEntityOptional =
        pmsPipelineService.getPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false);
    if (!pipelineEntityOptional.isPresent()) {
      throw new InvalidRequestException(
          String.format("Pipeline with the given ID: %s does not exist or has been deleted", pipelineIdentifier));
    }
    return pipelineEntityOptional.get();
  }

  public ExecutionTriggerInfo buildTriggerInfo(String originalExecutionId) {
    ExecutionTriggerInfo.Builder triggerInfoBuilder =
        ExecutionTriggerInfo.newBuilder().setTriggerType(MANUAL).setTriggeredBy(
            triggeredByHelper.getFromSecurityContext());

    if (originalExecutionId == null) {
      return triggerInfoBuilder.setIsRerun(false).build();
    }

    ExecutionMetadata metadata = planExecutionService.getExecutionMetadataFromPlanExecution(originalExecutionId);
    ExecutionTriggerInfo originalTriggerInfo = metadata.getTriggerInfo();
    RerunInfo.Builder rerunInfoBuilder = RerunInfo.newBuilder()
                                             .setPrevExecutionId(originalExecutionId)
                                             .setPrevTriggerType(originalTriggerInfo.getTriggerType());
    if (originalTriggerInfo.getIsRerun()) {
      return triggerInfoBuilder.setIsRerun(true)
          .setRerunInfo(rerunInfoBuilder.setRootExecutionId(originalTriggerInfo.getRerunInfo().getRootExecutionId())
                            .setRootTriggerType(originalTriggerInfo.getRerunInfo().getRootTriggerType())
                            .build())
          .build();
    }

    return triggerInfoBuilder.setIsRerun(true)
        .setRerunInfo(rerunInfoBuilder.setRootExecutionId(originalExecutionId)
                          .setRootTriggerType(originalTriggerInfo.getTriggerType())
                          .build())
        .build();
  }

  boolean validForDebug(PipelineEntity pipelineEntity) {
    List<String> modules = PipelinesApiUtils.getModules(pipelineEntity);

    return (modules != null) && (modules.contains("ci"));
  }

  public ExecArgs buildExecutionArgs(PipelineEntity pipelineEntity, String moduleType, String mergedRuntimeInputYaml,
      List<String> stagesToRun, Map<String, String> expressionValues, ExecutionTriggerInfo triggerInfo,
      String originalExecutionId, RetryExecutionParameters retryExecutionParameters, boolean notifyOnlyUser,
      boolean isDebug) {
    JsonNode mergedRuntimeInputJsonNode = null;
    if (isNotEmpty(mergedRuntimeInputYaml)) {
      mergedRuntimeInputJsonNode = YamlUtils.readAsJsonNode(mergedRuntimeInputYaml);
    }
    return buildExecutionArgs(pipelineEntity, moduleType, stagesToRun, expressionValues, triggerInfo,
        originalExecutionId, retryExecutionParameters, notifyOnlyUser, isDebug, null, mergedRuntimeInputJsonNode);
  }

  @SneakyThrows
  public ExecArgs buildExecutionArgs(PipelineEntity pipelineEntity, String moduleType, List<String> stagesToRun,
      Map<String, String> expressionValues, ExecutionTriggerInfo triggerInfo, String originalExecutionId,
      RetryExecutionParameters retryExecutionParameters, boolean notifyOnlyUser, boolean isDebug, String notes,
      JsonNode mergedRuntimeInputJsonNode) {
    long start = System.currentTimeMillis();
    final String executionId = generateUuid();

    if (isDebug && !validForDebug(pipelineEntity)) {
      throw new InvalidRequestException(
          String.format("Debug executions are not allowed for pipeline [%s]", pipelineEntity.getIdentifier()));
    }
    try (AutoLogContext ignore =
             PlanCreatorUtils.autoLogContext(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
                 pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(), executionId)) {
      PipelineMetadataInternalDTO pipelineMetadataInternalDTO =
          getPipelineMetadataInternalDTO(pipelineEntity, mergedRuntimeInputJsonNode);

      // This will only be non-null in case of V0 for now.
      BasicPipeline basicPipeline = pipelineMetadataInternalDTO.getBasicPipeline();
      String pipelineYamlWithTemplateRef = pipelineMetadataInternalDTO.getPipelineYamlWithTemplateRef();
      List<NotificationRules> notificationRules = new ArrayList<>();
      boolean allowedStageExecution = false;
      if (HarnessYamlVersion.V0.equals(pipelineEntity.getHarnessVersion())) {
        if (basicPipeline != null) {
          notificationRules = basicPipeline.getNotificationRules();
          allowedStageExecution = pipelineMetadataInternalDTO.getBasicPipeline().isAllowStageExecutions();
        }
      } else {
        allowedStageExecution = true;
      }

      // TODO(Shalini): Change these methods to use jsonNode instead of yaml in processing.
      // This method throws error if stagesToRun is empty when allowedStageExecution is true. So, this needs to be done
      // before validating yaml schema, else error propagation would be different.
      ProcessStageExecutionInfoResult processStageExecutionInfoResult =
          processStageExecutionInfo(stagesToRun, allowedStageExecution, pipelineEntity,
              pipelineMetadataInternalDTO.getPipelineYaml(), pipelineYamlWithTemplateRef, expressionValues);
      StagesExecutionInfo stagesExecutionInfo = processStageExecutionInfoResult.getStagesExecutionInfo();
      pipelineYamlWithTemplateRef = processStageExecutionInfoResult.getFilteredPipelineYamlWithTemplateRef();

      validateYamlSchema(pipelineEntity, mergedRuntimeInputJsonNode);
      PlanExecutionMetadata planExecutionMetadata;
      // RetryExecutionInfo
      RetryExecutionInfo retryExecutionInfo = buildRetryInfo(retryExecutionParameters.isRetry(), originalExecutionId);

      if (!EmptyPredicate.isEmpty(mergedRuntimeInputJsonNode)) {
        planExecutionMetadata = buildPlanExecutionMetadata(pipelineEntity,
            YamlUtils.writeYamlString(mergedRuntimeInputJsonNode), originalExecutionId, retryExecutionParameters,
            notifyOnlyUser, notes, executionId, stagesExecutionInfo, pipelineYamlWithTemplateRef, retryExecutionInfo);
      } else {
        planExecutionMetadata = buildPlanExecutionMetadata(pipelineEntity, null, originalExecutionId,
            retryExecutionParameters, notifyOnlyUser, notes, executionId, stagesExecutionInfo,
            pipelineYamlWithTemplateRef, retryExecutionInfo);
      }

      ExecutionMetadata executionMetadata =
          buildExecutionMetadata(pipelineEntity.getIdentifier(), moduleType, triggerInfo, pipelineEntity, executionId,
              notificationRules, isDebug, pipelineMetadataInternalDTO.getProcessedYamlVersion());
      return ExecArgs.builder().metadata(executionMetadata).planExecutionMetadata(planExecutionMetadata).build();
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          String.format(
              "Failed to start execution for Pipeline with identifier [%s] in Project [%s] of Org [%s]. Error Message: %s",
              pipelineEntity.getIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getOrgIdentifier(),
              e.getMessage()),
          e);
      throw new InvalidRequestException("Failed to start execution for Pipeline.", e);
    } finally {
      log.info("[PMS_EXECUTE] Pipeline build execution args took time {}ms", System.currentTimeMillis() - start);
    }
  }

  private PipelineMetadataInternalDTO getPipelineMetadataInternalDTO(
      PipelineEntity pipelineEntity, JsonNode mergedRuntimeInputJsonNode) throws Exception {
    String pipelineYaml;
    String pipelineYamlWithTemplateRef;
    BasicPipeline basicPipeline = null;
    TemplateMergeResponseDTO templateMergeResponseDTO;
    switch (pipelineEntity.getHarnessVersion()) {
      case HarnessYamlVersion.V1:
        pipelineYaml = InputSetMergeHelperV1.mergeInputSetIntoPipelineYaml(
            mergedRuntimeInputJsonNode, YamlUtils.readAsJsonNode(pipelineEntity.getYaml()));
        // Adds ids in all the stages and steps where it doesn't already exists
        // For templates, the ids will be added by template service during template resolution
        pipelineYaml = pmsPipelineServiceHelper.preProcessPipelineYaml(pipelineYaml);
        pipelineYamlWithTemplateRef = pipelineYaml;
        templateMergeResponseDTO = getPipelineYamlAndValidateStaticallyReferredEntities(
            YamlUtils.readAsJsonNode(pipelineYaml), pipelineEntity, System.currentTimeMillis());
        pipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
        break;
      case HarnessYamlVersion.V0:
        templateMergeResponseDTO =
            getPipelineYamlAndValidateStaticallyReferredEntities(mergedRuntimeInputJsonNode, pipelineEntity);
        pipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
        pipelineYamlWithTemplateRef = templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef();
        basicPipeline = YamlUtils.read(pipelineYaml, BasicPipeline.class);
        break;
      default:
        throw new InvalidRequestException("version not supported");
    }
    return PipelineMetadataInternalDTO.builder()
        .pipelineYaml(pipelineYaml)
        .basicPipeline(basicPipeline)
        .pipelineYamlWithTemplateRef(pipelineYamlWithTemplateRef)
        .processedYamlVersion(templateMergeResponseDTO.getProcessedYamlVersion())
        .build();
  }

  private PlanExecutionMetadata buildPlanExecutionMetadata(PipelineEntity pipelineEntity, String mergedRuntimeInputYaml,
      String originalExecutionId, RetryExecutionParameters retryExecutionParameters, boolean notifyOnlyUser,
      String notes, String executionId, StagesExecutionInfo stagesExecutionInfo, String pipelineYamlWithTemplateRef,
      RetryExecutionInfo retryExecutionInfo) throws Exception {
    Builder planExecutionMetadataBuilder = obtainPlanExecutionMetadata(mergedRuntimeInputYaml, executionId,
        stagesExecutionInfo, originalExecutionId, retryExecutionParameters, notifyOnlyUser, notes, pipelineEntity);
    // TODO - @utkarsh @brijesh - CDS-85458 - Add Enforcement Check for V1 Pipelines
    if (HarnessYamlVersion.V0.equals(pipelineEntity.getHarnessVersion())) {
      if (stagesExecutionInfo.isStagesExecution()) {
        pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(pipelineEntity.getAccountId(),
            YamlUtils.extractPipelineField(planExecutionMetadataBuilder.build().getProcessedYaml()));
      } else {
        pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(pipelineEntity);
      }
    }

    String branch = GitAwareContextHelper.getBranchInRequestOrFromSCMGitMetadata();
    String expandedJson = pipelineGovernanceService.fetchExpandedPipelineJSONFromYaml(
        pipelineEntity, pipelineYamlWithTemplateRef, branch, OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN);
    planExecutionMetadataBuilder.expandedPipelineJson(expandedJson);
    if (retryExecutionParameters.isRetry()) {
      planExecutionMetadataBuilder.retryStagesMetadata(
          RetryStagesMetadata.builder()
              .retryStagesIdentifier(retryExecutionParameters.getRetryStagesIdentifier())
              .skipStagesIdentifier(retryExecutionParameters.getIdentifierOfSkipStages())
              .build());
    }
    if (retryExecutionInfo != null) {
      planExecutionMetadataBuilder.retryExecutionInfo(retryExecutionInfo);
    }
    return planExecutionMetadataBuilder.build();
  }

  public void validateYamlSchema(PipelineEntity pipelineEntity, JsonNode mergedRuntimeInputJsonNode) {
    /*
    For schema validations, we don't want input set validators to be appended. For example, if some timeout field in
    the pipeline is <+input>.allowedValues(12h, 1d), and the runtime input gives a value 12h, the value for this field
    in pipelineYamlJsonNode will be 12h.allowedValues(12h, 1d) for validation during execution. However, this value will
    give an error in schema validation. That's why we need a value that doesn't have this validator appended.
     */
    JsonNode jsonNodeForValidatingSchema =
        getPipelineYamlWithUnResolvedTemplates(mergedRuntimeInputJsonNode, pipelineEntity);
    pmsYamlSchemaService.validateYamlSchema(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
        pipelineEntity.getProjectIdentifier(), jsonNodeForValidatingSchema, pipelineEntity.getHarnessVersion());
  }

  private ExecutionMetadata buildExecutionMetadata(@NotNull String pipelineIdentifier, String moduleType,
      ExecutionTriggerInfo triggerInfo, PipelineEntity pipelineEntity, String executionId,
      List<NotificationRules> notificationRules, boolean isDebug, String processedYamlVersion) {
    ExecutionMetadata.Builder builder = ExecutionMetadata.newBuilder()
                                            .setExecutionUuid(executionId)
                                            .setTriggerInfo(triggerInfo)
                                            .setModuleType(EmptyPredicate.isEmpty(moduleType) ? "" : moduleType)
                                            .setPipelineIdentifier(pipelineIdentifier)
                                            .setPrincipalInfo(principalInfoHelper.getPrincipalInfoFromSecurityContext())
                                            .setIsNotificationConfigured(isNotEmpty(notificationRules))
                                            .setHarnessVersion(pipelineEntity.getHarnessVersion())
                                            .setIsDebug(isDebug)
                                            .setProcessedYamlVersion(processedYamlVersion)
                                            .setExecutionMode(ExecutionMode.NORMAL);
    ByteString gitSyncBranchContext = pmsGitSyncHelper.getGitSyncBranchContextBytesThreadLocal(
        pipelineEntity, pipelineEntity.getStoreType(), pipelineEntity.getRepo(), pipelineEntity.getConnectorRef());
    if (gitSyncBranchContext != null) {
      builder.setGitSyncBranchContext(gitSyncBranchContext);
    }
    PipelineStoreType pipelineStoreType = StoreTypeMapper.fromStoreType(pipelineEntity.getStoreType());
    if (pipelineStoreType != null) {
      builder.setPipelineStoreType(pipelineStoreType);
    }
    if (pipelineEntity.getConnectorRef() != null) {
      builder.setPipelineConnectorRef(pipelineEntity.getConnectorRef());
    }
    // adding metadata populated by Pipeline NG Settings
    updateSettingsInExecutionMetadataBuilder(pipelineEntity, builder);
    updateFeatureFlagsInExecutionMetadataBuilder(pipelineEntity.getAccountIdentifier(), featureNames, builder);
    return builder.build();
  }

  public JsonNode getPipelineYamlWithUnResolvedTemplates(
      JsonNode mergedRuntimeInputJsonNode, PipelineEntity pipelineEntity) {
    JsonNode pipelineJsonNodeForSchemaValidations;
    if (EmptyPredicate.isEmpty(mergedRuntimeInputJsonNode)) {
      pipelineJsonNodeForSchemaValidations = YamlUtils.readAsJsonNode(pipelineEntity.getYaml());
    } else {
      /*
      For schema validations, we don't want input set validators to be appended. For example, if some timeout field in
      the pipeline is <+input>.allowedValues(12h, 1d), and the runtime input gives a value 12h, the value for this field
      in pipelineJsonNode will be 12h.allowedValues(12h, 1d) for validation during execution. However, this value will
      give an error in schema validation. That's why we need a value that doesn't have this validator appended.
       */
      pipelineJsonNodeForSchemaValidations =
          MergeHelper.mergeRuntimeInputValuesIntoOriginalJsonNode(YamlUtils.readAsJsonNode(pipelineEntity.getYaml()),
              Collections.singletonList(mergedRuntimeInputJsonNode), false);
    }
    pipelineJsonNodeForSchemaValidations = InputSetSanitizer.trimValues(pipelineJsonNodeForSchemaValidations);
    return pipelineJsonNodeForSchemaValidations;
  }

  @VisibleForTesting
  TemplateMergeResponseDTO getPipelineYamlAndValidateStaticallyReferredEntities(
      JsonNode mergedRuntimeInputJsonNode, PipelineEntity pipelineEntity) {
    JsonNode pipelineJsonNode;

    long start = System.currentTimeMillis();
    if (EmptyPredicate.isEmpty(mergedRuntimeInputJsonNode)) {
      pipelineJsonNode = YamlUtils.readAsJsonNode(pipelineEntity.getYaml());
    } else {
      JsonNode pipelineEntityJsonNode = YamlUtils.readAsJsonNode(pipelineEntity.getYaml());
      pipelineJsonNode = MergeHelper.mergeRuntimeInputValuesIntoOriginalJsonNode(
          pipelineEntityJsonNode, mergedRuntimeInputJsonNode, true, true);
    }
    return getPipelineYamlAndValidateStaticallyReferredEntities(pipelineJsonNode, pipelineEntity, start);
  }
  public boolean shouldRunRbacValidationBeforeExecutingInlinePipelines(PipelineEntity pipelineEntity) {
    String shouldRunRbacValidationBeforeExecutingInlinePipelines = "true";
    try {
      shouldRunRbacValidationBeforeExecutingInlinePipelines =
          NGRestUtils
              .getResponse(settingsClient.getSetting(
                  RUN_RBAC_VALIDATION_BEFORE_EXECUTING_INLINE_PIPELINES, pipelineEntity.getAccountId(), null, null))
              .getValue();
    } catch (Exception ex) {
      log.error("Failed to fetch setting value for {}", RUN_RBAC_VALIDATION_BEFORE_EXECUTING_INLINE_PIPELINES, ex);
    }
    return Boolean.TRUE.equals(Boolean.valueOf(shouldRunRbacValidationBeforeExecutingInlinePipelines));
  }

  TemplateMergeResponseDTO getPipelineYamlAndValidateStaticallyReferredEntities(
      JsonNode pipelineJsonNode, PipelineEntity pipelineEntity, long start) {
    pipelineJsonNode = InputSetSanitizer.trimValues(pipelineJsonNode);

    String pipelineYaml = YamlUtils.writeYamlString(pipelineJsonNode);
    log.info("[PMS_EXECUTE] Pipeline input set merge total time took {}ms", System.currentTimeMillis() - start);

    String pipelineYamlWithTemplateRef = pipelineYaml;
    String processedYamlVersion = pipelineEntity.getHarnessVersion();
    if (Boolean.TRUE.equals(TemplateRefHelper.hasTemplateRef(pipelineJsonNode))) {
      TemplateMergeResponseDTO templateMergeResponseDTO =
          pipelineTemplateHelper.resolveTemplateRefsInPipelineAndAppendInputSetValidators(pipelineEntity.getAccountId(),
              pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineYaml, true,
              featureFlagService.isEnabled(pipelineEntity.getAccountId(), FeatureName.OPA_PIPELINE_GOVERNANCE),
              BOOLEAN_FALSE_VALUE, pipelineEntity.getHarnessVersion());
      pipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
      pipelineYamlWithTemplateRef =
          EmptyPredicate.isEmpty(templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef())
          ? pipelineYaml
          : templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef();
      processedYamlVersion = templateMergeResponseDTO.getProcessedYamlVersion();
    }
    if ((pipelineEntity.getStoreType() == null || pipelineEntity.getStoreType() == StoreType.INLINE)
        && shouldRunRbacValidationBeforeExecutingInlinePipelines(pipelineEntity)) {
      // For REMOTE Pipelines, entity setup usage framework cannot be relied upon. That is because the setup usages can
      // be outdated wrt the YAML we find on Git during execution. This means the fail fast approach that we have for
      // RBAC checks can't be provided for remote pipelines
      pipelineRbacServiceImpl.extractAndValidateStaticallyReferredEntities(pipelineEntity.getAccountId(),
          pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(),
          pipelineJsonNode);
    }
    return TemplateMergeResponseDTO.builder()
        .mergedPipelineYaml(pipelineYaml)
        .mergedPipelineYamlWithTemplateRef(pipelineYamlWithTemplateRef)
        .processedYamlVersion(processedYamlVersion)
        .build();
  }

  private PlanExecutionMetadata.Builder obtainPlanExecutionMetadata(String mergedRuntimeInputYaml, String executionId,
      StagesExecutionInfo stagesExecutionInfo, String originalExecutionId,
      RetryExecutionParameters retryExecutionParameters, boolean notifyOnlyUser, String notes,
      PipelineEntity pipelineEntity) {
    long start = System.currentTimeMillis();
    String version = pipelineEntity.getHarnessVersion();
    boolean isRetry = retryExecutionParameters.isRetry();
    String pipelineYaml = stagesExecutionInfo.getPipelineYamlToRun();
    PlanExecutionMetadata.Builder planExecutionMetadataBuilder =
        PlanExecutionMetadata.builder()
            .planExecutionId(executionId)
            .inputSetYaml(mergedRuntimeInputYaml)
            .yaml(pipelineYaml)
            .pipelineYaml(pipelineEntity.getYaml())
            .stagesExecutionMetadata(stagesExecutionInfo.toStagesExecutionMetadata())
            .allowStagesExecution(stagesExecutionInfo.isAllowStagesExecution())
            .notifyOnlyUser(notifyOnlyUser)
            .notes(notes);
    String currentProcessedYaml;
    try {
      switch (version) {
        case HarnessYamlVersion.V1:
          currentProcessedYaml = YamlUtils.injectUuidWithType(pipelineYaml, YAMLFieldNameConstants.PIPELINE);
          break;
        case HarnessYamlVersion.V0:
          currentProcessedYaml = YamlUtils.injectUuid(pipelineYaml);
          break;
        default:
          throw new IllegalStateException("version not supported");
      }

    } catch (IOException e) {
      log.error("Unable to inject Uuids into pipeline Yaml. Yaml:\n" + pipelineYaml, e);
      throw new InvalidYamlException("Unable to inject Uuids into pipeline Yaml", e);
    }
    if (isRetry) {
      try {
        currentProcessedYaml =
            retryExecutionHelper.retryProcessedYaml(retryExecutionParameters.getPreviousProcessedYaml(),
                currentProcessedYaml, retryExecutionParameters.getRetryStagesIdentifier(),
                retryExecutionParameters.getIdentifierOfSkipStages(), version);
      } catch (IOException e) {
        log.error("Unable to get processed yaml. Previous Processed yaml:\n"
                + retryExecutionParameters.getPreviousProcessedYaml(),
            e);
        throw new InvalidYamlException("Unable to get processed yaml for retry.", e);
      }
    }
    planExecutionMetadataBuilder.processedYaml(currentProcessedYaml);

    if (isNotEmpty(originalExecutionId)) {
      planExecutionMetadataBuilder = populateTriggerDataForRerun(originalExecutionId, planExecutionMetadataBuilder);
    }
    log.info("[PMS_EXECUTE] PlanExecution Metadata creation took total time {}ms", System.currentTimeMillis() - start);
    return planExecutionMetadataBuilder;
  }

  private PlanExecutionMetadata.Builder populateTriggerDataForRerun(
      String originalExecutionId, PlanExecutionMetadata.Builder planExecutionMetadataBuilder) {
    Optional<PlanExecutionMetadata> prevMetadataOptional =
        planExecutionMetadataService.findByPlanExecutionId(originalExecutionId);

    if (prevMetadataOptional.isPresent()) {
      PlanExecutionMetadata prevMetadata = prevMetadataOptional.get();
      return planExecutionMetadataBuilder.triggerPayload(prevMetadata.getTriggerPayload())
          .triggerJsonPayload(prevMetadata.getTriggerJsonPayload());
    } else {
      log.warn("No prev plan execution metadata found for plan execution id [" + originalExecutionId + "]");
    }
    return planExecutionMetadataBuilder;
  }

  public PlanExecution startExecution(String accountId, String orgIdentifier, String projectIdentifier,
      ExecutionMetadata executionMetadata, PlanExecutionMetadata planExecutionMetadata, boolean isRetry,
      List<String> identifierOfSkipStages, String previousExecutionId, List<String> retryStagesIdentifier,
      boolean runAllStages) {
    long startTs = System.currentTimeMillis();
    try (AutoLogContext ignore =
             PlanCreatorUtils.autoLogContext(executionMetadata, accountId, orgIdentifier, projectIdentifier)) {
      PlanCreationBlobResponse resp;
      Plan plan;
      try {
        String version = executionMetadata.getProcessedYamlVersion();
        resp = planCreatorMergeService.createPlanVersioned(
            accountId, orgIdentifier, projectIdentifier, version, executionMetadata, planExecutionMetadata);
        plan = PlanExecutionUtils.extractPlan(resp);
      } catch (IOException e) {
        log.error(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(e)), e);
        throw new InvalidYamlException(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(e)), e);
      }
      ImmutableMap<String, String> abstractions = ImmutableMap.<String, String>builder()
                                                      .put(SetupAbstractionKeys.accountId, accountId)
                                                      .put(SetupAbstractionKeys.orgIdentifier, orgIdentifier)
                                                      .put(SetupAbstractionKeys.projectIdentifier, projectIdentifier)
                                                      .build();
      long endTs = System.currentTimeMillis();
      try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(abstractions)) {
        metricService.recordMetric(PLAN_CREATION_TIME_METRIC_NAME, endTs - startTs);
      }
      log.info("[PMS_PLAN] Time taken to complete plan: {}ms ", endTs - startTs);

      List<Node> planNodesList = plan.getPlanNodes();
      // Fetches the modules based on the steps in the pipeline using the stepType in the planeNodes
      Set<String> modules = nodeTypeLookupService.modulesThatSupportStepTypes(planNodesList);
      // Setting all the modules based on the steps in the pipeline
      if (!modules.isEmpty()) {
        plan = plan.withStepModules(modules);
      }

      ExecutionMode executionMode = executionMetadata.getExecutionMode();
      List<String> rollbackStageIds = Collections.emptyList();
      if (planExecutionMetadata.getStagesExecutionMetadata() != null) {
        rollbackStageIds = planExecutionMetadata.getStagesExecutionMetadata().getStageIdentifiers();
      }
      plan = transformPlan(plan, isRetry, identifierOfSkipStages, previousExecutionId, retryStagesIdentifier,
          executionMode, rollbackStageIds, runAllStages);

      // Currently not adding transaction here to validate if there are errors after plan creation
      ExecutionMetadata finalExecutionMetadata =
          executionMetadata.toBuilder()
              .setRunSequence(pipelineMetadataService.incrementRunSequence(
                  accountId, orgIdentifier, projectIdentifier, executionMetadata.getPipelineIdentifier()))
              .build();
      try {
        return orchestrationService.startExecution(plan, abstractions, finalExecutionMetadata, planExecutionMetadata);
      } catch (Exception e) {
        log.warn("Add transaction for increment and startExecution as execution failed after plan creation");
        throw new InternalServerErrorException(e.getMessage());
      }
    }
  }

  Plan transformPlan(Plan plan, boolean isRetry, List<String> identifierOfSkipStages, String previousExecutionId,
      List<String> retryStagesIdentifier, ExecutionMode executionMode, List<String> rollbackStageIds,
      boolean runAllStages) {
    if (isRetry) {
      return retryExecutionHelper.transformPlan(
          plan, identifierOfSkipStages, previousExecutionId, retryStagesIdentifier, runAllStages);
    }
    if (isRollbackMode(executionMode)) {
      return rollbackModeExecutionHelper.transformPlanForRollbackMode(
          plan, previousExecutionId, plan.getPreservedNodesInRollbackMode(), executionMode, rollbackStageIds);
    }
    return plan;
  }

  public PlanExecution startExecutionV2(String accountId, String orgIdentifier, String projectIdentifier,
      ExecutionMetadata executionMetadata, PlanExecutionMetadata planExecutionMetadata, boolean isRetry,
      List<String> identifierOfSkipStages, String previousExecutionId, List<String> retryStagesIdentifier,
      boolean runAllStages) {
    long startTs = System.currentTimeMillis();
    String planCreationId = generateUuid();
    try {
      planCreatorMergeService.createPlanV2(
          accountId, orgIdentifier, projectIdentifier, planCreationId, executionMetadata, planExecutionMetadata);
    } catch (IOException e) {
      log.error(
          "Could not extract Pipeline Field from the processed yaml:\n" + planExecutionMetadata.getProcessedYaml(), e);
      throw new InvalidYamlException("Could not extract Pipeline Field from the yaml.", e);
    }

    ImmutableMap<String, String> abstractions = ImmutableMap.<String, String>builder()
                                                    .put(SetupAbstractionKeys.accountId, accountId)
                                                    .put(SetupAbstractionKeys.orgIdentifier, orgIdentifier)
                                                    .put(SetupAbstractionKeys.projectIdentifier, projectIdentifier)
                                                    .build();
    while (!planService.fetchPlanOptional(planCreationId).isPresent()) {
      Morpheus.sleep(Duration.ofMillis(100));
    }

    long endTs = System.currentTimeMillis();
    log.info("Time taken to complete plan: {}", endTs - startTs);
    Plan plan = planService.fetchPlan(planCreationId);
    if (!plan.isValid()) {
      PmsExceptionUtils.checkAndThrowPlanCreatorException(ImmutableList.of(plan.getErrorResponse()));
      return PlanExecution.builder().build();
    }
    if (isRetry) {
      retryExecutionHelper.transformPlan(
          plan, identifierOfSkipStages, previousExecutionId, retryStagesIdentifier, runAllStages);
      return orchestrationService.startExecutionV2(
          planCreationId, abstractions, executionMetadata, planExecutionMetadata);
    }
    return orchestrationService.startExecutionV2(
        planCreationId, abstractions, executionMetadata, planExecutionMetadata);
  }

  public RetryExecutionInfo buildRetryInfo(boolean isRetry, String originalExecutionId) {
    if (!isRetry || isEmpty(originalExecutionId)) {
      return RetryExecutionInfo.newBuilder().setIsRetry(false).build();
    }
    String rootRetryExecutionId = pmsExecutionSummaryRespository.fetchRootRetryExecutionId(originalExecutionId);
    return RetryExecutionInfo.newBuilder()
        .setIsRetry(true)
        .setParentRetryId(originalExecutionId)
        .setRootExecutionId(rootRetryExecutionId)
        .build();
  }

  public PipelineExecutionDetailDTO getResponseDTO(String stageNodeId, String stageNodeExecutionId,
      String childStageNodeId, Boolean renderFullBottomGraph, PipelineExecutionSummaryEntity executionSummaryEntity,
      EntityGitDetails entityGitDetails) {
    String accountId = executionSummaryEntity.getAccountId();
    String orgId = executionSummaryEntity.getOrgIdentifier();
    String projectId = executionSummaryEntity.getProjectIdentifier();
    String planExecutionId = executionSummaryEntity.getPlanExecutionId();
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of("PIPELINE", executionSummaryEntity.getPipelineIdentifier()), PipelineRbacPermissions.PIPELINE_VIEW);

    ChildExecutionDetailDTO rollbackGraph = rollbackGraphGenerator.checkAndBuildRollbackGraph(accountId, orgId,
        projectId, executionSummaryEntity, entityGitDetails, childStageNodeId, stageNodeExecutionId, stageNodeId);

    // If the stage is of type Pipeline Stage, then return the child graph along with top graph of parent pipeline
    if (pipelineStageHelper.validateChildGraphToGenerate(executionSummaryEntity.getLayoutNodeMap(), stageNodeId)) {
      NodeExecution nodeExecution = getNodeExecution(stageNodeId, planExecutionId);
      if (nodeExecution != null && isNotEmpty(nodeExecution.getExecutableResponses())) {
        // TODO: check with @sahilHindwani whether this update is required or not.
        pmsExecutionService.sendGraphUpdateEvent(executionSummaryEntity);
        ChildExecutionDetailDTO childGraph = pipelineStageHelper.getChildGraph(
            accountId, childStageNodeId, entityGitDetails, nodeExecution, stageNodeExecutionId);
        return PipelineExecutionDetailDTO.builder()
            .pipelineExecutionSummary(PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, entityGitDetails))
            .childGraph(childGraph)
            .rollbackGraph(rollbackGraph)
            .build();
      }
    }

    // if the rollback graph has its executionGraph field filled, then we don't need to add execution graph to parent
    // response dto, because UI will only use the execution graph in the rollback graph
    boolean rollbackGraphWithExecutionGraph = rollbackGraph != null && rollbackGraph.getExecutionGraph() != null;
    if (rollbackGraphWithExecutionGraph
        || EmptyPredicate.isEmpty(stageNodeId) && (renderFullBottomGraph == null || !renderFullBottomGraph)) {
      pmsExecutionService.sendGraphUpdateEvent(executionSummaryEntity);
      return PipelineExecutionDetailDTO.builder()
          .pipelineExecutionSummary(PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, entityGitDetails))
          .rollbackGraph(rollbackGraph)
          .build();
    }

    return PipelineExecutionDetailDTO.builder()
        .pipelineExecutionSummary(PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, entityGitDetails))
        .executionGraph(ExecutionGraphMapper.toExecutionGraph(
            pmsExecutionService.getOrchestrationGraph(stageNodeId, planExecutionId, stageNodeExecutionId),
            executionSummaryEntity))
        .rollbackGraph(rollbackGraph)
        .build();
  }

  private NodeExecution getNodeExecution(String stageNodeId, String planExecutionId) {
    try {
      return nodeExecutionService.getByPlanNodeUuid(stageNodeId, planExecutionId);
    } catch (InvalidRequestException ex) {
      log.info("NodeExecution is null for plan node: {} ", stageNodeId);
    }
    return null;
  }

  public ProcessStageExecutionInfoResult processStageExecutionInfo(List<String> stagesToRun,
      boolean allowedStageExecution, PipelineEntity pipelineEntity, String pipelineYaml,
      String pipelineYamlWithTemplateRef, Map<String, String> expressionValues) {
    StagesExecutionInfo stagesExecutionInfo;
    if (isNotEmpty(stagesToRun)) {
      if (!allowedStageExecution) {
        throw new InvalidRequestException(
            String.format("Stage executions are not allowed for pipeline [%s]", pipelineEntity.getIdentifier()));
      }

      StagesExecutionHelper.throwErrorIfAllStagesAreDeleted(pipelineYaml, stagesToRun);
      pipelineYaml = StagesExpressionExtractor.replaceExpressionsWithJsonNode(pipelineYaml, expressionValues);
      stagesExecutionInfo = StagesExecutionHelper.getStagesExecutionInfo(pipelineYaml, stagesToRun, expressionValues);
      pipelineYamlWithTemplateRef =
          InputSetMergeHelper.removeNonRequiredStages(pipelineYamlWithTemplateRef, stagesToRun);
    } else {
      stagesExecutionInfo = StagesExecutionInfo.builder()
                                .isStagesExecution(false)
                                .pipelineYamlToRun(pipelineYaml)
                                .allowStagesExecution(allowedStageExecution)
                                .build();
    }
    return ProcessStageExecutionInfoResult.builder()
        .stagesExecutionInfo(stagesExecutionInfo)
        .filteredPipelineYamlWithTemplateRef(pipelineYamlWithTemplateRef)
        .build();
  }

  public void updateSettingsInExecutionMetadataBuilder(
      PipelineEntity pipelineEntity, ExecutionMetadata.Builder builder) {
    try {
      Call<ResponseDTO<List<SettingResponseDTO>>> responseDTOCall =
          settingsClient.listSettings(pipelineEntity.getAccountIdentifier(), pipelineEntity.getOrgIdentifier(),
              pipelineEntity.getProjectIdentifier(), SettingCategory.PMS, PMS_EXECUTION_SETTINGS_GROUP_IDENTIFIER);

      List<SettingResponseDTO> response = NGRestUtils.getResponse(responseDTOCall);

      for (SettingResponseDTO settingDto : response) {
        SettingDTO setting = settingDto.getSetting();
        builder.putSettingToValueMap(setting.getIdentifier(), setting.getValue());
      }

      // TODO(Remove this specific setting call once the settings-list API returns all scope settings and not only
      // project)
      if (!builder.getSettingToValueMapMap().containsKey(
              NGPipelineSettingsConstant.ENABLE_NODE_EXECUTION_AUDIT_EVENTS.getName())) {
        Call<ResponseDTO<SettingValueResponseDTO>> auditSettingResponseDTO =
            settingsClient.getSetting(NGPipelineSettingsConstant.ENABLE_NODE_EXECUTION_AUDIT_EVENTS.getName(),
                pipelineEntity.getAccountIdentifier(), null, null);
        SettingValueResponseDTO auditSettingResponse = NGRestUtils.getResponse(auditSettingResponseDTO);
        builder.putSettingToValueMap(
            NGPipelineSettingsConstant.ENABLE_NODE_EXECUTION_AUDIT_EVENTS.getName(), auditSettingResponse.getValue());
      }
    } catch (Exception e) {
      log.error("Error in fetching pipeline Settings due to {}", e.getMessage());
    }
  }

  public void updateFeatureFlagsInExecutionMetadataBuilder(
      String accountIdentifier, List<FeatureName> featureNames, ExecutionMetadata.Builder builder) {
    for (FeatureName featureName : featureNames) {
      builder.putFeatureFlagToValueMap(
          featureName.name(), featureFlagService.isEnabled(accountIdentifier, featureName));
    }
  }
}
