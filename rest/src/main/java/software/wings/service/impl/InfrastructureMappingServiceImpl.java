package software.wings.service.impl;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toMap;
import static software.wings.api.DeploymentType.AWS_CODEDEPLOY;
import static software.wings.api.DeploymentType.ECS;
import static software.wings.api.DeploymentType.KUBERNETES;
import static software.wings.api.DeploymentType.SSH;
import static software.wings.beans.infrastructure.Host.Builder.aHost;
import static software.wings.dl.PageRequest.Builder.aPageRequest;
import static software.wings.settings.SettingValue.SettingVariableTypes.AWS;
import static software.wings.settings.SettingValue.SettingVariableTypes.GCP;
import static software.wings.settings.SettingValue.SettingVariableTypes.PHYSICAL_DATA_CENTER;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.inject.Singleton;

import com.amazonaws.services.autoscaling.model.LaunchConfiguration;
import org.mongodb.morphia.query.UpdateOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.wings.api.DeploymentType;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.CodeDeployInfrastructureMapping;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.ErrorCode;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
import software.wings.beans.HostValidationRequest;
import software.wings.beans.HostValidationResponse;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.PhysicalInfrastructureMapping;
import software.wings.beans.SearchFilter.Operator;
import software.wings.beans.Service;
import software.wings.beans.ServiceInstance;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.Host;
import software.wings.cloudprovider.aws.AwsCodeDeployService;
import software.wings.dl.PageRequest;
import software.wings.dl.PageRequest.Builder;
import software.wings.dl.PageResponse;
import software.wings.dl.WingsPersistence;
import software.wings.exception.WingsException;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.InfrastructureProvider;
import software.wings.service.intfc.ServiceInstanceService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.settings.SettingValue.SettingVariableTypes;
import software.wings.stencils.Stencil;
import software.wings.stencils.StencilPostProcessor;
import software.wings.utils.ArtifactType;
import software.wings.utils.JsonUtils;
import software.wings.utils.Validator;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.executable.ValidateOnExecution;

/**
 * Created by anubhaw on 1/10/17.
 */
@Singleton
@ValidateOnExecution
public class InfrastructureMappingServiceImpl implements InfrastructureMappingService {
  private static final String stencilsPath = "/templates/inframapping/";
  private static final String uiSchemaSuffix = "-InfraMappingUISchema.json";
  private final Logger logger = LoggerFactory.getLogger(getClass());
  @Inject private WingsPersistence wingsPersistence;
  @Inject private Map<String, InfrastructureProvider> infrastructureProviders;
  @Inject private ServiceInstanceService serviceInstanceService;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject private SettingsService settingsService;
  @Inject private HostService hostService;
  @Inject private ExecutorService executorService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private StencilPostProcessor stencilPostProcessor;
  @Inject private AwsCodeDeployService awsCodeDeployService;

  @Override
  public PageResponse<InfrastructureMapping> list(PageRequest<InfrastructureMapping> pageRequest) {
    PageResponse<InfrastructureMapping> pageResponse = wingsPersistence.query(InfrastructureMapping.class, pageRequest);
    pageResponse.getResponse().forEach(this ::setLoadBalancerName);
    return pageResponse;
  }

  private void setLoadBalancerName(InfrastructureMapping infrastructureMapping) {
    if (infrastructureMapping instanceof AwsInfrastructureMapping) {
      ((AwsInfrastructureMapping) infrastructureMapping)
          .setLoadBalancerName(((AwsInfrastructureMapping) infrastructureMapping).getLoadBalancerId());
    } else if (infrastructureMapping instanceof PhysicalInfrastructureMapping) {
      SettingAttribute settingAttribute =
          settingsService.get(((PhysicalInfrastructureMapping) infrastructureMapping).getLoadBalancerId());
      if (settingAttribute != null) {
        ((PhysicalInfrastructureMapping) infrastructureMapping).setLoadBalancerName(settingAttribute.getName());
      }
    }
  }

