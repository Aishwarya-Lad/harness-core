/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.cdng.creator.plan.envGroup.EnvGroupPlanCreatorHelper;
import io.harness.cdng.creator.plan.infrastructure.InfrastructurePmsPlanCreator;
import io.harness.cdng.creator.plan.service.ServiceAllInOnePlanCreatorUtils;
import io.harness.cdng.creator.plan.service.ServicePlanCreatorHelper;
import io.harness.cdng.creator.plan.stage.service.DeploymentStagePlanCreationInfoService;
import io.harness.cdng.envGroup.yaml.EnvGroupPlanCreatorConfig;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.helper.EnvironmentInfraFilterUtils;
import io.harness.cdng.environment.helper.EnvironmentPlanCreatorHelper;
import io.harness.cdng.environment.helper.EnvironmentsPlanCreatorHelper;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.environment.yaml.ServiceOverrideInputsYaml;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.pipeline.beans.DeploymentStageStepParameters;
import io.harness.cdng.pipeline.beans.MultiDeploymentStepParameters;
import io.harness.cdng.pipeline.steps.CdStepParametersUtils;
import io.harness.cdng.pipeline.steps.DeploymentStageStep;
import io.harness.cdng.pipeline.steps.MultiDeploymentSpawnerUtils;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceUseFromStageV2;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.beans.ServicesMetadata;
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.cdng.service.steps.helpers.beans.ServiceStepV3Parameters;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.NGFreezeException;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.helpers.FreezeRBACHelper;
import io.harness.freeze.service.FreezeEvaluateService;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.telemetry.entity.SvcEnvTelemetryInfo;
import io.harness.plancreator.stages.AbstractStagePlanCreator;
import io.harness.plancreator.steps.GenericStepPMSPlanCreator;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StageElementParameters.StageElementParametersBuilder;
import io.harness.plancreator.strategy.StrategyType;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rbac.CDNGRbacUtility;
import io.harness.serializer.KryoSerializer;
import io.harness.strategy.StrategyValidationUtils;
import io.harness.telemetry.helpers.SvcEnvInstrumentationHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.when.utils.RunInfoUtils;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Stage plan graph V1 -
 *  Stage
 *      spec (1 children, serviceConfig, next = infra)
 *          serviceConfig (1 children, serviceNode)
 *              service (next = serviceDefinitionNode)
 *              serviceDefinition (1 children, env)
 *                Environment (next = spec node)
 *                spec
 *                  artifacts
 *                  manifests
 *          infrastructureSection(UI visible)
 *            infraDefinition
 *              spec
 *          execution
 * Stage plan graph V2 -
 *  Stage
 *      spec (1 children, service)
 *          serviceSection (1 children, service, next = infra) [Done to keep previous plan creators in sync with v2]
 *              service (next = serviceDef)
 *              serviceDefinition (1 children, env)
 *                Environment (next = spec node)
 *                spec
 *                  artifacts
 *                  manifests
 *          infrastructureSection(UI visible)/Gitops(UI visible)
 *            infraDefinition
 *              spec
 *          execution
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(CDC)
@Slf4j
public class DeploymentStagePMSPlanCreatorV2 extends AbstractStagePlanCreator<DeploymentStageNode> {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private EnvironmentService environmentService;
  @Inject private NGFeatureFlagHelperService featureFlagHelperService;
  @Inject private InfrastructureEntityService infrastructure;
  @Inject private ServiceOverrideService serviceOverrideService;
  @Inject private EnvGroupPlanCreatorHelper envGroupPlanCreatorHelper;
  @Inject private EnvironmentsPlanCreatorHelper environmentsPlanCreatorHelper;
  @Inject private ServicePlanCreatorHelper servicePlanCreatorHelper;
  @Inject private FreezeEvaluateService freezeEvaluateService;
  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject private StagePlanCreatorHelper stagePlanCreatorHelper;
  @Inject private DeploymentStagePlanCreationInfoService deploymentStagePlanCreationInfoService;
  @Inject @Named("deployment-stage-plan-creation-info-executor") private ExecutorService executorService;
  @Inject private SvcEnvInstrumentationHelper instrumentationHelper;

  @Override
  public Set<String> getSupportedStageTypes() {
    return Collections.singleton("Deployment");
  }

  @Override
  public StepType getStepType(DeploymentStageNode stageElementConfig) {
    return DeploymentStageStep.STEP_TYPE;
  }

  @Override
  public SpecParameters getSpecParameters(String childNodeId, PlanCreationContext ctx, DeploymentStageNode stageNode) {
    return DeploymentStageStepParameters.getStepParameters(childNodeId);
  }

  @Override
  public Class<DeploymentStageNode> getFieldClass() {
    return DeploymentStageNode.class;
  }

  @Override
  public String getExecutionInputTemplateAndModifyYamlField(YamlField yamlField) {
    return RuntimeInputFormHelper.createExecutionInputFormAndUpdateYamlFieldForStage(yamlField);
  }