  @Override
  public InfrastructureMapping save(@Valid InfrastructureMapping infraMapping) {
    SettingAttribute computeProviderSetting = settingsService.get(infraMapping.getComputeProviderSettingId());

    ServiceTemplate serviceTemplate =
        serviceTemplateService.get(infraMapping.getAppId(), infraMapping.getServiceTemplateId());
    Validator.notNullCheck("Service Template", serviceTemplate);

    infraMapping.setServiceId(serviceTemplate.getServiceId());
    if (computeProviderSetting != null) {
      infraMapping.setComputeProviderName(computeProviderSetting.getName());
    }

    InfrastructureMapping savedInfraMapping = wingsPersistence.saveAndGet(InfrastructureMapping.class, infraMapping);

    if (savedInfraMapping instanceof PhysicalInfrastructureMapping) {
      savedInfraMapping = syncPhysicalHostsAndServiceInstances(savedInfraMapping, serviceTemplate, new ArrayList<>());
    }
    return savedInfraMapping;
  }

  public InfrastructureMapping syncPhysicalHostsAndServiceInstances(
      InfrastructureMapping infrastructureMapping, ServiceTemplate serviceTemplate, List<String> existingHostNames) {
    PhysicalInfrastructureMapping pyInfraMapping = (PhysicalInfrastructureMapping) infrastructureMapping;

    List<String> distinctHostNames = pyInfraMapping.getHostNames()
                                         .stream()
                                         .map(String::trim)
                                         .filter(Objects::nonNull)
                                         .distinct()
                                         .collect(Collectors.toList());

    if (distinctHostNames.size() < ((PhysicalInfrastructureMapping) infrastructureMapping).getHostNames().size()) {
      logger.info("Duplicate hosts {} ", pyInfraMapping.getHostNames().size() - distinctHostNames.size());
      ((PhysicalInfrastructureMapping) infrastructureMapping).setHostNames(distinctHostNames);
    }

    wingsPersistence.updateField(
        InfrastructureMapping.class, infrastructureMapping.getUuid(), "hostNames", distinctHostNames);

    List<String> removedHosts = existingHostNames.stream()
                                    .filter(hostName -> !distinctHostNames.contains(hostName))
                                    .collect(Collectors.toList());

    List<Host> savedHosts =
        distinctHostNames.stream()
            .map(hostName -> {
              Host host = aHost()
                              .withAppId(pyInfraMapping.getAppId())
                              .withEnvId(pyInfraMapping.getEnvId())
                              .withInfraMappingId(infrastructureMapping.getUuid())
                              .withServiceTemplateId(pyInfraMapping.getServiceTemplateId())
                              .withComputeProviderId(pyInfraMapping.getComputeProviderSettingId())
                              .withHostConnAttr(pyInfraMapping.getHostConnectionAttrs())
                              .withHostName(hostName)
                              .build();
              return getInfrastructureProviderByComputeProviderType(PHYSICAL_DATA_CENTER.name()).saveHost(host);
            })
            .collect(Collectors.toList());

    removedHosts.forEach(hostName
        -> getInfrastructureProviderByComputeProviderType(PHYSICAL_DATA_CENTER.name())
               .deleteHost(infrastructureMapping.getAppId(), infrastructureMapping.getUuid(), hostName));
    serviceInstanceService.updateInstanceMappings(serviceTemplate, infrastructureMapping, savedHosts, removedHosts);
    return infrastructureMapping;
  }

  @Override
  public InfrastructureMapping get(String appId, String infraMappingId) {
    return wingsPersistence.get(InfrastructureMapping.class, appId, infraMappingId);
  }

  @Override
  public InfrastructureMapping update(@Valid InfrastructureMapping infrastructureMapping) {
    InfrastructureMapping savedInfraMapping = get(infrastructureMapping.getAppId(), infrastructureMapping.getUuid());
    UpdateOperations<InfrastructureMapping> updateOperations =
        wingsPersistence.createUpdateOperations(InfrastructureMapping.class);

    if (savedInfraMapping.getHostConnectionAttrs() != null
        && !savedInfraMapping.getHostConnectionAttrs().equals(infrastructureMapping.getHostConnectionAttrs())) {
      getInfrastructureProviderByComputeProviderType(infrastructureMapping.getComputeProviderType())
          .updateHostConnAttrs(infrastructureMapping, infrastructureMapping.getHostConnectionAttrs());
      updateOperations.set("hostConnectionAttrs", infrastructureMapping.getHostConnectionAttrs());
    }

    if (infrastructureMapping instanceof EcsInfrastructureMapping) {
      EcsInfrastructureMapping ecsInfrastructureMapping = (EcsInfrastructureMapping) infrastructureMapping;
      updateOperations.set("clusterName", ecsInfrastructureMapping.getClusterName());
      updateOperations.set("region", ecsInfrastructureMapping.getRegion());
    } else if (infrastructureMapping instanceof DirectKubernetesInfrastructureMapping) {
      updateOperations.set("masterUrl", ((DirectKubernetesInfrastructureMapping) infrastructureMapping).getMasterUrl());
      updateOperations.set("username", ((DirectKubernetesInfrastructureMapping) infrastructureMapping).getUsername());
      updateOperations.set("password", ((DirectKubernetesInfrastructureMapping) infrastructureMapping).getPassword());
      updateOperations.set(
          "clusterName", ((DirectKubernetesInfrastructureMapping) infrastructureMapping).getClusterName());
    } else if (infrastructureMapping instanceof GcpKubernetesInfrastructureMapping) {
      updateOperations.set(
          "clusterName", ((GcpKubernetesInfrastructureMapping) infrastructureMapping).getClusterName());
    } else if (infrastructureMapping instanceof AwsInfrastructureMapping) {
      updateOperations.set("region", ((AwsInfrastructureMapping) infrastructureMapping).getRegion());
      updateOperations.set("loadBalancerId", ((AwsInfrastructureMapping) infrastructureMapping).getLoadBalancerId());
    } else if (infrastructureMapping instanceof PhysicalInfrastructureMapping) {
      updateOperations.set(
          "loadBalancerId", ((PhysicalInfrastructureMapping) infrastructureMapping).getLoadBalancerId());
    } else if (infrastructureMapping instanceof CodeDeployInfrastructureMapping) {
      CodeDeployInfrastructureMapping codeDeployInfrastructureMapping =
          ((CodeDeployInfrastructureMapping) infrastructureMapping);
      updateOperations.set("region", codeDeployInfrastructureMapping.getRegion());
      updateOperations.set("applicationName", codeDeployInfrastructureMapping.getApplicationName());
      updateOperations.set("deploymentGroup", codeDeployInfrastructureMapping.getDeploymentGroup());
      updateOperations.set("deploymentConfig", codeDeployInfrastructureMapping.getDeploymentConfig());
    }

    wingsPersistence.update(savedInfraMapping, updateOperations);

    if (savedInfraMapping instanceof PhysicalInfrastructureMapping) {
      ServiceTemplate serviceTemplate =
          serviceTemplateService.get(savedInfraMapping.getAppId(), savedInfraMapping.getServiceTemplateId());
      Validator.notNullCheck("Service Template", serviceTemplate);
      syncPhysicalHostsAndServiceInstances(
          infrastructureMapping, serviceTemplate, ((PhysicalInfrastructureMapping) savedInfraMapping).getHostNames());
    }
    return get(infrastructureMapping.getAppId(), infrastructureMapping.getUuid());
  }

  @Override
  public void delete(String appId, String envId, String infraMappingId) {
    InfrastructureMapping infrastructureMapping = get(appId, infraMappingId);
    if (infrastructureMapping != null) {
      boolean deleted = wingsPersistence.delete(infrastructureMapping);
      if (deleted) {
        InfrastructureProvider infrastructureProvider =
            getInfrastructureProviderByComputeProviderType(infrastructureMapping.getComputeProviderType());
        executorService.submit(() -> infrastructureProvider.deleteHostByInfraMappingId(appId, infraMappingId));
        executorService.submit(() -> serviceInstanceService.deleteByInfraMappingId(appId, infraMappingId));
      }
    }
  }