  @SneakyThrows
  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, DeploymentStageNode stageNode, List<String> childrenNodeIds) {
    if (stageNode.getStrategy() != null && MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(stageNode)) {
      throw new InvalidRequestException(
          "Looping Strategy and Multi Service/Environment configurations are not supported together in a single stage. Please use any one of these");
    }
    stageNode.setIdentifier(getIdentifierWithExpression(ctx, stageNode, stageNode.getIdentifier()));
    stageNode.setName(getIdentifierWithExpression(ctx, stageNode, stageNode.getName()));
    StageElementParametersBuilder stageParameters = CdStepParametersUtils.getStageParameters(stageNode);
    YamlField specField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));
    stageParameters.specConfig(getSpecParameters(specField.getNode().getUuid(), ctx, stageNode));

    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    if (!MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(stageNode)) {
      adviserObtainments = getAdviserObtainmentFromMetaData(ctx.getCurrentField(), ctx.getDependency());
    }

    // We need to swap the ids if strategy is present
    PlanNodeBuilder planNodeBuilder =
        PlanNode.builder()
            .uuid(getFinalPlanNodeId(ctx, stageNode))
            .name(stageNode.getName())
            .identifier(stageNode.getIdentifier())
            .group(StepOutcomeGroup.STAGE.name())
            .stepParameters(stageParameters.build())
            .stepType(getStepType(stageNode))
            .skipCondition(SkipInfoUtils.getSkipCondition(stageNode.getSkipCondition()))
            .whenCondition(RunInfoUtils.getRunConditionForStage(stageNode.getWhen(), ctx.getExecutionMode()))
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                    .build())
            .adviserObtainments(adviserObtainments);

    if (!EmptyPredicate.isEmpty(ctx.getExecutionInputTemplate())) {
      planNodeBuilder.executionInputTemplate(ctx.getExecutionInputTemplate());
    }
    return planNodeBuilder.build();
  }

  public String getIdentifierWithExpression(PlanCreationContext ctx, DeploymentStageNode node, String identifier) {
    if (MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(node)) {
      return identifier + StrategyValidationUtils.STRATEGY_IDENTIFIER_POSTFIX;
    }
    return StrategyUtils.getIdentifierWithExpression(ctx, identifier);
  }
  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, DeploymentStageNode stageNode) {
    failIfProjectIsFrozen(ctx);

    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    try {
      // Validate Stage Failure strategy.
      validateFailureStrategy(stageNode);

      Map<String, ByteString> metadataMap = new HashMap<>();

      YamlField specField =
          Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));

      YamlField executionField = specField.getNode().getField(YAMLFieldNameConstants.EXECUTION);
      if (executionField == null) {
        throw new InvalidRequestException("Execution section cannot be absent in deploy stage");
      }

      final boolean isProjectScopedResourceConstraintQueue =
          stagePlanCreatorHelper.isProjectScopedResourceConstraintQueueByFFOrSetting(ctx);
      if (v2Flow(stageNode)) {
        if (isGitopsEnabled(stageNode.getDeploymentStageConfig())) {
          // GitOps flow doesn't fork on environments, so handling it in this function.
          return buildPlanCreationResponse(ctx, planCreationResponseMap, stageNode, specField, executionField);
        } else {
          List<AdviserObtainment> adviserObtainments =
              stagePlanCreatorHelper.addResourceConstraintDependencyWithWhenCondition(
                  planCreationResponseMap, specField, ctx, isProjectScopedResourceConstraintQueue);
          boolean allowDifferentInfraForEnvPropagation = featureFlagHelperService.isEnabled(
              ctx.getAccountIdentifier(), FeatureName.CDS_SUPPORT_DIFFERENT_INFRA_DURING_ENV_PROPAGATION);
          String infraNodeId = addInfrastructureNode(
              planCreationResponseMap, stageNode, adviserObtainments, specField, allowDifferentInfraForEnvPropagation);
          Optional<String> provisionerIdOptional =
              addProvisionerNodeIfNeeded(specField, planCreationResponseMap, stageNode, infraNodeId);
          String serviceNextNodeId = provisionerIdOptional.orElse(infraNodeId);
          String serviceNodeId = addServiceNode(specField, planCreationResponseMap, stageNode, serviceNextNodeId, ctx);
          saveSingleServiceEnvDeploymentStagePlanCreationSummary(
              planCreationResponseMap.get(serviceNodeId), ctx, stageNode);
          addSpecNode(planCreationResponseMap, specField, serviceNodeId);
        }
      } else {
        final YamlField serviceField = servicePlanCreatorHelper.getResolvedServiceField(specField);
        PipelineInfrastructure pipelineInfrastructure = stageNode.getDeploymentStageConfig().getInfrastructure();
        addEnvAndInfraDependency(
            planCreationResponseMap, specField, pipelineInfrastructure, ctx, isProjectScopedResourceConstraintQueue);
        addServiceDependency(planCreationResponseMap, specField, stageNode, serviceField);
      }

      addCDExecutionDependencies(planCreationResponseMap, executionField, ctx, stageNode);
      addMultiDeploymentDependency(planCreationResponseMap, stageNode, ctx, specField);

      StrategyUtils.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, stageNode.getUuid(), stageNode.getName(),
          stageNode.getIdentifier(), planCreationResponseMap, metadataMap,
          StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, false), false, false);

      return planCreationResponseMap;
    } catch (IOException e) {
      throw new InvalidRequestException(
          "Invalid yaml for Deployment stage with identifier - " + stageNode.getIdentifier(), e);
    }
  }

  private LinkedHashMap<String, PlanCreationResponse> buildPlanCreationResponse(PlanCreationContext ctx,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, DeploymentStageNode stageNode,
      YamlField specField, YamlField executionField) throws IOException {
    String infraNodeId = addGitOpsClustersNode(ctx, planCreationResponseMap, stageNode, executionField);
    Optional<String> provisionerIdOptional =
        addProvisionerNodeIfNeeded(specField, planCreationResponseMap, stageNode, infraNodeId);
    String serviceNextNodeId = provisionerIdOptional.orElse(infraNodeId);
    String serviceNodeId =
        addServiceNodeForGitOps(specField, planCreationResponseMap, stageNode, serviceNextNodeId, ctx);
    addSpecNode(planCreationResponseMap, specField, serviceNodeId);

    addCDExecutionDependencies(planCreationResponseMap, executionField, ctx, stageNode);
    addMultiDeploymentDependencyForGitOps(planCreationResponseMap, stageNode, ctx, specField);

    StrategyUtils.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, stageNode.getUuid(), stageNode.getName(),
        stageNode.getIdentifier(), planCreationResponseMap, new HashMap<>(),
        StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, false), false, false);

    return planCreationResponseMap;
  }

  @Override
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext context, DeploymentStageNode config) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    YamlField yamlField = context.getCurrentField();
    if (MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(config)) {
      YamlField siblingField = yamlField.getNode().nextSiblingFromParentArray(
          yamlField.getName(), Arrays.asList(YAMLFieldNameConstants.STAGE, YAMLFieldNameConstants.PARALLEL));
      EdgeLayoutList edgeLayoutList;
      String planNodeId = MultiDeploymentSpawnerUtils.getUuidForMultiDeployment(config);
      String pipelineRollbackStageId = StrategyUtils.getPipelineRollbackStageId(context.getCurrentField());
      if (siblingField == null || Objects.equals(siblingField.getUuid(), pipelineRollbackStageId)) {
        edgeLayoutList = EdgeLayoutList.newBuilder().addCurrentNodeChildren(planNodeId).build();
      } else {
        edgeLayoutList = EdgeLayoutList.newBuilder()
                             .addNextIds(siblingField.getNode().getUuid())
                             .addCurrentNodeChildren(planNodeId)
                             .build();
      }
      stageYamlFieldMap.put(yamlField.getNode().getUuid(),
          GraphLayoutNode.newBuilder()
              .setNodeUUID(yamlField.getNode().getUuid())
              .setNodeType(StrategyType.MATRIX.name())
              .setName(yamlField.getNode().getName())
              .setNodeGroup(StepOutcomeGroup.STRATEGY.name())
              .setNodeIdentifier(yamlField.getNode().getIdentifier())
              .setEdgeLayoutList(edgeLayoutList)
              .build());
      stageYamlFieldMap.put(planNodeId,
          GraphLayoutNode.newBuilder()
              .setNodeUUID(planNodeId)
              .setNodeType(yamlField.getNode().getType())
              .setName(config.getName())
              .setNodeGroup(StepOutcomeGroup.STAGE.name())
              .setNodeIdentifier(config.getIdentifier())
              .setEdgeLayoutList(EdgeLayoutList.newBuilder().build())
              .build());
      return GraphLayoutResponse.builder().layoutNodes(stageYamlFieldMap).build();
    }
    YamlField stageYamlField = context.getCurrentField();
    if (StrategyUtils.isWrappedUnderStrategy(context.getCurrentField())) {
      stageYamlFieldMap = StrategyUtils.modifyStageLayoutNodeGraph(stageYamlField);
    }
    return GraphLayoutResponse.builder().layoutNodes(stageYamlFieldMap).build();
  }

  private boolean v2Flow(DeploymentStageNode stageNode) {
    final DeploymentStageConfig deploymentStageConfig = stageNode.getDeploymentStageConfig();

    boolean isServiceV2 = deploymentStageConfig.getService() != null
        && ParameterField.isNotNull(deploymentStageConfig.getService().getServiceRef());
    boolean serviceV2UseFromStage = deploymentStageConfig.getService() != null
        && deploymentStageConfig.getService().getUseFromStage() != null
        && EmptyPredicate.isNotEmpty(deploymentStageConfig.getService().getUseFromStage().getStage());
    boolean isServices = deploymentStageConfig.getServices() != null;
    return isServices || isServiceV2 || serviceV2UseFromStage;
  }

  private void addEnvAndInfraDependency(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      YamlField specField, PipelineInfrastructure pipelineInfrastructure, PlanCreationContext ctx,
      boolean isProjectScopedResourceConstraintQueue) throws IOException {
    final YamlField infraField = specField.getNode().getField(YamlTypes.PIPELINE_INFRASTRUCTURE);
    if (infraField != null) {
      // Adding infrastructure node
      PlanNode infraStepNode = InfrastructurePmsPlanCreator.getInfraStepPlanNode(
          pipelineInfrastructure.getInfrastructureDefinition().getSpec());
      planCreationResponseMap.put(
          infraStepNode.getUuid(), PlanCreationResponse.builder().node(infraStepNode.getUuid(), infraStepNode).build());
      String infraSectionNodeChildId = infraStepNode.getUuid();

      if (InfrastructurePmsPlanCreator.isProvisionerConfigured(pipelineInfrastructure)) {
        planCreationResponseMap.putAll(InfrastructurePmsPlanCreator.createPlanForProvisioner(
            pipelineInfrastructure, infraField, infraStepNode.getUuid(), kryoSerializer));
        infraSectionNodeChildId = InfrastructurePmsPlanCreator.getProvisionerNodeId(infraField);
      }

      YamlField infrastructureDefField =
          Preconditions.checkNotNull(infraField.getNode().getField(YamlTypes.INFRASTRUCTURE_DEF));
      PlanNode infraDefPlanNode =
          InfrastructurePmsPlanCreator.getInfraDefPlanNode(infrastructureDefField, infraSectionNodeChildId);
      planCreationResponseMap.put(infraDefPlanNode.getUuid(),
          PlanCreationResponse.builder().node(infraDefPlanNode.getUuid(), infraDefPlanNode).build());

      YamlNode infraNode = infraField.getNode();
      planCreationResponseMap.putAll(InfrastructurePmsPlanCreator.createPlanForInfraSectionV1(infraNode,
          infraDefPlanNode.getUuid(), pipelineInfrastructure, kryoSerializer, infraNode.getUuid(), ctx,
          isProjectScopedResourceConstraintQueue));
    }
  }

  private void addMultiDeploymentDependency(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      DeploymentStageNode stageNode, PlanCreationContext ctx, YamlField specField) throws IOException {
    DeploymentStageConfig stageConfig = stageNode.getDeploymentStageConfig();
    ServicesYaml finalServicesYaml = useFromStage(stageConfig.getServices())
        ? useServicesYamlFromStage(stageConfig, specField)
        : stageConfig.getServices();
    stageConfig.setServices(finalServicesYaml);
    sendTelemetryEventForSvcEnv(ctx, stageNode);

    MultiDeploymentSpawnerUtils.validateMultiServiceInfra(stageConfig);
    if (stageConfig.getServices() == null && stageConfig.getEnvironments() == null
        && stageConfig.getEnvironmentGroup() == null) {
      return;
    }

    String subType;

    // If filters are present
    if (EnvironmentInfraFilterUtils.areFiltersPresent(stageNode.deploymentStageConfig.getEnvironments())) {
      subType = MultiDeploymentSpawnerUtils.MULTI_SERVICE_ENV_DEPLOYMENT;
    } else {
      if (stageConfig.getEnvironments() == null) {
        subType = MultiDeploymentSpawnerUtils.MULTI_SERVICE_DEPLOYMENT;
      } else if (stageConfig.getServices() == null) {
        subType = MultiDeploymentSpawnerUtils.MULTI_ENV_DEPLOYMENT;
      } else {
        subType = MultiDeploymentSpawnerUtils.MULTI_SERVICE_ENV_DEPLOYMENT;
      }
    }
    List<ServiceOverrideInputsYaml> servicesOverrides = null;
    if (stageConfig.getEnvironment() != null
        && EmptyPredicate.isNotEmpty(stageConfig.getEnvironment().getServicesOverrides())) {
      servicesOverrides = stageConfig.getEnvironment().getServicesOverrides();
    }

    saveDeploymentStagePlanCreationSummaryForMultiServiceMultiEnvAsync(ctx, stageNode, specField);

    MultiDeploymentStepParameters stepParameters =
        MultiDeploymentStepParameters.builder()
            .strategyType(StrategyType.MATRIX)
            .childNodeId(MultiDeploymentSpawnerUtils.getUuidForMultiDeployment(stageNode))
            .environments(stageConfig.getEnvironments())
            .environmentGroup(stageConfig.getEnvironmentGroup())
            .services(stageConfig.getServices())
            .serviceYamlV2(getServiceYaml(specField, stageConfig))
            .subType(subType)
            .servicesOverrides(servicesOverrides)
            .deploymentType(stageConfig.getDeploymentType())
            .build();

    buildMultiDeploymentMetadata(planCreationResponseMap, stageNode, ctx, stepParameters);
  }

  private ServicesMetadata getServicesMetadataOfCurrentStage(DeploymentStageConfig stageConfig) {
    if (stageConfig.getServices() != null && stageConfig.getServices().getServicesMetadata() != null
        && ParameterField.isNotNull(stageConfig.getServices().getServicesMetadata().getParallel())) {
      return stageConfig.getServices().getServicesMetadata();
    }

    return ServicesMetadata.builder().parallel(ParameterField.createValueField(Boolean.TRUE)).build();
  }

  @VisibleForTesting
  ServicesYaml useServicesYamlFromStage(DeploymentStageConfig currentStageConfig, YamlField specField) {
    ServiceUseFromStageV2 useFromStage = currentStageConfig.getServices().getUseFromStage();
    final YamlField serviceField = specField.getNode().getField(YamlTypes.SERVICE_ENTITIES);
    String stage = useFromStage.getStage();
    if (isBlank(stage)) {
      throw new InvalidArgumentsException("Stage identifier is empty in useFromStage");
    }

    try {
      YamlField propagatedFromStageConfig = PlanCreatorUtils.getStageConfig(serviceField, stage);
      if (propagatedFromStageConfig == null) {
        throw new InvalidArgumentsException(
            "Stage with identifier [" + stage + "] given for service propagation does not exist.");
      }

      DeploymentStageNode stageElementConfigPropagatedFrom =
          YamlUtils.read(propagatedFromStageConfig.getNode().toString(), DeploymentStageNode.class);
      DeploymentStageConfig deploymentStagePropagatedFrom = stageElementConfigPropagatedFrom.getDeploymentStageConfig();
      if (deploymentStagePropagatedFrom != null) {
        if (deploymentStagePropagatedFrom.getServices() != null
            && useFromStage(deploymentStagePropagatedFrom.getServices())) {
          throw new InvalidArgumentsException("Invalid identifier [" + stage
              + "] given in useFromStage. Cannot reference a stage which also has useFromStage parameter");
        }

        if (deploymentStagePropagatedFrom.getServices() == null) {
          throw new InvalidRequestException(String.format(
              "Could not find multi service configuration in stage [%s], hence not possible to propagate service from that stage",
              stage));
        }

        if (deploymentStagePropagatedFrom.getDeploymentType() != null
            && isNotBlank(deploymentStagePropagatedFrom.getDeploymentType().getYamlName())
            && specField.getNode().getField("deploymentType").getNode() != null) {
          if (!deploymentStagePropagatedFrom.getDeploymentType().getYamlName().equals(
                  specField.getNode().getField("deploymentType").getNode().asText())) {
            throw new InvalidRequestException(String.format(
                "Deployment type: [%s] of stage: [%s] does not match with deployment type: [%s] of stage: [%s] from which service propagation is configured",
                specField.getNode().getField("deploymentType").getNode().asText(),
                specField.getNode().getParentNode().getIdentifier(),
                deploymentStagePropagatedFrom.getDeploymentType().getYamlName(), stage));
          }
        }

        ServicesYaml propagatedServices = deploymentStagePropagatedFrom.getServices();
        ServicesYaml currentStageServices = ServicesYaml.builder()
                                                .values(propagatedServices.getValues())
                                                .servicesMetadata(getServicesMetadataOfCurrentStage(currentStageConfig))
                                                .build();

        String svcYaml = YamlUtils.writeYamlString(currentStageServices);
        String injectUuidSvc = YamlUtils.injectUuid(svcYaml);
        return YamlUtils.read(injectUuidSvc, ServicesYaml.class);
      } else {
        throw new InvalidArgumentsException("Stage identifier given in useFromStage doesn't exist");
      }
    } catch (IOException ex) {
      throw new InvalidRequestException("Cannot parse stage: " + stage);
    }
  }

  private boolean useFromStage(ServicesYaml services) {
    return services != null && services.getUseFromStage() != null && isNotBlank(services.getUseFromStage().getStage());
  }

  private ServiceYamlV2 getServiceYaml(YamlField specField, DeploymentStageConfig stageConfig) {
    if (stageConfig.getService() != null && ServiceAllInOnePlanCreatorUtils.useFromStage(stageConfig.getService())) {
      return ServiceAllInOnePlanCreatorUtils.useServiceYamlFromStage(
          stageConfig.getService().getUseFromStage(), specField);
    }

    return stageConfig.getService();
  }

  private EnvironmentYamlV2 getEnvironmentYaml(
      YamlField specField, DeploymentStageConfig deploymentStageConfig, boolean allowDifferentInfraForEnvPropagation) {
    return ServiceAllInOnePlanCreatorUtils.useFromStage(deploymentStageConfig.getEnvironment())
        ? ServiceAllInOnePlanCreatorUtils.useEnvironmentYamlFromStage(
            deploymentStageConfig.getEnvironment(), specField, allowDifferentInfraForEnvPropagation)
        : deploymentStageConfig.getEnvironment();
  }

  /**
   * Method Handles creation of the multi-service deployments for GitOps Deployments.
   * Note: GitOps Flow doesn't fork stages for deploying to multiple environments or environmentgroup
   */
  private void addMultiDeploymentDependencyForGitOps(
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, DeploymentStageNode stageNode,
      PlanCreationContext ctx, YamlField specField) {
    DeploymentStageConfig stageConfig = stageNode.getDeploymentStageConfig();
    if (stageConfig.getServices() == null && stageConfig.getEnvironments() == null
        && stageConfig.getEnvironmentGroup() == null) {
      return;
    }

    final ServicesYaml finalServicesYaml = useFromStage(stageConfig.getServices())
        ? useServicesYamlFromStage(stageConfig, specField)
        : stageConfig.getServices();
    stageConfig.setServices(finalServicesYaml);

    String subType;
    if (stageConfig.getServices() != null) {
      subType = MultiDeploymentSpawnerUtils.MULTI_SERVICE_DEPLOYMENT;

      // Environment and EnvironmentGroup is not set for GitOps since we do not want to fork for gitops.
      // The GitOps cluster step takes care of deploying to multi infra
      MultiDeploymentStepParameters stepParameters =
          MultiDeploymentStepParameters.builder()
              .strategyType(StrategyType.MATRIX)
              .childNodeId(MultiDeploymentSpawnerUtils.getUuidForMultiDeployment(stageNode))
              .services(stageConfig.getServices())
              .subType(subType)
              .build();

      buildMultiDeploymentMetadata(planCreationResponseMap, stageNode, ctx, stepParameters);
    }
  }

  private void buildMultiDeploymentMetadata(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      DeploymentStageNode stageNode, PlanCreationContext ctx, MultiDeploymentStepParameters stepParameters) {
    MultiDeploymentMetadata metadata =
        MultiDeploymentMetadata.builder()
            .multiDeploymentNodeId(ctx.getCurrentField().getNode().getUuid())
            .multiDeploymentStepParameters(stepParameters)
            .strategyNodeIdentifier(stageNode.getIdentifier())
            .strategyNodeName(stageNode.getName())
            .adviserObtainments(StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, false))
            .build();

    PlanNode node = MultiDeploymentStepPlanCreator.createPlan(metadata);
    planCreationResponseMap.put(UUIDGenerator.generateUuid(), PlanCreationResponse.builder().planNode(node).build());
  }

  // This function adds the service dependency and returns the resolved service field
  private void addServiceDependency(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      YamlField specField, DeploymentStageNode stageNode, YamlField serviceField) throws IOException {
    // Adding service child by resolving the serviceField
    String serviceNodeUuid = serviceField.getNode().getUuid();

    // Adding Spec node
    planCreationResponseMap.put(specField.getNode().getUuid(),
        PlanCreationResponse.builder().dependencies(getDependenciesForSpecNode(specField, serviceNodeUuid)).build());

    // Adding dependency for service
    // Adding serviceField to yamlUpdates as its resolved value should be updated.
    planCreationResponseMap.put(serviceNodeUuid,
        PlanCreationResponse.builder()
            .dependencies(servicePlanCreatorHelper.getDependenciesForService(serviceField, stageNode))
            .yamlUpdates(YamlUpdates.newBuilder()
                             .putFqnToYaml(serviceField.getYamlPath(),
                                 YamlUtils.writeYamlString(serviceField).replace("---\n", ""))
                             .build())
            .build());
  }
  private String addServiceNode(YamlField specField,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, DeploymentStageNode stageNode,
      String nextNodeId, PlanCreationContext ctx) throws IOException {
    // Adding service child by resolving the serviceField
    ServiceDefinitionType deploymentType = stageNode.getDeploymentStageConfig().getDeploymentType();
    ServiceYamlV2 service;
    if (stageNode.getDeploymentStageConfig().getServices() != null) {
      service = MultiDeploymentSpawnerUtils.getServiceYamlV2Node();
    } else {
      service = stageNode.getDeploymentStageConfig().getService();
    }

    ParameterField<String> envGroupRef = ParameterField.ofNull();
    EnvironmentYamlV2 environment;
    if (stageNode.getDeploymentStageConfig().getEnvironmentGroup() != null) {
      environment = MultiDeploymentSpawnerUtils.getEnvironmentYamlV2Node();
      envGroupRef = stageNode.getDeploymentStageConfig().getEnvironmentGroup().getEnvGroupRef();
    } else if (stageNode.getDeploymentStageConfig().getEnvironments() != null) {
      environment = MultiDeploymentSpawnerUtils.getEnvironmentYamlV2Node();
    } else {
      environment = stageNode.getDeploymentStageConfig().getEnvironment();
    }
    String serviceNodeId = service.getUuid();
    boolean allowDifferentInfraForEnvPropagation = featureFlagHelperService.isEnabled(
        ctx.getAccountIdentifier(), FeatureName.CDS_SUPPORT_DIFFERENT_INFRA_DURING_ENV_PROPAGATION);
    planCreationResponseMap.putAll(
        ServiceAllInOnePlanCreatorUtils.addServiceNode(specField, kryoSerializer, service, environment, serviceNodeId,
            nextNodeId, deploymentType, envGroupRef, ctx, allowDifferentInfraForEnvPropagation));
    return serviceNodeId;
  }

  private String addServiceNodeForGitOps(YamlField specField,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, DeploymentStageNode stageNode,
      String nextNodeId, PlanCreationContext ctx) {
    // Adding service child by resolving the serviceField
    ServiceDefinitionType deploymentType = stageNode.getDeploymentStageConfig().getDeploymentType();
    ServiceYamlV2 service;
    if (stageNode.getDeploymentStageConfig().getServices() != null) {
      service = MultiDeploymentSpawnerUtils.getServiceYamlV2Node();
    } else {
      service = stageNode.getDeploymentStageConfig().getService();
    }

    EnvironmentGroupYaml environmentGroupYaml = stageNode.getDeploymentStageConfig().getEnvironmentGroup();
    String serviceNodeId = service.getUuid();
    if (stageNode.getDeploymentStageConfig().getEnvironmentGroup() != null) {
      planCreationResponseMap.putAll(ServiceAllInOnePlanCreatorUtils.addServiceNodeForGitOpsEnvGroup(
          specField, kryoSerializer, service, environmentGroupYaml, serviceNodeId, nextNodeId, deploymentType, ctx));
    } else if (stageNode.deploymentStageConfig.getEnvironments() != null) {
      planCreationResponseMap.putAll(
          ServiceAllInOnePlanCreatorUtils.addServiceNodeForGitOpsEnvironments(specField, kryoSerializer, service,
              stageNode.deploymentStageConfig.getEnvironments(), serviceNodeId, nextNodeId, deploymentType, ctx));
    } else if (stageNode.deploymentStageConfig.getEnvironment() != null) {
      boolean allowDifferentInfraForEnvPropagation = featureFlagHelperService.isEnabled(
          ctx.getAccountIdentifier(), FeatureName.CDS_SUPPORT_DIFFERENT_INFRA_DURING_ENV_PROPAGATION);
      planCreationResponseMap.putAll(ServiceAllInOnePlanCreatorUtils.addServiceNode(specField, kryoSerializer, service,
          stageNode.deploymentStageConfig.getEnvironment(), serviceNodeId, nextNodeId, deploymentType,
          ParameterField.ofNull(), ctx, allowDifferentInfraForEnvPropagation));
    }

    return serviceNodeId;
  }
  private String addInfrastructureNode(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      DeploymentStageNode stageNode, List<AdviserObtainment> adviserObtainments, YamlField specField,
      boolean allowDifferentInfraForEnvPropagation) throws IOException {
    EnvironmentYamlV2 environment;
    if (stageNode.getDeploymentStageConfig().getEnvironments() != null
        || stageNode.getDeploymentStageConfig().getEnvironmentGroup() != null) {
      environment = MultiDeploymentSpawnerUtils.getEnvironmentYamlV2Node();
    } else {
      environment = stageNode.getDeploymentStageConfig().getEnvironment();
    }

    final EnvironmentYamlV2 finalEnvironmentYamlV2 = ServiceAllInOnePlanCreatorUtils.useFromStage(environment)
        ? ServiceAllInOnePlanCreatorUtils.useEnvironmentYamlFromStage(
            environment, specField, allowDifferentInfraForEnvPropagation)
        : environment;

    PlanNode node = InfrastructurePmsPlanCreator.getInfraTaskExecutableStepV2PlanNode(finalEnvironmentYamlV2,
        adviserObtainments, stageNode.getDeploymentStageConfig().getDeploymentType(), stageNode.skipInstances);
    planCreationResponseMap.put(node.getUuid(), PlanCreationResponse.builder().planNode(node).build());
    return node.getUuid();
  }
  private Optional<String> addProvisionerNodeIfNeeded(YamlField specField,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, DeploymentStageNode stageNode,
      String infraNodeId) {
    if (stageNode.getDeploymentStageConfig().getEnvironment() == null
        || stageNode.getDeploymentStageConfig().getEnvironment().getProvisioner() == null) {
      return Optional.empty();
    }

    YamlField envField = specField.getNode().getField(YAMLFieldNameConstants.ENVIRONMENT);
    YamlField provisionerField = envField.getNode().getField(YAMLFieldNameConstants.PROVISIONER);
    YamlField provisionerStepsField = provisionerField.getNode().getField(YAMLFieldNameConstants.STEPS);

    Map<String, YamlField> stepsYamlFieldMap = new HashMap<>();
    stepsYamlFieldMap.put(provisionerStepsField.getNode().getUuid(), provisionerStepsField);
    planCreationResponseMap.put(provisionerStepsField.getNode().getUuid(),
        PlanCreationResponse.builder().dependencies(DependenciesUtils.toDependenciesProto(stepsYamlFieldMap)).build());

    PlanNode node = InfrastructurePmsPlanCreator.getProvisionerPlanNode(
        provisionerField, provisionerStepsField.getUuid(), infraNodeId, kryoSerializer);
    planCreationResponseMap.put(node.getUuid(), PlanCreationResponse.builder().planNode(node).build());
    return Optional.of(node.getUuid());
  }

  private String addGitOpsClustersNode(PlanCreationContext ctx,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, DeploymentStageNode stageNode,
      YamlField executionUuid) {
    PlanNode gitopsNode = null;
    final String postServiceStepUuid = "gitOpsClusters-" + UUIDGenerator.generateUuid();
    EnvironmentGroupYaml envGroupYaml = stageNode.getDeploymentStageConfig().getEnvironmentGroup();
    if (envGroupYaml != null) {
      EnvGroupPlanCreatorConfig config = envGroupPlanCreatorHelper.createEnvGroupPlanCreatorConfig(ctx, envGroupYaml);

      gitopsNode = InfrastructurePmsPlanCreator.createPlanForGitopsClusters(
          executionUuid, postServiceStepUuid, config, kryoSerializer);
    } else if (stageNode.getDeploymentStageConfig().getEnvironments() != null) {
      EnvironmentsPlanCreatorConfig environmentsPlanCreatorConfig =
          environmentsPlanCreatorHelper.createEnvironmentsPlanCreatorConfig(
              ctx, stageNode.getDeploymentStageConfig().getEnvironments());

      gitopsNode = InfrastructurePmsPlanCreator.createPlanForGitopsClusters(
          executionUuid, postServiceStepUuid, environmentsPlanCreatorConfig, kryoSerializer);
    } else if (stageNode.deploymentStageConfig.getEnvironment() != null) {
      String serviceRef;

      if (stageNode.getDeploymentStageConfig().getServices() != null) {
        serviceRef = MultiDeploymentSpawnerUtils.getServiceYamlV2Node().getServiceRef().getValue();
      } else {
        serviceRef = stageNode.getDeploymentStageConfig().getService().getServiceRef().getValue();
      }
      EnvironmentPlanCreatorConfig environmentPlanCreatorConfig =
          EnvironmentPlanCreatorHelper.getResolvedEnvRefs(ctx, stageNode.deploymentStageConfig.getEnvironment(), true,
              serviceRef, serviceOverrideService, environmentService, infrastructure);
      gitopsNode = InfrastructurePmsPlanCreator.createPlanForGitopsClusters(
          executionUuid, postServiceStepUuid, environmentPlanCreatorConfig, kryoSerializer);
    }

    if (gitopsNode == null) {
      log.error("Gitops node was not created, hence not adding it to plan creation");
      return null;
    }

    planCreationResponseMap.put(gitopsNode.getUuid(), PlanCreationResponse.builder().planNode(gitopsNode).build());
    return gitopsNode.getUuid();
  }

  private void addSpecNode(
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, YamlField specField, String nextNodeId) {
    // Adding Spec node
    planCreationResponseMap.put(specField.getNode().getUuid(),
        PlanCreationResponse.builder().dependencies(getDependenciesForSpecNode(specField, nextNodeId)).build());
  }

  public Dependencies getDependenciesForSpecNode(YamlField specField, String childNodeUuid) {
    Map<String, YamlField> specYamlFieldMap = new HashMap<>();
    String specNodeUuid = specField.getNode().getUuid();
    specYamlFieldMap.put(specNodeUuid, specField);

    Map<String, ByteString> specDependencyMap = new HashMap<>();
    specDependencyMap.put(
        YAMLFieldNameConstants.CHILD_NODE_OF_SPEC, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(childNodeUuid)));
    Dependency specDependency = Dependency.newBuilder().putAllMetadata(specDependencyMap).build();
    return DependenciesUtils.toDependenciesProto(specYamlFieldMap)
        .toBuilder()
        .putDependencyMetadata(specNodeUuid, specDependency)
        .build();
  }

  public void addCDExecutionDependencies(Map<String, PlanCreationResponse> planCreationResponseMap,
      YamlField executionField, PlanCreationContext ctx, DeploymentStageNode stageNode) {
    Map<String, YamlField> executionYamlFieldMap = new HashMap<>();
    executionYamlFieldMap.put(executionField.getNode().getUuid(), executionField);
    planCreationResponseMap.put(executionField.getNode().getUuid(),
        PlanCreationResponse.builder()
            .dependencies(
                DependenciesUtils.toDependenciesProto(executionYamlFieldMap)
                    .toBuilder()
                    .putDependencyMetadata(executionField.getNode().getUuid(),
                        Dependency.newBuilder()
                            .setParentInfo(generateParentInfo(ctx, stageNode, getFinalPlanNodeId(ctx, stageNode),
                                StrategyUtils.isWrappedUnderStrategy(ctx.getCurrentField())
                                    || MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(stageNode)))
                            .build())
                    .build())
            .build());
  }

  private void validateFailureStrategy(DeploymentStageNode stageNode) {
    // Failure strategy should be present.
    ParameterField<List<FailureStrategyConfig>> stageFailureStrategies = stageNode.getFailureStrategies();
    if (ParameterField.isNull(stageFailureStrategies) || isEmpty(stageFailureStrategies.getValue())) {
      throw new InvalidRequestException("There should be at least one failure strategy configured at stage level.");
    }

    // checking stageFailureStrategies is having one strategy with error type as AllErrors and along with that no
    // error type is involved
    if (!GenericStepPMSPlanCreator.containsOnlyAllErrorsInSomeConfig(stageFailureStrategies)) {
      throw new InvalidRequestException(
          "There should be a Failure strategy that contains one error type as AllErrors, with no other error type along with it in that Failure Strategy.");
    }
  }

  protected void failIfProjectIsFrozen(PlanCreationContext ctx) {
    List<FreezeSummaryResponseDTO> projectFreezeConfigs = null;
    try {
      String accountId = ctx.getAccountIdentifier();
      String orgId = ctx.getOrgIdentifier();
      String projectId = ctx.getProjectIdentifier();
      String pipelineId = ctx.getPipelineIdentifier();
      if (FreezeRBACHelper.checkIfUserHasFreezeOverrideAccess(featureFlagHelperService, accountId, orgId, projectId,
              accessControlClient, CDNGRbacUtility.getExecutionPrincipalInfo(ctx))) {
        return;
      }

      projectFreezeConfigs = freezeEvaluateService.getActiveFreezeEntities(accountId, orgId, projectId, pipelineId);
    } catch (Exception e) {
      log.error(
          "NG Freeze: Failure occurred when evaluating execution should fail due to freeze at the time of plan creation");
    }
    if (EmptyPredicate.isNotEmpty(projectFreezeConfigs)) {
      throw new NGFreezeException("Execution can't be performed because project is frozen");
    }
  }

  private boolean isGitopsEnabled(DeploymentStageConfig deploymentStageConfig) {
    return deploymentStageConfig.getGitOpsEnabled();
  }

  @Override
  protected String getFinalPlanNodeId(PlanCreationContext ctx, DeploymentStageNode stageNode) {
    String uuid = MultiDeploymentSpawnerUtils.getUuidForMultiDeployment(stageNode);
    return StrategyUtils.getSwappedPlanNodeId(ctx, uuid);
  }

  protected void saveSingleServiceEnvDeploymentStagePlanCreationSummary(
      PlanCreationResponse servicePlanCreationResponse, @NotNull PlanCreationContext ctx,
      @NotNull DeploymentStageNode stageNode) {
    // TODO: get names of ser/env/infra if possible
    DeploymentStageConfig stageConfig = stageNode.getDeploymentStageConfig();
    if (isNull(servicePlanCreationResponse) || isNull(servicePlanCreationResponse.getPlanNode())) {
      log.warn(
          "Plan node or stage config corresponding to service not found while saving deployment info at plan creation, returning");
      return;
    }
    if (MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(stageConfig)) {
      // since multi-service / env configured, info will be saved while adding multi dependency
      log.debug("Multi service and(or) environment deployment stage encountered, skipping saving info");
      return;
    }

    StepParameters stepParameters = servicePlanCreationResponse.getPlanNode().getStepParameters();
    if (isNull(stepParameters) || !(stepParameters instanceof ServiceStepV3Parameters)) {
      log.warn(
          "Step params for service node not of type ServiceStepV3Parameters while saving deployment info at plan creation, returning");
      return;
    }

    ServiceStepV3Parameters serviceStepV3Parameters = (ServiceStepV3Parameters) stepParameters;
    executorService.submit(() -> {
      try {
        deploymentStagePlanCreationInfoService.save(
            DeploymentStagePlanCreationInfo.builder()
                .planExecutionId(ctx.getExecutionUuid())
                .accountIdentifier(ctx.getAccountIdentifier())
                .orgIdentifier(ctx.getOrgIdentifier())
                .projectIdentifier(ctx.getProjectIdentifier())
                .pipelineIdentifier(ctx.getPipelineIdentifier())
                .stageType(DeploymentStageType.SINGLE_SERVICE_ENVIRONMENT)
                .deploymentStageDetailsInfo(
                    // saving the expressions as well, so we know in notifications that these fields were expressions
                    SingleServiceEnvDeploymentStageDetailsInfo.builder()
                        .envIdentifier(ParameterFieldHelper.getParameterFieldFinalValueStringOrNullIfBlank(
                            serviceStepV3Parameters.getEnvRef()))
                        .serviceIdentifier(ParameterFieldHelper.getParameterFieldFinalValueStringOrNullIfBlank(
                            serviceStepV3Parameters.getServiceRef()))
                        .infraIdentifier(ParameterFieldHelper.getParameterFieldFinalValueStringOrNullIfBlank(
                            serviceStepV3Parameters.getInfraId()))
                        .build())
                .deploymentType(serviceStepV3Parameters.getDeploymentType())
                // for looping cases, omit <+strategy.identifierPostFix>
                .stageIdentifier(StrategyUtils.refineIdentifier(stageNode.getIdentifier()))
                .stageName(StrategyUtils.refineIdentifier(stageNode.getName()))
                .build());
      } catch (Exception ex) {
        log.warn("Exception occurred while async saving deployment stage plan creation info: {}",
            ExceptionUtils.getMessage(ex), ex);
      }
    });
  }

  private void sendTelemetryEventForSvcEnv(PlanCreationContext ctx, DeploymentStageNode stageNode) {
    try {
      DeploymentStageConfig deploymentStageConfig = stageNode.getDeploymentStageConfig();
      if (deploymentStageConfig == null) {
        return;
      }
      SvcEnvTelemetryInfo svcEnvTelemetryInfo =
          SvcEnvTelemetryInfo.builder()
              .accountIdentifier(ctx.getAccountIdentifier())
              .orgIdentifier(ctx.getOrgIdentifier())
              .projectIdentifier(ctx.getProjectIdentifier())
              .pipelineIdentifier(ctx.getPipelineIdentifier())
              .serviceCount(getServicesCountForMultiSvcEnvDeployment(deploymentStageConfig))
              .environmentCount(getEnvironmentsCountForMultiSvcEnvDeployment(deploymentStageConfig))
              .infrastructureCount(getInfrastructuresCountForMultiSvcEnvDeployment(deploymentStageConfig))
              .deploymentType(deploymentStageConfig.getDeploymentType().toString())
              .stageIdentifier(StrategyUtils.refineIdentifier(stageNode.getIdentifier()))
              .multiServiceDeployment(deploymentStageConfig.getServices() != null)
              .multiEnvironmentDeployment(deploymentStageConfig.getEnvironmentGroup() != null
                  || deploymentStageConfig.getEnvironments() != null)
              .environmentGroupPresent(deploymentStageConfig.getEnvironmentGroup() != null)
              .environmentFilterPresent(
                  EnvironmentInfraFilterUtils.areFiltersPresent(deploymentStageConfig.getEnvironments())
                  || EnvironmentInfraFilterUtils.areFiltersPresent(deploymentStageConfig.getEnvironmentGroup()))
              .build();

      instrumentationHelper.sendSvcEnvEvent(svcEnvTelemetryInfo);
    } catch (Exception ex) {
      log.warn("Exception occurred while sending telemetry event for multi deployment stage: {}",
          ExceptionUtils.getMessage(ex), ex);
    }
  }

  private Integer getServicesCountForMultiSvcEnvDeployment(DeploymentStageConfig deploymentStageConfig) {
    ServicesYaml servicesYaml = deploymentStageConfig.getServices();
    ServiceYamlV2 serviceYamlV2 = deploymentStageConfig.getService();
    if (servicesYaml != null) {
      ParameterField<List<ServiceYamlV2>> servicesList = servicesYaml.getValues();
      if (ParameterField.isNotNull(servicesList)) {
        List<ServiceYamlV2> serviceYamlV2List = servicesList.getValue();
        return serviceYamlV2List.size();
      }
    } else if (serviceYamlV2 != null) {
      return 1;
    }
    return 0;
  }

  private Integer getEnvironmentsCountForMultiSvcEnvDeployment(DeploymentStageConfig deploymentStageConfig) {
    EnvironmentsYaml environmentsYaml = deploymentStageConfig.getEnvironments();
    EnvironmentGroupYaml environmentGroupYaml = deploymentStageConfig.getEnvironmentGroup();
    EnvironmentYamlV2 environmentYamlV2 = deploymentStageConfig.getEnvironment();
    if (environmentsYaml != null) {
      ParameterField<List<EnvironmentYamlV2>> environmentsList = environmentsYaml.getValues();
      if (ParameterField.isNotNull(environmentsList)) {
        List<EnvironmentYamlV2> environmentYamlV2List = environmentsList.getValue();
        return environmentYamlV2List.size();
      }
    } else if (environmentGroupYaml != null) {
      ParameterField<List<EnvironmentYamlV2>> environmentsList = environmentGroupYaml.getEnvironments();
      if (ParameterField.isNotNull(environmentsList)) {
        List<EnvironmentYamlV2> environmentYamlV2List = environmentsList.getValue();
        return environmentYamlV2List.size();
      }
    } else if (environmentYamlV2 != null) {
      return 1;
    }
    return 0;
  }

  private Integer getInfrastructuresCountForMultiSvcEnvDeployment(DeploymentStageConfig deploymentStageConfig) {
    EnvironmentsYaml environmentsYaml = deploymentStageConfig.getEnvironments();
    EnvironmentGroupYaml environmentGroupYaml = deploymentStageConfig.getEnvironmentGroup();
    EnvironmentYamlV2 environmentYamlV2 = deploymentStageConfig.getEnvironment();
    Integer count = 0;

    if (environmentsYaml != null) {
      ParameterField<List<EnvironmentYamlV2>> environmentsList = environmentsYaml.getValues();
      count = getInfrastructureCountForEnvironmentsList(count, environmentsList);
    } else if (environmentGroupYaml != null) {
      ParameterField<List<EnvironmentYamlV2>> environmentsList = environmentGroupYaml.getEnvironments();
      count = getInfrastructureCountForEnvironmentsList(count, environmentsList);
    } else if (environmentYamlV2 != null) {
      count = 1;
    }
    return count;
  }

  private Integer getInfrastructureCountForEnvironmentsList(
      Integer count, ParameterField<List<EnvironmentYamlV2>> environmentsList) {
    if (ParameterField.isNotNull(environmentsList)) {
      List<EnvironmentYamlV2> environmentYamlV2List = environmentsList.getValue();
      for (EnvironmentYamlV2 environmentYamlV2 : environmentYamlV2List) {
        count += getInfrastructureCountForAnEnvironment(environmentYamlV2);
      }
    }
    return count;
  }

  private Integer getInfrastructureCountForAnEnvironment(EnvironmentYamlV2 environmentYamlV2) {
    ParameterField<List<InfraStructureDefinitionYaml>> infraDefinitions =
        environmentYamlV2.getInfrastructureDefinitions();
    if (ParameterField.isNotNull(infraDefinitions)) {
      List<InfraStructureDefinitionYaml> infraStructureDefinitionYamls = infraDefinitions.getValue();
      return infraStructureDefinitionYamls.size();
    }
    return 1;
  }

  protected void saveDeploymentStagePlanCreationSummaryForMultiServiceMultiEnvAsync(
      @NotNull PlanCreationContext ctx, DeploymentStageNode stageNode, YamlField specField) {
    // TODO: get names if possible
    // We won't be able to get filtered infras and envs at the time of plan creation as they are populated at the time
    // of step execution.
    executorService.submit(() -> {
      try {
        saveDeploymentStagePlanCreationSummaryForMultiServiceMultiEnv(ctx, stageNode, specField);
      } catch (Exception ex) {
        log.warn("Exception occurred while async saving multi deployment stage plan creation info: {}",
            ExceptionUtils.getMessage(ex), ex);
      }
    });
  }

  protected void saveDeploymentStagePlanCreationSummaryForMultiServiceMultiEnv(
      @NotNull PlanCreationContext ctx, DeploymentStageNode stageNode, YamlField specField) {
    try {
      boolean allowDifferentInfraForEnvPropagation = featureFlagHelperService.isEnabled(
          ctx.getAccountIdentifier(), FeatureName.CDS_SUPPORT_DIFFERENT_INFRA_DURING_ENV_PROPAGATION);
      deploymentStagePlanCreationInfoService.save(
          DeploymentStagePlanCreationInfo.builder()
              .planExecutionId(ctx.getExecutionUuid())
              .accountIdentifier(ctx.getAccountIdentifier())
              .orgIdentifier(ctx.getOrgIdentifier())
              .projectIdentifier(ctx.getProjectIdentifier())
              .pipelineIdentifier(ctx.getPipelineIdentifier())
              .stageType(DeploymentStageType.MULTI_SERVICE_ENVIRONMENT)
              .deploymentStageDetailsInfo(
                  // saving the expressions as well, so we know in notifications that these fields were expressions
                  MultiServiceEnvDeploymentStageDetailsInfo.builder()
                      .envIdentifiers(getEnvironmentRefsForMultiEnvDeployment(
                          stageNode.getDeploymentStageConfig(), specField, allowDifferentInfraForEnvPropagation))
                      .serviceIdentifiers(
                          getServicesRefsForMultiSvcDeployment(stageNode.getDeploymentStageConfig(), specField))
                      .infraIdentifiers(getInfraRefsForMultiEnvDeployment(
                          stageNode.getDeploymentStageConfig(), specField, allowDifferentInfraForEnvPropagation))
                      .envGroup(getEnvGroupsForMultiEnvDeployment(stageNode.getDeploymentStageConfig()))
                      .build())
              .deploymentType(stageNode.getDeploymentStageConfig().getDeploymentType())
              .stageIdentifier(StrategyUtils.refineIdentifier(stageNode.getIdentifier()))
              .stageName(StrategyUtils.refineIdentifier(stageNode.getName()))
              .build());
    } catch (Exception ex) {
      log.warn("Exception occurred while async saving deployment stage plan creation info: {}",
          ExceptionUtils.getMessage(ex), ex);
    }
  }

  private String getEnvGroupsForMultiEnvDeployment(DeploymentStageConfig deploymentStageConfig) {
    if (deploymentStageConfig.getEnvironmentGroup() != null) {
      return ParameterFieldHelper.getParameterFieldFinalValueStringOrNullIfBlank(
          deploymentStageConfig.getEnvironmentGroup().getEnvGroupRef());
    }
    return null;
  }

  private Set<String> getInfraRefsForMultiEnvDeployment(
      DeploymentStageConfig deploymentStageConfig, YamlField specField, boolean allowDifferentInfraForEnvPropagation) {
    // InfraRefs can come from either environments(Multi Service), environmentGroup(Multi Service), environment(Single
    // Service)
    if (deploymentStageConfig.getEnvironments() != null) {
      EnvironmentsYaml environmentsYaml = deploymentStageConfig.getEnvironments();
      return getInfraRefsForMultiEnvDeployment(environmentsYaml.getValues());
    } else if (deploymentStageConfig.getEnvironmentGroup() != null) {
      EnvironmentGroupYaml environmentGroupYaml = deploymentStageConfig.getEnvironmentGroup();
      if (environmentGroupYaml.getEnvironments() != null) {
        return getInfraRefsForMultiEnvDeployment(environmentGroupYaml.getEnvironments());
      }
      return new HashSet<>();
    } else {
      // Making sure useFromStages are handled
      EnvironmentYamlV2 finalEnvironmentYamlV2 =
          getEnvironmentYaml(specField, deploymentStageConfig, allowDifferentInfraForEnvPropagation);
      return finalEnvironmentYamlV2.getInfrastructureDefinitions()
          .getValue()
          .stream()
          .map(InfraStructureDefinitionYaml::getIdentifier)
          .map(ParameterFieldHelper::getParameterFieldFinalValueStringOrNullIfBlank)
          .collect(Collectors.toSet());
    }
  }

  private Set<String> getInfraRefsForMultiEnvDeployment(ParameterField<List<EnvironmentYamlV2>> environmentsYaml) {
    Set<String> infras = new HashSet<>();
    if (ParameterField.isNotNull(environmentsYaml) && !environmentsYaml.isExpression()) {
      environmentsYaml.getValue().stream().forEach(environmentYamlV2
          -> infras.addAll(
              getEnvironmentRefsForMultiEnvDeploymentFromInfraYaml(environmentYamlV2.getInfrastructureDefinitions())));
    }
    return infras;
  }

  private Set<String> getEnvironmentRefsForMultiEnvDeploymentFromInfraYaml(
      ParameterField<List<InfraStructureDefinitionYaml>> infraDefinitions) {
    if (infraDefinitions != null && ParameterField.isNotNull(infraDefinitions)) {
      if (!infraDefinitions.isExpression()) {
        return infraDefinitions.getValue()
            .stream()
            .map(InfraStructureDefinitionYaml::getIdentifier)
            .map(this::getReferenceValue)
            .collect(Collectors.toSet());
      } else {
        return Arrays.asList(infraDefinitions.getExpressionValue()).stream().collect(Collectors.toSet());
      }
    }
    return new HashSet<>();
  }

  private Set<String> getEnvironmentRefsForMultiEnvDeployment(
      DeploymentStageConfig deploymentStageConfig, YamlField specField, boolean allowDifferentInfraForEnvPropagation) {
    // EnvironmentRefs can come from either environments(Multi Service), environmentGroup(Multi Service),
    // environment(Single Service)
    if (deploymentStageConfig.getEnvironments() != null) {
      EnvironmentsYaml environmentsYaml = deploymentStageConfig.getEnvironments();
      return getEnvironmentRefsForMultiEnvDeployment(environmentsYaml.getValues());
    } else if (deploymentStageConfig.getEnvironmentGroup() != null) {
      EnvironmentGroupYaml environmentGroupYaml = deploymentStageConfig.getEnvironmentGroup();
      return getEnvironmentRefsForMultiEnvDeployment(environmentGroupYaml.getEnvironments());
    } else {
      // Making sure useFromStages are handled
      EnvironmentYamlV2 finalEnvironmentYamlV2 =
          getEnvironmentYaml(specField, deploymentStageConfig, allowDifferentInfraForEnvPropagation);
      return Arrays
          .asList(ParameterFieldHelper.getParameterFieldFinalValueStringOrNullIfBlank(
              finalEnvironmentYamlV2.getEnvironmentRef()))
          .stream()
          .collect(Collectors.toSet());
    }
  }

  private Set<String> getEnvironmentRefsForMultiEnvDeployment(
      ParameterField<List<EnvironmentYamlV2>> environmentsYaml) {
    if (ParameterField.isNotNull(environmentsYaml)) {
      return environmentsYaml.getValue()
          .stream()
          .map(EnvironmentYamlV2::getEnvironmentRef)
          .map(this::getReferenceValue)
          .collect(Collectors.toSet());
    }
    return new HashSet<>();
  }

  private Set<String> getServicesRefsForMultiSvcDeployment(
      DeploymentStageConfig deploymentStageConfig, YamlField specField) {
    if (deploymentStageConfig.getServices() != null) {
      ServicesYaml servicesYaml = deploymentStageConfig.getServices();
      if (ParameterField.isNotNull(servicesYaml.getValues())) {
        if (!servicesYaml.getValues().isExpression()) {
          return getServicesRefsForMultiSvcDeployment(servicesYaml.getValues().getValue());
        } else {
          return Arrays.asList(servicesYaml.getValues().getExpressionValue()).stream().collect(Collectors.toSet());
        }
      }
      return new HashSet<>();
    } else {
      // Making sure useFromStages are handled
      ServiceYamlV2 serviceYamlV2 = getServiceYaml(specField, deploymentStageConfig);
      return Arrays
          .asList(ParameterFieldHelper.getParameterFieldFinalValueStringOrNullIfBlank(serviceYamlV2.getServiceRef()))
          .stream()
          .collect(Collectors.toSet());
    }
  }

  private Set<String> getServicesRefsForMultiSvcDeployment(List<ServiceYamlV2> servicesYaml) {
    Set<String> services = new HashSet<>();
    for (ServiceYamlV2 serviceYamlV2 : servicesYaml) {
      if (serviceYamlV2 != null && ParameterField.isNotNull(serviceYamlV2.getServiceRef())) {
        services.add(getReferenceValue(serviceYamlV2.getServiceRef()));
      }
    }
    return services;
  }

  private String getReferenceValue(ParameterField<String> parameterField) {
    return Objects.nonNull(parameterField.getValue()) ? parameterField.getValue() : parameterField.getExpressionValue();
  }
}