  @Override
  public Map<String, Object> getInfraMappingStencils(String appId) {
    return stencilPostProcessor.postProcess(Lists.newArrayList(InfrastructureMappingType.values()), appId)
        .stream()
        .collect(toMap(Stencil::getName, Function.identity()));

    /*System.out.println();
    //TODO:: dynamically read
    Map<String, Map<String, Object>> infraStencils = new HashMap<>();

    //TODO:: InfraMapping remove this hack and use stencils model

    JsonNode physicalJsonSchema = JsonUtils.jsonSchema(PhysicalInfrastructureMapping.class);
    List<SettingAttribute> settingAttributes = settingsService.getSettingAttributesByType(appId,
    SettingVariableTypes.HOST_CONNECTION_ATTRIBUTES.name());

    Map<String, String> data = settingAttributes.stream().collect(toMap(SettingAttribute::getUuid,
    SettingAttribute::getName));

    ObjectNode jsonSchemaField = ((ObjectNode) physicalJsonSchema.get("properties").get("hostConnectionAttrs"));
    jsonSchemaField.set("enum", JsonUtils.asTree(data.keySet()));
    jsonSchemaField.set("enumNames", JsonUtils.asTree(data.values()));

    infraStencils
        .put(PHYSICAL_DATA_CENTER_SSH.name(), ImmutableMap.of("jsonSchema", physicalJsonSchema, "uiSchema",
    readUiSchema(PHYSICAL_DATA_CENTER_SSH.name())));

    JsonNode awsJsonSchema = JsonUtils.jsonSchema(AwsInfrastructureMapping.class);
    jsonSchemaField = ((ObjectNode) awsJsonSchema.get("properties").get("hostConnectionAttrs"));
    jsonSchemaField.set("enum", JsonUtils.asTree(data.keySet()));
    jsonSchemaField.set("enumNames", JsonUtils.asTree(data.values()));

    infraStencils.put(AWS_SSH.name(), ImmutableMap.of("jsonSchema", awsJsonSchema, "uiSchema",
    readUiSchema(AWS_SSH.name()))); infraStencils .put(AWS_ECS.name(), ImmutableMap.of("jsonSchema",
    JsonUtils.jsonSchema(EcsInfrastructureMapping.class), "uiSchema", readUiSchema(AWS_ECS.name())));
    infraStencils.put(GCP_KUBERNETES.name(),
        ImmutableMap.of("jsonSchema", JsonUtils.jsonSchema(GcpKubernetesInfrastructureMapping.class), "uiSchema",
    readUiSchema(GCP_KUBERNETES.name()))); return infraStencils;*/
  }

  @Override
  public List<ServiceInstance> selectServiceInstances(
      String appId, String envId, String infraMappingId, Map<String, Object> selectionParams) {
    InfrastructureMapping infrastructureMapping = get(appId, infraMappingId);
    Validator.notNullCheck("Infra Mapping", infrastructureMapping);

    SettingAttribute computeProviderSetting = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    Validator.notNullCheck("Compute Provider", computeProviderSetting);

    if (infrastructureMapping instanceof AwsInfrastructureMapping) {
      syncAwsHostsAndUpdateInstances(
          infrastructureMapping, computeProviderSetting); // TODO:: instead of on-demand do it periodically?
    }
    return selectServiceInstancesByInfraMapping(
        appId, infrastructureMapping.getServiceTemplateId(), infrastructureMapping.getUuid(), selectionParams);
  }

  @Override
  public List<String> listClusters(String appId, String deploymentType, String computeProviderId, String region) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    Validator.notNullCheck("Compute Provider", computeProviderSetting);

    String type = computeProviderSetting.getValue().getType();
    if (AWS.name().equals(type)) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listClusterNames(computeProviderSetting, region);
    } else if (GCP.name().equals(type)) {
      GcpInfrastructureProvider infrastructureProvider =
          (GcpInfrastructureProvider) getInfrastructureProviderByComputeProviderType(GCP.name());
      return infrastructureProvider.listClusterNames(computeProviderSetting);
    }
    return Collections.emptyList();
  }

  @Override
  public List<String> listImages(String appId, String deploymentType, String computeProviderId, String region) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    Validator.notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listAMIs(computeProviderSetting, region);
    }
    return Collections.emptyList();
  }

  @Override
  public List<String> listRegions(String appId, String deploymentType, String computeProviderId) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    Validator.notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listRegions(computeProviderSetting);
    }
    return Collections.emptyList();
  }

  @Override
  public List<String> listInstanceTypes(String appId, String deploymentType, String computeProviderId) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    Validator.notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listInstanceTypes(computeProviderSetting);
    }
    return Collections.emptyList();
  }

  @Override
  public List<String> listInstanceRoles(String appId, String deploymentType, String computeProviderId) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    Validator.notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listIAMInstanceRoles(computeProviderSetting);
    }
    return Collections.emptyList();
  }

  @Override
  public Map<String, String> listAllRoles(String appId, String deploymentType, String computeProviderId) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    Validator.notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listIAMRoles(computeProviderSetting);
    }
    return Collections.emptyMap();
  }

  @Override
  public List<String> listNetworks(String appId, String deploymentType, String computeProviderId, String region) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    Validator.notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listVPCs(region, computeProviderSetting);
    }
    return Collections.emptyList();
  }

  @Override
  public Map<String, String> listLoadBalancers(String appId, String deploymentType, String computeProviderId) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    Validator.notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listLoadBalancers(computeProviderSetting).stream().collect(toMap(s -> s, s -> s));
    } else if (PHYSICAL_DATA_CENTER.name().equals(computeProviderSetting.getValue().getType())) {
      return settingsService
          .getGlobalSettingAttributesByType(computeProviderSetting.getAccountId(), SettingVariableTypes.ELB.name())
          .stream()
          .collect(toMap(SettingAttribute::getUuid, SettingAttribute::getName));
    }
    return Collections.emptyMap();
  }

  @Override
  public List<String> listClassicLoadBalancers(String appId, String computeProviderId, String region) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    Validator.notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listClassicLoadBalancers(computeProviderSetting, region);
    }
    return Collections.emptyList();
  }

  @Override
  public Map<String, String> listTargetGroups(
      String appId, String deploymentType, String computeProviderId, String loadBalancerName) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    Validator.notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      return infrastructureProvider.listTargetGroups(computeProviderSetting, loadBalancerName);
    }
    return Collections.emptyMap();
  }

  @Override
  public List<HostValidationResponse> validateHost(@Valid HostValidationRequest validationRequest) {
    SettingAttribute computeProviderSetting = settingsService.get(validationRequest.getComputeProviderSettingId());
    Validator.notNullCheck("Compute Provider", computeProviderSetting);

    if (!PHYSICAL_DATA_CENTER.name().equals(computeProviderSetting.getValue().getType())) {
      throw new WingsException(ErrorCode.INVALID_REQUEST, "message", "Invalid infrastructure provider");
    }
    StaticInfrastructureProvider infrastructureProvider =
        (StaticInfrastructureProvider) getInfrastructureProviderByComputeProviderType(PHYSICAL_DATA_CENTER.name());

    SettingAttribute hostConnectionSetting = settingsService.get(validationRequest.getHostConnectionAttrs());

    return asList(infrastructureProvider.validateHost(
        validationRequest.getHostNames().get(0), hostConnectionSetting, validationRequest.getExecutionCredential()));
  }

  @Override
  public List<String> listElasticLoadBalancer(String accessKey, char[] secretKey, String region) {
    AwsInfrastructureProvider infrastructureProvider =
        (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
    return infrastructureProvider.listClassicLoadBalancers(accessKey, secretKey, region);
  }

  @Override
  public List<ServiceInstance> selectServiceInstances(
      String appId, String serviceId, String envId, String computeProviderId, Map<String, Object> selectionParams) {
    String serviceTemplateId =
        (String) serviceTemplateService.getTemplateRefKeysByService(appId, serviceId, envId).get(0).getId();

    InfrastructureMapping infrastructureMapping = wingsPersistence.createQuery(InfrastructureMapping.class)
                                                      .field("appId")
                                                      .equal(appId)
                                                      .field("envId")
                                                      .equal(envId)
                                                      .field("serviceTemplateId")
                                                      .equal(serviceTemplateId)
                                                      .field("computeProviderSettingId")
                                                      .equal(computeProviderId)
                                                      .get();
    Validator.notNullCheck("Infra Mapping", infrastructureMapping);

    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    Validator.notNullCheck("Compute Provider", computeProviderSetting);

    if (infrastructureMapping instanceof AwsInfrastructureMapping) {
      syncAwsHostsAndUpdateInstances(
          infrastructureMapping, computeProviderSetting); // TODO:: instead of on-demand do it periodically?
    }
    return selectServiceInstancesByInfraMapping(
        appId, serviceTemplateId, infrastructureMapping.getUuid(), selectionParams);
  }

  @Override
  public List<String> listCodeDeployApplicationNames(String computeProviderId, String region) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    Validator.notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      return awsCodeDeployService.listApplications(region, computeProviderSetting);
    }
    return ImmutableList.of();
  }

  @Override
  public List<String> listCodeDeployDeploymentGroups(String computeProviderId, String region, String applicationName) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    Validator.notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      return awsCodeDeployService.listDeploymentGroup(region, applicationName, computeProviderSetting);
    }
    return ImmutableList.of();
  }

  @Override
  public List<String> listCodeDeployDeploymentConfigs(String computeProviderId, String region) {
    SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
    Validator.notNullCheck("Compute Provider", computeProviderSetting);

    if (AWS.name().equals(computeProviderSetting.getValue().getType())) {
      return awsCodeDeployService.listDeploymentConfiguration(region, computeProviderSetting);
    }
    return ImmutableList.of();
  }

  private void syncAwsHostsAndUpdateInstances(
      InfrastructureMapping infrastructureMapping, SettingAttribute computeProviderSetting) {
    AwsInfrastructureProvider awsInfrastructureProvider =
        (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
    AwsInfrastructureMapping awsInfrastructureMapping = (AwsInfrastructureMapping) infrastructureMapping;
    List<Host> hosts = awsInfrastructureProvider
                           .listHosts(awsInfrastructureMapping.getRegion(), computeProviderSetting, new PageRequest<>())
                           .getResponse();
    PageRequest<Host> pageRequest = aPageRequest()
                                        .withLimit(PageRequest.UNLIMITED)
                                        .addFilter("appId", Operator.EQ, infrastructureMapping.getAppId())
                                        .addFilter("infraMappingId", Operator.EQ, infrastructureMapping.getUuid())
                                        .build();
    List<String> existingPublicDnsNames =
        hostService.list(pageRequest).getResponse().stream().map(Host::getPublicDns).collect(Collectors.toList());

    ListIterator<Host> hostListIterator = hosts.listIterator();

    while (hostListIterator.hasNext()) {
      Host host = hostListIterator.next();
      if (existingPublicDnsNames.contains(host.getPublicDns())) {
        hostListIterator.remove();
        existingPublicDnsNames.remove(host.getPublicDns());
      }
    }
    updateHostsAndServiceInstances(infrastructureMapping, hosts, existingPublicDnsNames);
  }

  private void updateHostsAndServiceInstances(
      InfrastructureMapping infraMapping, List<Host> activeHosts, List<String> deletedPublicDnsNames) {
    InfrastructureProvider awsInfrastructureProvider =
        getInfrastructureProviderByComputeProviderType(infraMapping.getComputeProviderType());

    deletedPublicDnsNames.forEach(publicDns -> {
      awsInfrastructureProvider.deleteHost(infraMapping.getAppId(), infraMapping.getUuid(), publicDns);
    });

    List<Host> savedHosts = activeHosts.stream()
                                .map(host -> {
                                  host.setAppId(infraMapping.getAppId());
                                  host.setEnvId(infraMapping.getEnvId());
                                  host.setHostConnAttr(infraMapping.getHostConnectionAttrs());
                                  host.setInfraMappingId(infraMapping.getUuid());
                                  host.setServiceTemplateId(infraMapping.getServiceTemplateId());
                                  return awsInfrastructureProvider.saveHost(host);
                                })
                                .collect(Collectors.toList());

    ServiceTemplate serviceTemplate =
        serviceTemplateService.get(infraMapping.getAppId(), infraMapping.getServiceTemplateId());
    serviceInstanceService.updateInstanceMappings(serviceTemplate, infraMapping, savedHosts, deletedPublicDnsNames);
  }

  private List<ServiceInstance> selectServiceInstancesByInfraMapping(
      String appId, Object serviceTemplateId, String infraMappingId, Map<String, Object> selectionParams) {
    Builder requestBuilder = aPageRequest()
                                 .addFilter("appId", Operator.EQ, appId)
                                 .addFilter("serviceTemplateId", Operator.EQ, serviceTemplateId)
                                 .addFilter("infraMappingId", Operator.EQ, infraMappingId);

    boolean specificHosts =
        selectionParams.containsKey("specificHosts") && (boolean) selectionParams.get("specificHosts");
    String instanceCount = selectionParams.containsKey("instanceCount")
        ? Integer.toString((int) selectionParams.get("instanceCount"))
        : PageRequest.UNLIMITED;

    if (specificHosts) {
      List<String> hostNames = (List<String>) selectionParams.get("hostNames");
      requestBuilder.addFilter("publicDns", Operator.IN, hostNames.toArray());
    } else {
      requestBuilder.withLimit(instanceCount);
    }

    return serviceInstanceService.list(requestBuilder.build()).getResponse();
  }

  @Override
  public List<String> listComputeProviderHosts(String appId, String envId, String serviceId, String computeProviderId) {
    Object serviceTemplateId =
        serviceTemplateService.getTemplateRefKeysByService(appId, serviceId, envId).get(0).getId();
    InfrastructureMapping infrastructureMapping = wingsPersistence.createQuery(InfrastructureMapping.class)
                                                      .field("appId")
                                                      .equal(appId)
                                                      .field("envId")
                                                      .equal(envId)
                                                      .field("serviceTemplateId")
                                                      .equal(serviceTemplateId)
                                                      .field("computeProviderSettingId")
                                                      .equal(computeProviderId)
                                                      .get();
    Validator.notNullCheck("Infra Mapping", infrastructureMapping);

    if (infrastructureMapping instanceof PhysicalInfrastructureMapping) {
      return ((PhysicalInfrastructureMapping) infrastructureMapping).getHostNames();
    } else if (infrastructureMapping instanceof AwsInfrastructureMapping) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
      Validator.notNullCheck("Compute Provider", computeProviderSetting);
      return infrastructureProvider
          .listHosts(((AwsInfrastructureMapping) infrastructureMapping).getRegion(), computeProviderSetting,
              new PageRequest<>())
          .getResponse()
          .stream()
          .map(Host::getPublicDns)
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  @Override
  public List<LaunchConfiguration> listLaunchConfigs(String appId, String envId, String infraMappingId) {
    InfrastructureMapping infrastructureMapping = get(appId, infraMappingId);
    Validator.notNullCheck("Infra Mapping", infrastructureMapping);

    if (infrastructureMapping instanceof AwsInfrastructureMapping) {
      AwsInfrastructureProvider infrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(AWS.name());
      SettingAttribute computeProviderSetting =
          settingsService.get(infrastructureMapping.getComputeProviderSettingId());
      Validator.notNullCheck("Compute Provider", computeProviderSetting);
      return infrastructureProvider.listLaunchConfigurations(
          computeProviderSetting, ((AwsInfrastructureMapping) infrastructureMapping).getRegion());
    }
    return Collections.emptyList();
  }

  private InfrastructureProvider getInfrastructureProviderByComputeProviderType(String computeProviderType) {
    return infrastructureProviders.get(computeProviderType);
  }

  @Override
  public InfrastructureMapping getInfraMappingByComputeProviderAndServiceId(
      String appId, String envId, String serviceId, String computeProviderId) {
    Object serviceTemplateId =
        serviceTemplateService.getTemplateRefKeysByService(appId, serviceId, envId).get(0).getId();
    InfrastructureMapping infrastructureMapping = wingsPersistence.createQuery(InfrastructureMapping.class)
                                                      .field("appId")
                                                      .equal(appId)
                                                      .field("envId")
                                                      .equal(envId)
                                                      .field("serviceTemplateId")
                                                      .equal(serviceTemplateId)
                                                      .field("computeProviderSettingId")
                                                      .equal(computeProviderId)
                                                      .get();
    Validator.notNullCheck("Infra Mapping", infrastructureMapping);
    return infrastructureMapping;
  }

  @Override
  public List<ServiceInstance> provisionNodes(
      String appId, String envId, String infraMappingId, String launcherConfigName, int instanceCount) {
    InfrastructureMapping infrastructureMapping = get(appId, infraMappingId);
    Validator.notNullCheck("Infra Mapping", infrastructureMapping);

    if (infrastructureMapping instanceof AwsInfrastructureMapping) {
      SettingAttribute computeProviderSetting =
          settingsService.get(infrastructureMapping.getComputeProviderSettingId());
      Validator.notNullCheck("Compute Provider", computeProviderSetting);

      AwsInfrastructureProvider awsInfrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(
              infrastructureMapping.getComputeProviderType());
      AwsInfrastructureMapping awsInfrastructureMapping = (AwsInfrastructureMapping) infrastructureMapping;

      List<Host> hosts = awsInfrastructureProvider.provisionHosts(
          awsInfrastructureMapping.getRegion(), computeProviderSetting, launcherConfigName, instanceCount);
      updateHostsAndServiceInstances(infrastructureMapping, hosts, ImmutableList.of());

      return selectServiceInstancesByInfraMapping(appId, infrastructureMapping.getServiceTemplateId(),
          infrastructureMapping.getUuid(),
          ImmutableMap.of(
              "specificHosts", true, "hostNames", hosts.stream().map(Host::getHostName).collect(Collectors.toList())));
    } else {
      throw new WingsException(
          ErrorCode.INVALID_REQUEST, "message", "Node Provisioning is only supported for AWS and GCP infra mapping");
    }
  }

  @Override
  public void deProvisionNodes(
      String appId, String serviceId, String envId, String computeProviderId, List<String> hostNames) {
    String serviceTemplateId =
        (String) serviceTemplateService.getTemplateRefKeysByService(appId, serviceId, envId).get(0).getId();

    InfrastructureMapping infrastructureMapping = wingsPersistence.createQuery(InfrastructureMapping.class)
                                                      .field("appId")
                                                      .equal(appId)
                                                      .field("envId")
                                                      .equal(envId)
                                                      .field("serviceTemplateId")
                                                      .equal(serviceTemplateId)
                                                      .field("computeProviderSettingId")
                                                      .equal(computeProviderId)
                                                      .get();
    Validator.notNullCheck("Infra Mapping", infrastructureMapping);

    if (infrastructureMapping instanceof AwsInfrastructureMapping) {
      SettingAttribute computeProviderSetting = settingsService.get(computeProviderId);
      Validator.notNullCheck("Compute Provider", computeProviderSetting);

      AwsInfrastructureProvider awsInfrastructureProvider =
          (AwsInfrastructureProvider) getInfrastructureProviderByComputeProviderType(
              infrastructureMapping.getComputeProviderType());
      AwsInfrastructureMapping awsInfrastructureMapping = (AwsInfrastructureMapping) infrastructureMapping;

      awsInfrastructureProvider.deProvisionHosts(appId, infrastructureMapping.getUuid(), computeProviderSetting,
          awsInfrastructureMapping.getRegion(), hostNames);
      updateHostsAndServiceInstances(infrastructureMapping, ImmutableList.of(), hostNames);
    } else {
      throw new WingsException(
          ErrorCode.INVALID_REQUEST, "message", "Node deprovisioning is only supported for AWS infra mapping");
    }
  }

  @Override
  public Map<DeploymentType, List<SettingVariableTypes>> listInfraTypes(String appId, String envId, String serviceId) {
    // TODO:: use serviceId and envId to narrow down list ??

    Service service = serviceResourceService.get(appId, serviceId);
    ArtifactType artifactType = service.getArtifactType();
    Map<DeploymentType, List<SettingVariableTypes>> infraTypes = new HashMap<>();

    if (artifactType == ArtifactType.DOCKER) {
      infraTypes.put(ECS, asList(SettingVariableTypes.AWS));
      infraTypes.put(KUBERNETES, asList(SettingVariableTypes.GCP, SettingVariableTypes.DIRECT));
    } else if (artifactType == ArtifactType.TAR || artifactType == ArtifactType.ZIP) {
      infraTypes.put(AWS_CODEDEPLOY, asList(SettingVariableTypes.AWS));
      infraTypes.put(SSH, asList(SettingVariableTypes.PHYSICAL_DATA_CENTER, SettingVariableTypes.AWS));
    } else {
      infraTypes.put(SSH, asList(SettingVariableTypes.PHYSICAL_DATA_CENTER, SettingVariableTypes.AWS));
    }
    return infraTypes;
  }

  private Object readUiSchema(String type) {
    try {
      return readResource(stencilsPath + type + uiSchemaSuffix);
    } catch (Exception e) {
      return new HashMap<String, Object>();
    }
  }

  private Object readResource(String file) {
    try {
      URL url = this.getClass().getResource(file);
      String json = Resources.toString(url, Charsets.UTF_8);
      return JsonUtils.asObject(json, HashMap.class);
    } catch (Exception exception) {
      throw new WingsException("Error reading ui schema - " + file, exception);
    }
  }
}
