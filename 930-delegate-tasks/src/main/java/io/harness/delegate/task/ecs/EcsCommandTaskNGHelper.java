/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.trim;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.EcsV2Client;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.ecs.EcsMapper;
import io.harness.delegate.beans.ecs.EcsRunTaskResult;
import io.harness.delegate.beans.ecs.EcsTask;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.request.EcsBlueGreenRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsRunTaskResponse;
import io.harness.exception.CommandExecutionException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.TimeoutException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.serializer.YamlUtils;

import software.wings.beans.LogColor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DeleteScalingPolicyRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DeregisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.PutScalingPolicyRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.ServiceNamespace;
import software.amazon.awssdk.services.ecs.model.Container;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.CreateServiceResponse;
import software.amazon.awssdk.services.ecs.model.DeleteServiceRequest;
import software.amazon.awssdk.services.ecs.model.DeleteServiceResponse;
import software.amazon.awssdk.services.ecs.model.Deployment;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.DesiredStatus;
import software.amazon.awssdk.services.ecs.model.ListServicesRequest;
import software.amazon.awssdk.services.ecs.model.ListServicesResponse;
import software.amazon.awssdk.services.ecs.model.ListTasksRequest;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.LoadBalancer;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.ServiceEvent;
import software.amazon.awssdk.services.ecs.model.ServiceField;
import software.amazon.awssdk.services.ecs.model.Tag;
import software.amazon.awssdk.services.ecs.model.TagResourceRequest;
import software.amazon.awssdk.services.ecs.model.Task;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;
import software.amazon.awssdk.services.ecs.model.UntagResourceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Action;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ActionTypeEnum;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyListenerRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyRuleRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(CDP)
@Singleton
@Slf4j
public class EcsCommandTaskNGHelper {
  @Inject private EcsV2Client ecsV2Client;
  @Inject private ElbV2Client elbV2Client;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private TimeLimiter timeLimiter;
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;

  private YamlUtils yamlUtils = new YamlUtils();
  public static final String DELIMITER = "__";
  public static final String BG_VERSION = "BG_VERSION";
  public static final String BG_GREEN = "GREEN";
  public static final String BG_BLUE = "BLUE";
  private static final int DESCRIBE_SERVICE_API_MAX_LIMIT = 10;
  private static final int SERVICE_REVISION_FOR_FIRST_DEPLOYMENT = 1;
  private static final String INVALID_LOAD_BALANCER_CONFIG_ERROR = "Invalid Load balancer configuration in Service.";
  private static final String INVALID_TARGET_GROUP_ERROR_HINT =
      "Service should be attached with prod target group or stage target group but not both";
  private static final String NO_TARGET_GROUP_ERROR_EXPLANATION =
      "Service: %s is not associated with any target group. "
      + "Blue/Green Service should be attached with prod target group or stage target group.";
  private static final String BOTH_TARGET_GROUP_ERROR_EXPLANATION =
      "Service: %s is associated with both prod target group: %s and stage target group: %s. "
      + "Blue/Green Service should be attached with prod target group or stage target group but not both.";
  private static final String OTHER_TARGET_GROUP_ERROR_EXPLANATION =
      "Service: %s is associated with following target groups: %s "
      + "Blue/Green Service should be attached with prod target group or stage target group.";
  private static final String BOTH_SERVICE_WITH_SAME_TARGET_GROUP_ERROR_HINT =
      "Only one version of Service should be attached with %s target group but not both.";
  private static final String BOTH_SERVICE_WITH_SAME_TARGET_GROUP_ERROR_EXPLANATION =
      "Service: %s and Service: %s is associated with %s target group: %s. "
      + "We can't decide actual %s service between these two service, so only one service should be attached with %s target group.";
  private static final String PROD_SERVICE = "prod";
  private static final String STAGE_SERVICE = "stage";

  public RegisterTaskDefinitionResponse createTaskDefinition(
      RegisterTaskDefinitionRequest registerTaskDefinitionRequest, String region, AwsConnectorDTO awsConnectorDTO) {
    return ecsV2Client.createTask(
        awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), registerTaskDefinitionRequest, region);
  }

  public DescribeTaskDefinitionResponse describeTaskDefinition(
      DescribeTaskDefinitionRequest describeTaskDefinitionRequest, String region, AwsConnectorDTO awsConnectorDTO) {
    return ecsV2Client.describeTaskDefinition(
        awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), describeTaskDefinitionRequest, region);
  }

  public CreateServiceResponse createService(
      CreateServiceRequest createServiceRequest, String region, AwsConnectorDTO awsConnectorDTO) {
    return ecsV2Client.createService(
        awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), createServiceRequest, region);
  }

  public UpdateServiceResponse updateService(
      UpdateServiceRequest updateServiceRequest, String region, AwsConnectorDTO awsConnectorDTO) {
    return ecsV2Client.updateService(
        awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), updateServiceRequest, region);
  }

  public DeleteServiceResponse deleteService(
      String serviceName, String cluster, String region, AwsConnectorDTO awsConnectorDTO) {
    DeleteServiceRequest deleteServiceRequest =
        DeleteServiceRequest.builder().service(serviceName).cluster(cluster).force(true).build();

    return ecsV2Client.deleteService(
        awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), deleteServiceRequest, region);
  }

  public Optional<Service> describeService(
      String cluster, String serviceName, String region, AwsConnectorDTO awsConnectorDTO) {
    DescribeServicesResponse describeServicesResponse = ecsV2Client.describeService(
        awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), cluster, serviceName, region);
    return CollectionUtils.isNotEmpty(describeServicesResponse.services())
        ? Optional.of(describeServicesResponse.services().get(0))
        : Optional.empty();
  }

  public void ecsServiceSteadyStateCheck(LogCallback deployLogCallback, AwsConnectorDTO awsConnectorDTO, String cluster,
      String serviceName, String region, long serviceSteadyStateTimeout, List<ServiceEvent> eventsAlreadyProcessed) {
    deployLogCallback.saveExecutionLog(
        format("Waiting for Service %s to reach steady state %n", serviceName), LogLevel.INFO);

    try {
      HTimeLimiter.callInterruptible(timeLimiter, Duration.ofMillis(serviceSteadyStateTimeout), () -> {
        Service service;

        do {
          service = describeService(cluster, serviceName, region, awsConnectorDTO).get();

          if (service == null) {
            String msg = new StringBuilder()
                             .append("Received empty response while describing service ")
                             .append(serviceName)
                             .toString();
            deployLogCallback.saveExecutionLog(msg, LogLevel.ERROR);
            throw new RuntimeException(msg);
          }
          printAwsEcsDeployments(service, deployLogCallback);
          printAwsEvent(service, eventsAlreadyProcessed, deployLogCallback);
          sleep(ofSeconds(10));
        } while (!hasServiceReachedSteadyState(service));

        return true;
      });
    } catch (Exception e) {
      String msg = new StringBuilder()
                       .append("Timed out waiting for service: ")
                       .append(serviceName)
                       .append(" to reach steady state")
                       .toString();
      deployLogCallback.saveExecutionLog(msg, LogLevel.ERROR);
      throw new RuntimeException(msg, e);
    }

    deployLogCallback.saveExecutionLog(format("Service %s reached steady state %n", serviceName), LogLevel.INFO);
  }

  boolean hasServiceReachedSteadyState(Service service) {
    List<Deployment> deployments = service.deployments();
    if (deployments.size() != 1) {
      return false;
    }
    long deploymentTime = deployments.get(0).updatedAt().getEpochSecond();
    long steadyStateMessageTime =
        service.events()
            .stream()
            .filter(serviceEvent -> serviceEvent.message().endsWith("has reached a steady state."))
            .map(serviceEvent -> serviceEvent.createdAt().getEpochSecond())
            .max(Long::compare)
            .orElse(0L);
    return steadyStateMessageTime >= deploymentTime;
  }

  public WaiterResponse<DescribeServicesResponse> ecsServiceInactiveStateCheck(LogCallback deployLogCallback,
      AwsConnectorDTO awsConnectorDTO, String cluster, String serviceName, String region,
      int serviceInactiveStateTimeout) {
    deployLogCallback.saveExecutionLog(
        format("Waiting for existing Service %s to reach inactive state %n", serviceName), LogLevel.INFO);

    DescribeServicesRequest describeServicesRequest =
        DescribeServicesRequest.builder().services(Collections.singletonList(serviceName)).cluster(cluster).build();

    WaiterResponse<DescribeServicesResponse> describeServicesResponseWaiterResponse =
        ecsV2Client.ecsServiceInactiveStateCheck(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO),
            describeServicesRequest, region, serviceInactiveStateTimeout);

    if (describeServicesResponseWaiterResponse.matched().exception().isPresent()) {
      Throwable throwable = describeServicesResponseWaiterResponse.matched().exception().get();
      deployLogCallback.saveExecutionLog(
          format("Existing Service %s failed to reach inactive state %n", serviceName), LogLevel.ERROR);
      throw new RuntimeException(
          format("Existing Service %s failed to reach inactive state %n", serviceName), throwable);
    }

    deployLogCallback.saveExecutionLog(
        format("Existing Service %s reached inactive state %n", serviceName), LogLevel.INFO);
    return describeServicesResponseWaiterResponse;
  }

  public DescribeScalableTargetsResponse listScalableTargets(
      AwsConnectorDTO awsConnectorDTO, String cluster, String serviceName, String region) {
    DescribeScalableTargetsRequest describeScalableTargetsRequest =
        DescribeScalableTargetsRequest.builder()
            .maxResults(100)
            .serviceNamespace(ServiceNamespace.ECS)
            .resourceIds(Collections.singletonList(format("service/%s/%s", cluster, serviceName)))
            .build();
    return ecsV2Client.listScalableTargets(
        awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), describeScalableTargetsRequest, region);
  }

  public DescribeScalingPoliciesResponse listScalingPolicies(
      AwsConnectorDTO awsConnectorDTO, String cluster, String serviceName, String region) {
    DescribeScalingPoliciesRequest describeScalingPoliciesRequest =
        DescribeScalingPoliciesRequest.builder()
            .maxResults(100)
            .serviceNamespace(ServiceNamespace.ECS)
            .resourceId(format("service/%s/%s", cluster, serviceName))
            .build();
    return ecsV2Client.listScalingPolicies(
        awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), describeScalingPoliciesRequest, region);
  }

  public void deleteScalingPolicies(
      AwsConnectorDTO awsConnectorDTO, String serviceName, String cluster, String region, LogCallback logCallback) {
    DescribeScalingPoliciesRequest describeScalingPoliciesRequest =
        DescribeScalingPoliciesRequest.builder()
            .maxResults(100)
            .serviceNamespace(ServiceNamespace.ECS)
            .resourceId(format("service/%s/%s", cluster, serviceName))
            .build();
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    DescribeScalingPoliciesResponse describeScalingPoliciesResponse =
        ecsV2Client.listScalingPolicies(awsInternalConfig, describeScalingPoliciesRequest, region);

    if (describeScalingPoliciesResponse != null
        && CollectionUtils.isNotEmpty(describeScalingPoliciesResponse.scalingPolicies())) {
      logCallback.saveExecutionLog(format("Deleting Scaling Policies from service %s..%n", serviceName), LogLevel.INFO);
      describeScalingPoliciesResponse.scalingPolicies().forEach(scalingPolicy -> {
        DeleteScalingPolicyRequest deleteScalingPolicyRequest =
            DeleteScalingPolicyRequest.builder()
                .policyName(scalingPolicy.policyName())
                .resourceId(format("service/%s/%s", cluster, serviceName))
                .scalableDimension(scalingPolicy.scalableDimension())
                .serviceNamespace(scalingPolicy.serviceNamespace())
                .build();
        ecsV2Client.deleteScalingPolicy(awsInternalConfig, deleteScalingPolicyRequest, region);
        logCallback.saveExecutionLog(
            format("Deleted Scaling Policy %s from service %s %n..", scalingPolicy.policyName(), serviceName),
            LogLevel.INFO);
      });

      logCallback.saveExecutionLog(format("Deleted Scaling Policies from service %s %n%n", serviceName), LogLevel.INFO);
    }
  }

  public List<EcsTask> getRunningEcsTasks(
      AwsConnectorDTO awsConnectorDTO, String cluster, String serviceName, String region) {
    String nextToken = null;
    List<EcsTask> response = new ArrayList<>();
    do {
      ListTasksRequest.Builder listTasksRequestBuilder =
          ListTasksRequest.builder().cluster(cluster).serviceName(serviceName).desiredStatus(DesiredStatus.RUNNING);

      if (nextToken != null) {
        listTasksRequestBuilder.nextToken(nextToken);
      }

      ListTasksResponse listTasksResponse = ecsV2Client.listTaskArns(
          awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), listTasksRequestBuilder.build(), region);
      nextToken = listTasksResponse.nextToken();
      if (CollectionUtils.isNotEmpty(listTasksResponse.taskArns())) {
        DescribeTasksResponse describeTasksResponse = ecsV2Client.getTasks(
            awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), cluster, listTasksResponse.taskArns(), region);
        response.addAll(describeTasksResponse.tasks()
                            .stream()
                            .map(task -> EcsMapper.toEcsTask(task, serviceName))
                            .collect(Collectors.toList()));
      }
    } while (nextToken != null);
    return response;
  }

  public List<Service> fetchServicesOfCluster(AwsConnectorDTO awsConnectorDTO, String cluster, String region) {
    List<String> serviceArns = listServiceArnsOfCluster(awsConnectorDTO, cluster, region);
    return listServicesOfCluster(awsConnectorDTO, cluster, region, serviceArns);
  }

  private List<String> listServiceArnsOfCluster(AwsConnectorDTO awsConnectorDTO, String cluster, String region) {
    String nextToken = null;
    List<String> serviceArns = newArrayList();
    do {
      ListServicesRequest listServicesRequest =
          ListServicesRequest.builder().cluster(cluster).nextToken(nextToken).build();
      ListServicesResponse listServicesResponse = ecsV2Client.listServices(
          awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), listServicesRequest, region);
      if (CollectionUtils.isNotEmpty(listServicesResponse.serviceArns())) {
        serviceArns.addAll(listServicesResponse.serviceArns());
      }
      nextToken = listServicesResponse.nextToken();
    } while (nextToken != null);
    return serviceArns;
  }

  private List<Service> listServicesOfCluster(
      AwsConnectorDTO awsConnectorDTO, String cluster, String region, List<String> serviceArns) {
    List<Service> services = newArrayList();
    int counter = 0;
    int endIndex;
    // We can only use this api to describe 10 services at a time.
    while (counter < serviceArns.size()) {
      endIndex = Integer.min(counter + DESCRIBE_SERVICE_API_MAX_LIMIT, serviceArns.size());
      DescribeServicesRequest describeServicesRequest =
          DescribeServicesRequest.builder().cluster(cluster).services(serviceArns.subList(counter, endIndex)).build();
      DescribeServicesResponse describeServicesResponse = ecsV2Client.describeServices(
          awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), describeServicesRequest, region);
      if (CollectionUtils.isNotEmpty(describeServicesResponse.services())) {
        services.addAll(describeServicesResponse.services());
      }
      counter += DESCRIBE_SERVICE_API_MAX_LIMIT;
    }
    return services;
  }

  public void deleteStaleServicesWithZeroInstance(
      List<Service> staleServices, LogCallback logCallback, EcsInfraConfig ecsInfraConfig) {
    // get stale services with zero instances
    List<Service> staleServicesHavingZeroInstances =
        ecsCommandTaskHelper.fetchServicesHavingZeroInstance(staleServices);
    if (CollectionUtils.isNotEmpty(staleServicesHavingZeroInstances)) {
      logCallback.saveExecutionLog(format("Deleting stale services with zero running instance..%n%n"), LogLevel.INFO);
    }
    staleServicesHavingZeroInstances.forEach(service -> {
      logCallback.saveExecutionLog(
          format("Deleting stale service: %s with zero running instance..", service.serviceName()), LogLevel.INFO);

      // deleting service
      deleteService(
          service.serviceName(), service.clusterArn(), ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
      logCallback.saveExecutionLog(
          format("Deleted stale service: %s with zero running instance..", service.serviceName()), LogLevel.INFO);
    });
  }

  public void createService(CreateServiceRequest createServiceRequest, EcsInfraConfig ecsInfraConfig,
      LogCallback logCallback, long timeoutInMillis) {
    logCallback.saveExecutionLog(format("Creating Service %s with task definition %s and desired count %s %n",
                                     createServiceRequest.serviceName(), createServiceRequest.taskDefinition(),
                                     createServiceRequest.desiredCount()),
        LogLevel.INFO);
    CreateServiceResponse createServiceResponse =
        createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    List<ServiceEvent> eventsAlreadyProcessed = new ArrayList<>(createServiceResponse.service().events());

    ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
        createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), timeoutInMillis, eventsAlreadyProcessed);

    logCallback.saveExecutionLog(format("Created Service %s with Arn %s %n", createServiceRequest.serviceName(),
                                     createServiceResponse.service().serviceArn()),
        LogLevel.INFO);
  }

  public void downsizeStaleServicesHavingRunningInstances(
      List<Service> staleServices, LogCallback logCallback, EcsInfraConfig ecsInfraConfig, long timeoutInMillis) {
    // get stale services with running instances
    List<Service> staleServicesHavingRunningInstances =
        ecsCommandTaskHelper.fetchServicesHavingRunningInstance(staleServices);
    if (CollectionUtils.isNotEmpty(staleServicesHavingRunningInstances)) {
      logCallback.saveExecutionLog(format("Downsizing stale services with running instance..%n%n"), LogLevel.INFO);
    }
    staleServicesHavingRunningInstances.forEach(service -> {
      logCallback.saveExecutionLog(format("Downsize stale service: %s with %s running instance..",
                                       service.serviceName(), service.desiredCount()),
          LogLevel.INFO);

      // downsize stale services
      downsizeService(service, ecsInfraConfig, logCallback, timeoutInMillis);
    });
  }

  public void downsizeService(
      Service service, EcsInfraConfig ecsInfraConfig, LogCallback logCallback, long timeoutInMillis) {
    if (service.desiredCount() == 0) {
      logCallback.saveExecutionLog(
          format("service: %s already stays at zero instances, ignoring downsize", service.serviceName()),
          LogLevel.INFO);
      return;
    }
    if (isServiceActive(service)) {
      UpdateServiceRequest updateServiceRequest = UpdateServiceRequest.builder()
                                                      .service(service.serviceName())
                                                      .cluster(ecsInfraConfig.getCluster())
                                                      .desiredCount(0)
                                                      .build();
      // updating desired count
      UpdateServiceResponse updateServiceResponse =
          ecsV2Client.updateService(awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO()),
              updateServiceRequest, ecsInfraConfig.getRegion());

      List<ServiceEvent> eventsAlreadyProcessed = new ArrayList<>(updateServiceResponse.service().events());

      // steady state check to reach stable state
      ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(),
          service.serviceName(), ecsInfraConfig.getRegion(), timeoutInMillis, eventsAlreadyProcessed);
    }
    logCallback.saveExecutionLog(
        format("service: %s is not active, ignoring downsize", service.serviceName()), LogLevel.INFO);
  }

  public List<Service> fetchServicesWithPrefix(List<Service> services, String servicePrefix) {
    return services.stream().filter(service -> matchWithRegex(service.serviceName(), servicePrefix)).collect(toList());
  }

  public Service fetchServiceWithName(List<Service> services, String serviceName) {
    return services.stream().filter(service -> service.serviceName().equals(serviceName)).findFirst().orElse(null);
  }

  public List<Service> fetchStaleServices(List<Service> services, List<String> activeServices) {
    return services.stream().filter(service -> !activeServices.contains(service.serviceName())).collect(toList());
  }

  private List<Service> fetchServicesHavingZeroInstance(List<Service> staleServices) {
    return staleServices.stream().filter(service -> !isServiceRunning(service)).collect(toList());
  }

  public List<Service> fetchServicesHavingRunningInstance(List<Service> staleServices) {
    return staleServices.stream().filter(this::isServiceRunning).collect(toList());
  }

  public Optional<Service> getLatestRunningService(List<Service> services) {
    List<Service> runningServices =
        services.stream()
            .filter(this::isServiceRunning)
            .sorted(comparingInt(service -> EcsUtils.getRevisionFromServiceName(service.serviceName())))
            .collect(toList());
    if (CollectionUtils.isEmpty(runningServices)) {
      return Optional.empty();
    }
    return Optional.of(runningServices.get(runningServices.size() - 1));
  }

  public int getNextRevisionForDeployment(List<Service> services) {
    List<Service> orderedServices =
        services.stream()
            .sorted(comparingInt(service -> EcsUtils.getRevisionFromServiceName(service.serviceName())))
            .collect(toList());
    if (CollectionUtils.isEmpty(orderedServices)) {
      return SERVICE_REVISION_FOR_FIRST_DEPLOYMENT;
    }

    int latestServiceRevision =
        EcsUtils.getRevisionFromServiceName(orderedServices.get(orderedServices.size() - 1).serviceName());

    return Integer.max(latestServiceRevision + 1, 1);
  }

  private boolean isServiceRunning(Service service) {
    return service != null && service.desiredCount() > 0;
  }

  private boolean matchWithRegex(String serviceName, String servicePrefix) {
    String pattern = "^" + servicePrefix + "[0-9]+$";
    return Pattern.compile(pattern).matcher(serviceName).matches();
  }

  public void deregisterScalableTargets(
      AwsConnectorDTO awsConnectorDTO, String serviceName, String cluster, String region, LogCallback logCallback) {
    DescribeScalableTargetsRequest describeScalableTargetsRequest =
        DescribeScalableTargetsRequest.builder()
            .maxResults(100)
            .serviceNamespace(ServiceNamespace.ECS)
            .resourceIds(format("service/%s/%s", cluster, serviceName))
            .build();

    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    DescribeScalableTargetsResponse describeScalableTargetsResponse =
        ecsV2Client.listScalableTargets(awsInternalConfig, describeScalableTargetsRequest, region);

    if (describeScalableTargetsResponse != null
        && CollectionUtils.isNotEmpty(describeScalableTargetsResponse.scalableTargets())) {
      logCallback.saveExecutionLog(
          format("Deregistering Scalable Targets from service %s..%n", serviceName), LogLevel.INFO);
      describeScalableTargetsResponse.scalableTargets().forEach(scalableTarget -> {
        DeregisterScalableTargetRequest deregisterScalableTargetRequest =
            DeregisterScalableTargetRequest.builder()
                .scalableDimension(scalableTarget.scalableDimension())
                .serviceNamespace(scalableTarget.serviceNamespace())
                .resourceId(format("service/%s/%s", cluster, serviceName))
                .build();

        ecsV2Client.deregisterScalableTarget(awsInternalConfig, deregisterScalableTargetRequest, region);
        logCallback.saveExecutionLog(
            format("Deregistered Scalable Target with Scalable Dimension %s from service %s.. %n",
                scalableTarget.scalableDimension().toString(), serviceName),
            LogLevel.INFO);
      });
      logCallback.saveExecutionLog(
          format("Deregistered Scalable Targets from service %s %n%n", serviceName), LogLevel.INFO);
    }
  }

  public void attachScalingPolicies(List<String> ecsScalingPolicyManifestContentList, AwsConnectorDTO awsConnectorDTO,
      String serviceName, String cluster, String region, LogCallback logCallback) {
    if (CollectionUtils.isNotEmpty(ecsScalingPolicyManifestContentList)) {
      logCallback.saveExecutionLog(
          format("%nAttaching Scaling Policies to service %s.. %n", serviceName), LogLevel.INFO);

      ecsScalingPolicyManifestContentList.forEach(ecsScalingPolicyManifestContent -> {
        AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);

        PutScalingPolicyRequest.Builder putScalingPolicyRequestBuilder =
            parseYamlAsObject(ecsScalingPolicyManifestContent, PutScalingPolicyRequest.serializableBuilderClass());
        PutScalingPolicyRequest putScalingPolicyRequest =
            putScalingPolicyRequestBuilder.resourceId(format("service/%s/%s", cluster, serviceName)).build();

        ecsV2Client.attachScalingPolicy(awsInternalConfig, putScalingPolicyRequest, region);
        logCallback.saveExecutionLog(
            format("Attached Scaling Policy %s to service %s %n", putScalingPolicyRequest.policyName(), serviceName),
            LogLevel.INFO);
      });

      logCallback.saveExecutionLog(format("Attached Scaling Policies to service %s %n", serviceName), LogLevel.INFO);
    }
  }

  public void registerScalableTargets(List<String> ecsScalableTargetManifestContentList,
      AwsConnectorDTO awsConnectorDTO, String serviceName, String cluster, String region, LogCallback logCallback) {
    if (CollectionUtils.isNotEmpty(ecsScalableTargetManifestContentList)) {
      logCallback.saveExecutionLog(
          format("%nRegistering Scalable Targets to service %s..%n", serviceName), LogLevel.INFO);

      ecsScalableTargetManifestContentList.forEach(ecsScalableTargetManifestContent -> {
        AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);

        RegisterScalableTargetRequest.Builder registerScalableTargetRequestBuilder = parseYamlAsObject(
            ecsScalableTargetManifestContent, RegisterScalableTargetRequest.serializableBuilderClass());

        RegisterScalableTargetRequest registerScalableTargetRequest =
            registerScalableTargetRequestBuilder.resourceId(format("service/%s/%s", cluster, serviceName)).build();

        ecsV2Client.registerScalableTarget(awsInternalConfig, registerScalableTargetRequest, region);
        logCallback.saveExecutionLog(format("Registered Scalable Target with Scalable Dimension %s to service %s %n",
                                         registerScalableTargetRequest.scalableDimension(), serviceName),
            LogLevel.INFO);
      });

      logCallback.saveExecutionLog(format("Registered Scalable Targets to service %s %n", serviceName), LogLevel.INFO);
    }
  }

  public <T> T parseYamlAsObject(String yaml, Class<T> tClass) {
    T object;
    try {
      object = yamlUtils.read(yaml, tClass);
    } catch (Exception e) {
      // Set default
      String schema = tClass.getName();

      if (tClass == RegisterTaskDefinitionRequest.serializableBuilderClass()) {
        schema = "ECS Task Definition";
      } else if (tClass == CreateServiceRequest.serializableBuilderClass()) {
        schema = "ECS Service Definition";
      } else if (tClass == RegisterScalableTargetRequest.serializableBuilderClass()) {
        schema = "ECS Scalable Target";
      } else if (tClass == PutScalingPolicyRequest.serializableBuilderClass()) {
        schema = "ECS Scaling Policy";
      } else if (tClass == RunTaskRequest.serializableBuilderClass()) {
        schema = "ECS Run Task Definition";
      }

      throw NestedExceptionUtils.hintWithExplanationException(
          format("Please check yaml configured matches schema %s", schema),
          format(
              "Error while parsing yaml %s. Its expected to be matching %s schema. Please check Harness documentation https://docs.harness.io for more details",
              yaml, schema),
          e);
    }
    return object;
  }

  public void createOrUpdateService(CreateServiceRequest createServiceRequest,
      List<String> ecsScalableTargetManifestContentList, List<String> ecsScalingPolicyManifestContentList,
      EcsInfraConfig ecsInfraConfig, LogCallback logCallback, long timeoutInMillis,
      boolean sameAsAlreadyRunningInstances, boolean forceNewDeployment) {
    if (StringUtils.isEmpty(createServiceRequest.serviceName())) {
      throw NestedExceptionUtils.hintWithExplanationException(
          format(
              "Please check if ECS service name is configured properly in ECS Service Definition Manifest in Harness Service."),
          format("ECS service name is not configured properly in ECS Service Definition. It is found to be empty."),
          new InvalidRequestException("ECS service name is empty."));
    }
    // if service exists create service, otherwise update service
    Optional<Service> optionalService = describeService(createServiceRequest.cluster(),
        createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    if (!(optionalService.isPresent() && isServiceActive(optionalService.get()))) {
      if (optionalService.isPresent() && isServiceDraining(optionalService.get())) {
        logCallback.saveExecutionLog(format("An existing Service with name %s draining, waiting for it to reach "
                                             + "inactive state %n",
                                         createServiceRequest.serviceName()),
            LogLevel.INFO);
        ecsServiceInactiveStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
            createServiceRequest.serviceName(), ecsInfraConfig.getRegion(),
            (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));
        logCallback.saveExecutionLog(
            format("An existed Service with name %s reached inactive state %n", createServiceRequest.serviceName()),
            LogLevel.INFO);
      }

      logCallback.saveExecutionLog(format("Creating Service %s with task definition %s and desired count %s %n",
                                       createServiceRequest.serviceName(), createServiceRequest.taskDefinition(),
                                       createServiceRequest.desiredCount()),
          LogLevel.INFO);

      CreateServiceResponse createServiceResponse =
          createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

      List<ServiceEvent> eventsAlreadyProcessed = new ArrayList<>(createServiceResponse.service().events());

      ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
          createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), timeoutInMillis, eventsAlreadyProcessed);

      logCallback.saveExecutionLog(format("Created Service %s with Arn %s %n", createServiceRequest.serviceName(),
                                       createServiceResponse.service().serviceArn()),
          LogLevel.INFO);

      registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
          createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
          logCallback);

      attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
          createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
          logCallback);

    } else {
      Service service = optionalService.get();
      deleteScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(), service.serviceName(), ecsInfraConfig.getCluster(),
          ecsInfraConfig.getRegion(), logCallback);
      deregisterScalableTargets(ecsInfraConfig.getAwsConnectorDTO(), service.serviceName(), ecsInfraConfig.getCluster(),
          ecsInfraConfig.getRegion(), logCallback);

      UpdateServiceRequest updateServiceRequest =
          EcsMapper.createServiceRequestToUpdateServiceRequest(createServiceRequest, forceNewDeployment);

      if (isSameAsCurrentService(updateServiceRequest, optionalService, ecsInfraConfig)) {
        logCallback.saveExecutionLog(
            color(format("Service %s is already up to date", optionalService.get().serviceName()), White, Bold),
            LogLevel.INFO);
      } else {
        // same as already running instances
        if (sameAsAlreadyRunningInstances) {
          updateServiceRequest = updateServiceRequest.toBuilder().desiredCount(null).build();
        }

        logCallback.saveExecutionLog(
            format("Updating Service %s with task definition %s and desired count %s %n",
                updateServiceRequest.service(), updateServiceRequest.taskDefinition(),
                updateServiceRequest.desiredCount() == null ? "same as existing" : updateServiceRequest.desiredCount()),
            LogLevel.INFO);
        UpdateServiceResponse updateServiceResponse =
            updateService(updateServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

        List<ServiceEvent> eventsAlreadyProcessed = new ArrayList<>(updateServiceResponse.service().events());

        ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
            createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), timeoutInMillis, eventsAlreadyProcessed);

        logCallback.saveExecutionLog(format("Updated Service %s with Arn %s %n", updateServiceRequest.service(),
                                         updateServiceResponse.service().serviceArn()),
            LogLevel.INFO);
      }
      registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
          service.serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);

      attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
          service.serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);
    }
  }

  private boolean isSameAsCurrentService(
      UpdateServiceRequest updateServiceRequest, Optional<Service> optionalService, EcsInfraConfig ecsInfraConfig) {
    Service service = optionalService.get();
    CreateServiceRequest.Builder createServiceRequestBuilder =
        EcsMapper.createCreateServiceRequestBuilderFromService(service);
    createServiceRequestBuilder.cluster(ecsInfraConfig.getCluster());
    UpdateServiceRequest currentUpdateServiceRequest =
        EcsMapper.createServiceRequestToUpdateServiceRequest(createServiceRequestBuilder.build(), false);
    return currentUpdateServiceRequest.equals(updateServiceRequest);
  }

  private boolean notAllDesiredTasksRunning(AwsInternalConfig awsConfig, String clusterName, String serviceName,
      String region, List<ServiceEvent> eventsAlreadyProcessed, LogCallback logCallback) {
    DescribeServicesResponse describeServicesResponse =
        ecsV2Client.describeService(awsConfig, clusterName, serviceName, region);
    Service service = describeServicesResponse.services().get(0);

    log.info("Waiting for pending tasks to finish. {}/{} running ...", service.runningCount(), service.desiredCount());

    logCallback.saveExecutionLog(format("Waiting for pending tasks to finish. %s/%s running ...",
                                     service.runningCount(), service.desiredCount()),
        LogLevel.INFO);

    printAwsEvent(service, eventsAlreadyProcessed, logCallback);

    return !Integer.valueOf(service.desiredCount()).equals(service.runningCount());
  }

  private void printAwsEvent(Service service, List<ServiceEvent> eventsAlreadyProcessed, LogCallback logCallback) {
    List<ServiceEvent> events = new ArrayList<>();
    events.addAll(service.events());

    Set<String> eventIdsProcessed = eventsAlreadyProcessed.stream().map(ServiceEvent::id).collect(toSet());
    events = events.stream().filter(event -> !eventIdsProcessed.contains(event.id())).collect(toList());

    for (int index = events.size() - 1; index >= 0; index--) {
      logCallback.saveExecutionLog(color(new StringBuilder()
                                             .append("# AWS Event: ")
                                             .append(Timestamp.from(events.get(index).createdAt()))
                                             .append(" ")
                                             .append(events.get(index).message())
                                             .toString(),
          Yellow, Bold));
    }

    eventsAlreadyProcessed.addAll(events);
  }

  private void printAwsEcsDeployments(Service service, LogCallback logCallback) {
    logCallback.saveExecutionLog(format("%nCurrent Deployment Status"));
    logCallback.saveExecutionLog(format("Service %s Overall Status DesiredCount=%s PendingCount=%s RunningCount=%s ",
        service.serviceName(), service.desiredCount(), service.pendingCount(), service.runningCount()));
    logCallback.saveExecutionLog("Service Deployments Status");
    for (Deployment deployment : service.deployments()) {
      logCallback.saveExecutionLog(color(getDeploymentDetails(deployment), White, Bold));
    }
  }

  private String getDeploymentDetails(Deployment deployment) {
    return format("Deployment Id=%s, Status=%s, "
            + "TaskDefinition=%s, DesiredCount=%s, PendingCount=%s, RunningCount=%s, FailedTasks=%s, "
            + "RolloutState=%s",
        deployment.id(), deployment.status(), deployment.taskDefinition(), deployment.desiredCount(),
        deployment.pendingCount(), deployment.runningCount(), deployment.failedTasks(), deployment.rolloutState());
  }

  public void createCanaryService(CreateServiceRequest createServiceRequest,
      List<String> ecsScalableTargetManifestContentList, List<String> ecsScalingPolicyManifestContentList,
      EcsInfraConfig ecsInfraConfig, LogCallback logCallback, long timeoutInMillis) {
    // if service exists create service, otherwise update service
    Optional<Service> optionalService = describeService(createServiceRequest.cluster(),
        createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    if (optionalService.isPresent() && isServiceActive(optionalService.get())) { // if service exists delete it

      Service service = optionalService.get();

      logCallback.saveExecutionLog(
          format("Deleting existing Service with name %s %n", createServiceRequest.serviceName()), LogLevel.INFO);

      deleteService(
          service.serviceName(), service.clusterArn(), ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

      ecsServiceInactiveStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
          createServiceRequest.serviceName(), ecsInfraConfig.getRegion(),
          (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));

      logCallback.saveExecutionLog(
          format("Deleted existing Service with name %s %n", createServiceRequest.serviceName()), LogLevel.INFO);
    } else if (optionalService.isPresent() && isServiceDraining(optionalService.get())) {
      logCallback.saveExecutionLog(format("An existing Service with name %s draining, waiting for it to reach "
                                           + "inactive state %n",
                                       createServiceRequest.serviceName()),
          LogLevel.INFO);
      ecsServiceInactiveStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
          createServiceRequest.serviceName(), ecsInfraConfig.getRegion(),
          (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));
      logCallback.saveExecutionLog(
          format("An existed Service with name %s reached inactive state %n", createServiceRequest.serviceName()),
          LogLevel.INFO);
    }

    logCallback.saveExecutionLog(format("Creating Service %s with task definition %s and desired count %s %n",
                                     createServiceRequest.serviceName(), createServiceRequest.taskDefinition(),
                                     createServiceRequest.desiredCount()),
        LogLevel.INFO);
    CreateServiceResponse createServiceResponse =
        createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    List<ServiceEvent> eventsAlreadyProcessed = new ArrayList<>(createServiceResponse.service().events());

    ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
        createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), timeoutInMillis, eventsAlreadyProcessed);

    logCallback.saveExecutionLog(format("Created Service %s with Arn %s %n", createServiceRequest.serviceName(),
                                     createServiceResponse.service().serviceArn()),
        LogLevel.INFO);

    registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
        createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
        logCallback);

    attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
        createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
        logCallback);
  }

  public List<String> getScalableTargetsAsString(
      LogCallback prepareRollbackDataLogCallback, String serviceName, Service service, EcsInfraConfig ecsInfraConfig) {
    List<String> registerScalableTargetRequestBuilderStrings = null;
    prepareRollbackDataLogCallback.saveExecutionLog(
        format("Fetching Scalable Target Details for Service %s..", serviceName), LogLevel.INFO);
    DescribeScalableTargetsResponse describeScalableTargetsResponse =
        listScalableTargets(ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(), service.serviceName(),
            ecsInfraConfig.getRegion());
    if (describeScalableTargetsResponse != null
        && CollectionUtils.isNotEmpty(describeScalableTargetsResponse.scalableTargets())) {
      registerScalableTargetRequestBuilderStrings =
          describeScalableTargetsResponse.scalableTargets()
              .stream()
              .map(scalableTarget -> {
                try {
                  return EcsMapper.createRegisterScalableTargetRequestFromScalableTarget(scalableTarget);
                } catch (Exception e) {
                  String message = "Error while creating register scalable target request json from scalable target";
                  log.error(message);
                  throw new InvalidRequestException(message, e);
                }
              })
              .collect(Collectors.toList());
      prepareRollbackDataLogCallback.saveExecutionLog(
          format("Fetched Scalable Target Details for Service %s", serviceName), LogLevel.INFO);
    } else {
      prepareRollbackDataLogCallback.saveExecutionLog(
          format("Didn't find Scalable Target Details for Service %s", serviceName), LogLevel.INFO);
    }
    return registerScalableTargetRequestBuilderStrings;
  }

  public List<String> getScalingPoliciesAsString(
      LogCallback prepareRollbackDataLogCallback, String serviceName, Service service, EcsInfraConfig ecsInfraConfig) {
    List<String> registerScalingPolicyRequestBuilderStrings = null;
    prepareRollbackDataLogCallback.saveExecutionLog(
        format("Fetching Scaling Policy Details for Service %s..", serviceName), LogLevel.INFO);
    DescribeScalingPoliciesResponse describeScalingPoliciesResponse =
        listScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(), service.serviceName(),
            ecsInfraConfig.getRegion());

    if (describeScalingPoliciesResponse != null
        && CollectionUtils.isNotEmpty(describeScalingPoliciesResponse.scalingPolicies())) {
      registerScalingPolicyRequestBuilderStrings =
          describeScalingPoliciesResponse.scalingPolicies()
              .stream()
              .map(scalingPolicy -> {
                try {
                  return EcsMapper.createPutScalingPolicyRequestFromScalingPolicy(scalingPolicy);
                } catch (JsonProcessingException e) {
                  String message = "Error while creating put scaling policy request json from scaling policy";
                  log.error(message);
                  throw new InvalidRequestException(message, e);
                }
              })
              .collect(Collectors.toList());
      prepareRollbackDataLogCallback.saveExecutionLog(
          format("Fetched Scaling Policy Details for Service %s", serviceName), LogLevel.INFO);
    } else {
      prepareRollbackDataLogCallback.saveExecutionLog(
          format("Didn't find Scaling Policy Details for Service %s", serviceName), LogLevel.INFO);
    }
    return registerScalingPolicyRequestBuilderStrings;
  }

  public String createStageService(String ecsServiceDefinitionManifestContent,
      List<String> ecsScalableTargetManifestContentList, List<String> ecsScalingPolicyManifestContentList,
      EcsInfraConfig ecsInfraConfig, LogCallback logCallback, long timeoutInMillis, String targetGroupArnKey,
      String taskDefinitionArn, String targetGroupArn, boolean isSameAsAlreadyRunningInstances,
      boolean removeAutoScalingFromBlueService, boolean updateGreenService) {
    // render target group arn value in its expression in ecs service definition yaml
    ecsServiceDefinitionManifestContent =
        updateTargetGroupArn(ecsServiceDefinitionManifestContent, targetGroupArn, targetGroupArnKey);

    CreateServiceRequest createServiceRequest =
        parseYamlAsObject(ecsServiceDefinitionManifestContent, CreateServiceRequest.serializableBuilderClass()).build();

    // get stage service name
    String stageServiceName =
        getNonBlueVersionServiceName(trim(createServiceRequest.serviceName() + DELIMITER), ecsInfraConfig);

    Optional<Service> optionalService = describeService(
        ecsInfraConfig.getCluster(), stageServiceName, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    boolean greenServiceExist = optionalService.isPresent();

    // delete the existing non-blue version
    if (optionalService.isPresent() && isServiceActive(optionalService.get()) && !updateGreenService) {
      Service service = optionalService.get();
      logCallback.saveExecutionLog(
          format("Deleting existing non-blue version Service: %s", service.serviceName()), LogLevel.INFO);

      deleteService(
          service.serviceName(), service.clusterArn(), ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

      ecsServiceInactiveStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(),
          service.serviceName(), ecsInfraConfig.getRegion(), (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));
      logCallback.saveExecutionLog(
          format("Deleted non-blue version Service: %s %n%n", service.serviceName()), LogLevel.INFO);
      greenServiceExist = false;
    } else if (optionalService.isPresent() && isServiceDraining(optionalService.get())) {
      logCallback.saveExecutionLog(
          format("An existing non-blue version Service with name %s draining, waiting for it to reach "
                  + "inactive state %n",
              stageServiceName),
          LogLevel.INFO);
      ecsServiceInactiveStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(),
          stageServiceName, ecsInfraConfig.getRegion(), (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));
      logCallback.saveExecutionLog(
          format("An existed non-blue version Service with name %s reached inactive state %n", stageServiceName),
          LogLevel.INFO);
      greenServiceExist = false;
    }

    // add green tag in create service request
    CreateServiceRequest.Builder createServiceRequestBuilder =
        addTagInCreateServiceRequest(createServiceRequest, BG_GREEN);

    if (isSameAsAlreadyRunningInstances) {
      // fetch blue service
      Optional<Service> blueServiceOptional =
          fetchBGService(trim(createServiceRequest.serviceName() + DELIMITER), ecsInfraConfig, BG_BLUE);
      // fetch desired count of blue service and set in green service
      blueServiceOptional.ifPresent(service -> createServiceRequestBuilder.desiredCount(service.desiredCount()));
    }

    if (removeAutoScalingFromBlueService) {
      // fetch blue service
      Optional<Service> blueServiceOptional =
          fetchBGService(trim(createServiceRequest.serviceName() + DELIMITER), ecsInfraConfig, BG_BLUE);

      // remove auto scaling from blue service
      blueServiceOptional.ifPresent(
          service -> deleteBGServiceAutoScaling(logCallback, "blue", service.serviceName(), ecsInfraConfig));
    }

    // update service name, cluster and task definition
    createServiceRequest = createServiceRequestBuilder.serviceName(stageServiceName)
                               .cluster(ecsInfraConfig.getCluster())
                               .taskDefinition(taskDefinitionArn)
                               .build();

    if (updateGreenService && greenServiceExist) {
      deleteScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.serviceName(),
          ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);
      deregisterScalableTargets(ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.serviceName(),
          ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);

      logCallback.saveExecutionLog(format("Updating Stage Service %s with task definition %s and desired count %s %n",
                                       createServiceRequest.serviceName(), createServiceRequest.taskDefinition(),
                                       createServiceRequest.desiredCount()),
          LogLevel.INFO);

      UpdateServiceRequest updateServiceRequest =
          EcsMapper.createServiceRequestToUpdateServiceRequest(createServiceRequest, true);

      UpdateServiceResponse updateServiceResponse =
          updateService(updateServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

      List<ServiceEvent> eventsAlreadyProcessed = new ArrayList<>(updateServiceResponse.service().events());

      ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
          createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), timeoutInMillis, eventsAlreadyProcessed);

      logCallback.saveExecutionLog(
          format("Updated Stage Service %s with Arn %s %n%n", createServiceRequest.serviceName(),
              updateServiceResponse.service().serviceArn()),
          LogLevel.INFO);
    } else {
      logCallback.saveExecutionLog(format("Creating Stage Service %s with task definition %s and desired count %s %n",
                                       createServiceRequest.serviceName(), createServiceRequest.taskDefinition(),
                                       createServiceRequest.desiredCount()),
          LogLevel.INFO);

      CreateServiceResponse createServiceResponse =
          createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

      List<ServiceEvent> eventsAlreadyProcessed = new ArrayList<>(createServiceResponse.service().events());

      ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
          createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), timeoutInMillis, eventsAlreadyProcessed);

      logCallback.saveExecutionLog(
          format("Created Stage Service %s with Arn %s %n%n", createServiceRequest.serviceName(),
              createServiceResponse.service().serviceArn()),
          LogLevel.INFO);
    }

    logCallback.saveExecutionLog(format("Target Group with Arn: %s is associated with Stage Service %s", targetGroupArn,
                                     createServiceRequest.serviceName()),
        LogLevel.INFO);

    logCallback.saveExecutionLog(format("Tag: [%s, %s] is associated with Stage Service %s", BG_VERSION, BG_GREEN,
                                     createServiceRequest.serviceName()),
        LogLevel.INFO);

    registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
        createServiceRequest.serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);

    attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
        createServiceRequest.serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);
    return createServiceRequest.serviceName();
  }

  private void deleteBGServiceAutoScaling(
      LogCallback logCallback, String version, String serviceName, EcsInfraConfig ecsInfraConfig) {
    logCallback.saveExecutionLog(format("Removing %s service:  %s scaling policies", version, serviceName));
    // deleting scaling policies for service
    deleteScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(), serviceName, ecsInfraConfig.getCluster(),
        ecsInfraConfig.getRegion(), logCallback);

    logCallback.saveExecutionLog(format("Removing %s service:  %s scalable targets", version, serviceName));
    // de-registering scalable target for service
    deregisterScalableTargets(ecsInfraConfig.getAwsConnectorDTO(), serviceName, ecsInfraConfig.getCluster(),
        ecsInfraConfig.getRegion(), logCallback);
  }

  public void updateOldService(EcsBlueGreenRollbackRequest ecsBlueGreenRollbackRequest,
      AwsInternalConfig awsInternalConfig, LogCallback logCallback, long timeoutInMillis) {
    EcsInfraConfig ecsInfraConfig = ecsBlueGreenRollbackRequest.getEcsInfraConfig();
    // Describe old service and get service details
    Optional<Service> oldOptionalService =
        describeService(ecsInfraConfig.getCluster(), ecsBlueGreenRollbackRequest.getOldServiceName(),
            ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
    List<ServiceEvent> eventsAlreadyProcessed = newArrayList();
    if (oldOptionalService.isPresent() && isServiceActive(oldOptionalService.get())) {
      Service oldService = oldOptionalService.get();
      CreateServiceRequest createServiceRequest =
          parseYamlAsObject(ecsBlueGreenRollbackRequest.getOldServiceCreateRequestBuilderString(),
              CreateServiceRequest.serializableBuilderClass())
              .build();

      // update desired count of old service to its maximum desired count if it is not less than that
      if (oldService.desiredCount() < (createServiceRequest.desiredCount())) {
        logCallback.saveExecutionLog(
            format("Updating Old Service %s with task definition %s and desired count %s", oldService.serviceName(),
                oldService.taskDefinition(), createServiceRequest.desiredCount()),
            LogLevel.INFO);
        Service service = updateDesiredCount(ecsBlueGreenRollbackRequest.getOldServiceName(), ecsInfraConfig,
            awsInternalConfig, createServiceRequest.desiredCount())
                              .service();
        eventsAlreadyProcessed = new ArrayList<>(service.events());
      }
    } else {
      CreateServiceRequest createServiceRequest =
          parseYamlAsObject(ecsBlueGreenRollbackRequest.getOldServiceCreateRequestBuilderString(),
              CreateServiceRequest.serializableBuilderClass())
              .build();

      if (oldOptionalService.isPresent() && isServiceDraining(oldOptionalService.get())) {
        logCallback.saveExecutionLog(format("An existing old Service with name %s draining, waiting for it to reach "
                                             + "inactive state %n",
                                         ecsBlueGreenRollbackRequest.getOldServiceName()),
            LogLevel.INFO);
        ecsServiceInactiveStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(),
            ecsBlueGreenRollbackRequest.getOldServiceName(), ecsInfraConfig.getRegion(),
            (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));
        logCallback.saveExecutionLog(format("An existed old Service with name %s reached inactive state %n",
                                         ecsBlueGreenRollbackRequest.getOldServiceName()),
            LogLevel.INFO);
      }

      logCallback.saveExecutionLog(format("Creating Old Service %s with task definition %s and desired count %s",
                                       createServiceRequest.serviceName(), createServiceRequest.taskDefinition(),
                                       createServiceRequest.desiredCount()),
          LogLevel.INFO);

      CreateServiceResponse createServiceResponse =
          createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
      eventsAlreadyProcessed = new ArrayList<>(createServiceResponse.service().events());
    }

    // steady state check to reach stable state
    ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(),
        ecsBlueGreenRollbackRequest.getEcsInfraConfig().getCluster(), ecsBlueGreenRollbackRequest.getOldServiceName(),
        ecsBlueGreenRollbackRequest.getEcsInfraConfig().getRegion(), timeoutInMillis, eventsAlreadyProcessed);

    logCallback.saveExecutionLog(
        format("Updating old service:  %s scalable targets", ecsBlueGreenRollbackRequest.getOldServiceName()));

    // register scalable target
    registerScalableTargets(ecsBlueGreenRollbackRequest.getOldServiceScalableTargetManifestContentList(),
        ecsInfraConfig.getAwsConnectorDTO(), ecsBlueGreenRollbackRequest.getOldServiceName(),
        ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);

    logCallback.saveExecutionLog(
        format("Updating old service:  %s scaling policies", ecsBlueGreenRollbackRequest.getOldServiceName()));

    // attach scale policies
    attachScalingPolicies(ecsBlueGreenRollbackRequest.getOldServiceScalingPolicyManifestContentList(),
        ecsInfraConfig.getAwsConnectorDTO(), ecsBlueGreenRollbackRequest.getOldServiceName(),
        ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);
  }

  public void swapTargetGroups(EcsInfraConfig ecsInfraConfig, LogCallback logCallback,
      EcsLoadBalancerConfig ecsLoadBalancerConfig, AwsInternalConfig awsInternalConfig) {
    logCallback.saveExecutionLog(
        format("Modifying ELB Prod Listener to Forward requests to Target group associated with new Service%n,"
                + "TargetGroup: %s",
            ecsLoadBalancerConfig.getStageTargetGroupArn()),
        LogLevel.INFO);
    // modify prod listener rule with stage target group
    modifyListenerRule(ecsInfraConfig, ecsLoadBalancerConfig.getProdListenerArn(),
        ecsLoadBalancerConfig.getProdListenerRuleArn(), ecsLoadBalancerConfig.getStageTargetGroupArn(),
        awsInternalConfig, logCallback);
    logCallback.saveExecutionLog(
        color(format("Successfully updated Prod Listener %n%n"), LogColor.White, Bold), LogLevel.INFO);

    logCallback.saveExecutionLog(
        format("Modifying ELB Stage Listener to Forward requests to Target group associated with old Service%n,"
                + "TargetGroup: %s",
            ecsLoadBalancerConfig.getProdTargetGroupArn()),
        LogLevel.INFO);
    // modify stage listener rule with prod target group
    modifyListenerRule(ecsInfraConfig, ecsLoadBalancerConfig.getStageListenerArn(),
        ecsLoadBalancerConfig.getStageListenerRuleArn(), ecsLoadBalancerConfig.getProdTargetGroupArn(),
        awsInternalConfig, logCallback);
    logCallback.saveExecutionLog(
        color(format("Successfully updated Stage Listener %n%n"), LogColor.White, Bold), LogLevel.INFO);
  }

  public void updateTag(String serviceName, EcsInfraConfig ecsInfraConfig, String value,
      AwsInternalConfig awsInternalConfig, LogCallback logCallback) {
    // Describe ecs service and get service details
    Optional<Service> optionalService = describeService(
        ecsInfraConfig.getCluster(), serviceName, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    if (optionalService.isPresent() && isServiceActive(optionalService.get())) {
      UntagResourceRequest untagResourceRequest =
          UntagResourceRequest.builder().resourceArn(optionalService.get().serviceArn()).tagKeys(BG_VERSION).build();

      logCallback.saveExecutionLog(
          format("Updating service: %s with tag: [%s, %s]", serviceName, BG_VERSION, value), LogLevel.INFO);

      // remove BG tag from service
      ecsV2Client.untagService(awsInternalConfig, untagResourceRequest, ecsInfraConfig.getRegion());
      TagResourceRequest tagResourceRequest = TagResourceRequest.builder()
                                                  .resourceArn(optionalService.get().serviceArn())
                                                  .tags(Tag.builder().key(BG_VERSION).value(value).build())
                                                  .build();

      // update BG tag to service
      ecsV2Client.tagService(awsInternalConfig, tagResourceRequest, ecsInfraConfig.getRegion());
    } else {
      throw new InvalidRequestException(format("Service: %s is not active", serviceName));
    }
  }

  public UpdateServiceResponse updateDesiredCount(
      String serviceName, EcsInfraConfig ecsInfraConfig, AwsInternalConfig awsInternalConfig, int desiredCount) {
    // Describe ecs service and get service details
    Optional<Service> optionalService = describeService(
        ecsInfraConfig.getCluster(), serviceName, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
    if (optionalService.isPresent() && isServiceActive(optionalService.get())) {
      UpdateServiceRequest updateServiceRequest = UpdateServiceRequest.builder()
                                                      .service(optionalService.get().serviceName())
                                                      .cluster(ecsInfraConfig.getCluster())
                                                      .desiredCount(desiredCount)
                                                      .build();
      // updating desired count
      return ecsV2Client.updateService(awsInternalConfig, updateServiceRequest, ecsInfraConfig.getRegion());
    }
    throw new InvalidRequestException(format("service: %s is not active, not able to update it.", serviceName));
  }

  public void modifyListenerRule(EcsInfraConfig ecsInfraConfig, String listenerArn, String listenerRuleArn,
      String targetGroupArn, AwsInternalConfig awsInternalConfig, LogCallback logCallback) {
    // check if listener rule is default one in listener
    if (checkForDefaultRule(ecsInfraConfig, listenerArn, listenerRuleArn, awsInternalConfig)) {
      logCallback.saveExecutionLog(
          format("Modifying the default Listener: %s %n with listener rule: %s %n to forward traffic to"
                  + " TargetGroup: %s",
              listenerArn, listenerRuleArn, targetGroupArn),
          LogLevel.INFO);
      // update listener with target group
      modifyDefaultListenerRule(ecsInfraConfig, listenerArn, targetGroupArn, awsInternalConfig);
    } else {
      logCallback.saveExecutionLog(format("Modifying the Listener rule: %s %n to forward traffic to"
                                           + " TargetGroup: %s",
                                       listenerRuleArn, targetGroupArn),
          LogLevel.INFO);
      // update listener rule with target group
      modifySpecificListenerRule(ecsInfraConfig, listenerRuleArn, targetGroupArn, awsInternalConfig);
    }
  }

  private void modifyDefaultListenerRule(
      EcsInfraConfig ecsInfraConfig, String listenerArn, String targetGroupArn, AwsInternalConfig awsInternalConfig) {
    ModifyListenerRequest modifyListenerRequest =
        ModifyListenerRequest.builder()
            .listenerArn(listenerArn)
            .defaultActions(Action.builder().type(ActionTypeEnum.FORWARD).targetGroupArn(targetGroupArn).build())
            .build();
    elbV2Client.modifyListener(awsInternalConfig, modifyListenerRequest, ecsInfraConfig.getRegion());
  }

  private void modifySpecificListenerRule(EcsInfraConfig ecsInfraConfig, String listenerRuleArn, String targetGroupArn,
      AwsInternalConfig awsInternalConfig) {
    ModifyRuleRequest modifyRuleRequest =
        ModifyRuleRequest.builder()
            .ruleArn(listenerRuleArn)
            .actions(Action.builder().type(ActionTypeEnum.FORWARD).targetGroupArn(targetGroupArn).build())
            .build();
    elbV2Client.modifyRule(awsInternalConfig, modifyRuleRequest, ecsInfraConfig.getRegion());
  }

  private boolean checkForDefaultRule(
      EcsInfraConfig ecsInfraConfig, String listenerArn, String listenerRuleArn, AwsInternalConfig awsInternalConfig) {
    String nextToken = null;
    do {
      DescribeRulesRequest describeRulesRequest =
          DescribeRulesRequest.builder().listenerArn(listenerArn).marker(nextToken).pageSize(10).build();
      DescribeRulesResponse describeRulesResponse =
          elbV2Client.describeRules(awsInternalConfig, describeRulesRequest, ecsInfraConfig.getRegion());
      List<Rule> currentRules = describeRulesResponse.rules();
      if (EmptyPredicate.isNotEmpty(currentRules)) {
        Optional<Rule> defaultRule = currentRules.stream().filter(Rule::isDefault).findFirst();
        if (defaultRule.isPresent() && listenerRuleArn.equalsIgnoreCase(defaultRule.get().ruleArn())) {
          return true;
        }
      }
      nextToken = describeRulesResponse.nextMarker();
    } while (nextToken != null);
    return false;
  }

  public void validateTagsInService(EcsLoadBalancerConfig loadBalancerConfig, String servicePrefix,
      EcsInfraConfig infraConfig, LogCallback logCallback) {
    String firstVersionServiceName = servicePrefix + 1;
    String secondVersionServiceName = servicePrefix + 2;
    Optional<Service> firstService = describeService(
        infraConfig.getCluster(), firstVersionServiceName, infraConfig.getRegion(), infraConfig.getAwsConnectorDTO());
    Optional<Service> secondService = describeService(
        infraConfig.getCluster(), secondVersionServiceName, infraConfig.getRegion(), infraConfig.getAwsConnectorDTO());

    List<String> firstServiceTargetGroups = null;
    List<String> secondServiceTargetGroups = null;

    if (firstService.isPresent()) {
      firstServiceTargetGroups = validateTargetGroup(loadBalancerConfig, firstService.get());
    }

    if (secondService.isPresent()) {
      secondServiceTargetGroups = validateTargetGroup(loadBalancerConfig, secondService.get());
    }

    if (CollectionUtils.isEmpty(firstServiceTargetGroups) || CollectionUtils.isEmpty(secondServiceTargetGroups)) {
      return;
    } else if (!loadBalancerConfig.getProdTargetGroupArn().equals(loadBalancerConfig.getStageTargetGroupArn())) {
      if (firstServiceTargetGroups.contains(loadBalancerConfig.getProdTargetGroupArn())
          && secondServiceTargetGroups.contains(loadBalancerConfig.getProdTargetGroupArn())) {
        throwSameTargetGroupException(PROD_SERVICE, firstService.get().serviceName(), secondService.get().serviceName(),
            loadBalancerConfig.getProdTargetGroupArn());
      } else if (firstServiceTargetGroups.contains(loadBalancerConfig.getStageTargetGroupArn())
          && secondServiceTargetGroups.contains(loadBalancerConfig.getStageTargetGroupArn())) {
        throwSameTargetGroupException(STAGE_SERVICE, firstService.get().serviceName(),
            secondService.get().serviceName(), loadBalancerConfig.getStageTargetGroupArn());
      }
    }
    // check if we can update tags in invalid configuration
    updateTags(infraConfig, loadBalancerConfig, firstService.get(), secondService.get(), logCallback,
        firstServiceTargetGroups, secondServiceTargetGroups);
  }

  private void throwSameTargetGroupException(
      String serviceType, String firstService, String secondService, String targetGroup) {
    throw NestedExceptionUtils.hintWithExplanationException(
        format(BOTH_SERVICE_WITH_SAME_TARGET_GROUP_ERROR_HINT, serviceType),
        format(BOTH_SERVICE_WITH_SAME_TARGET_GROUP_ERROR_EXPLANATION, firstService, secondService, targetGroup,
            serviceType, serviceType, serviceType),
        new InvalidRequestException(INVALID_LOAD_BALANCER_CONFIG_ERROR));
  }

  private void updateTags(EcsInfraConfig infraConfig, EcsLoadBalancerConfig loadBalancerConfig, Service firstService,
      Service secondService, LogCallback logCallback, List<String> firstServiceTargetGroups,
      List<String> secondServiceTargetGroups) {
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(infraConfig.getAwsConnectorDTO());
    if (loadBalancerConfig.getProdTargetGroupArn().equals(loadBalancerConfig.getStageTargetGroupArn())) {
      if (firstService.desiredCount() > secondService.desiredCount()) {
        updateTag(firstService.serviceName(), infraConfig, BG_BLUE, awsInternalConfig, logCallback);
        updateTag(secondService.serviceName(), infraConfig, BG_GREEN, awsInternalConfig, logCallback);
      } else {
        updateTag(firstService.serviceName(), infraConfig, BG_GREEN, awsInternalConfig, logCallback);
        updateTag(secondService.serviceName(), infraConfig, BG_BLUE, awsInternalConfig, logCallback);
      }
    } else {
      updateServiceTag(infraConfig, loadBalancerConfig, firstService, logCallback, firstServiceTargetGroups);
      updateServiceTag(infraConfig, loadBalancerConfig, secondService, logCallback, secondServiceTargetGroups);
    }
  }

  private void updateServiceTag(EcsInfraConfig infraConfig, EcsLoadBalancerConfig loadBalancerConfig, Service service,
      LogCallback logCallback, List<String> targetGroups) {
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(infraConfig.getAwsConnectorDTO());
    if (targetGroups.contains(loadBalancerConfig.getProdTargetGroupArn())) {
      updateTag(service.serviceName(), infraConfig, BG_BLUE, awsInternalConfig, logCallback);
    } else if (targetGroups.contains(loadBalancerConfig.getStageTargetGroupArn())) {
      updateTag(service.serviceName(), infraConfig, BG_GREEN, awsInternalConfig, logCallback);
    }
  }

  private List<String> validateTargetGroup(EcsLoadBalancerConfig loadBalancerConfig, Service service) {
    List<LoadBalancer> loadBalancers = service.loadBalancers();
    List<String> targetGroups = newArrayList();
    if (loadBalancers != null) {
      targetGroups = loadBalancers.stream().map(LoadBalancer::targetGroupArn).collect(toList());
    }
    if (CollectionUtils.isEmpty(targetGroups)) {
      throw NestedExceptionUtils.hintWithExplanationException(INVALID_TARGET_GROUP_ERROR_HINT,
          format(NO_TARGET_GROUP_ERROR_EXPLANATION, service.serviceName()),
          new InvalidRequestException(INVALID_LOAD_BALANCER_CONFIG_ERROR));
    } else if (targetGroups.contains(loadBalancerConfig.getProdTargetGroupArn())
        && targetGroups.contains(loadBalancerConfig.getStageTargetGroupArn())
        && !loadBalancerConfig.getProdTargetGroupArn().equals(loadBalancerConfig.getStageTargetGroupArn())) {
      throw NestedExceptionUtils.hintWithExplanationException(INVALID_TARGET_GROUP_ERROR_HINT,
          format(BOTH_TARGET_GROUP_ERROR_EXPLANATION, service.serviceName(), loadBalancerConfig.getProdTargetGroupArn(),
              loadBalancerConfig.getStageTargetGroupArn()),
          new InvalidRequestException(INVALID_LOAD_BALANCER_CONFIG_ERROR));
    } else if (!targetGroups.contains(loadBalancerConfig.getProdTargetGroupArn())
        && !targetGroups.contains(loadBalancerConfig.getStageTargetGroupArn())) {
      throw NestedExceptionUtils.hintWithExplanationException(INVALID_TARGET_GROUP_ERROR_HINT,
          format(OTHER_TARGET_GROUP_ERROR_EXPLANATION, service.serviceName(), targetGroups),
          new InvalidRequestException(INVALID_LOAD_BALANCER_CONFIG_ERROR));
    }
    return targetGroups;
  }

  public Optional<String> getBlueVersionServiceName(String servicePrefix, EcsInfraConfig ecsInfraConfig) {
    // check for service suffix 1
    String firstVersionServiceName = servicePrefix + 1;

    // if service with suffix 1 is active and has blue tag, then its blue version
    if (isBlueService(firstVersionServiceName, ecsInfraConfig)) {
      return Optional.of(firstVersionServiceName);
    }

    // check for service suffix 2
    String secondVersionServiceName = servicePrefix + 2;

    // if service with suffix 2 is active and has blue tag, then its blue version
    if (isBlueService(secondVersionServiceName, ecsInfraConfig)) {
      return Optional.of(secondVersionServiceName);
    }
    return Optional.empty();
  }

  public Optional<Service> fetchBGService(String servicePrefix, EcsInfraConfig ecsInfraConfig, String tag) {
    // check service with suffix 1 is active and has given tag attached
    String firstVersionService = servicePrefix + 1;

    Optional<Service> serviceOptional = fetchBGServiceWithTag(firstVersionService, ecsInfraConfig, tag);
    if (serviceOptional.isPresent()) {
      return serviceOptional;
    }
    // check service with suffix 2 is active and has given tag attached
    String secondVersionService = servicePrefix + 2;
    return fetchBGServiceWithTag(secondVersionService, ecsInfraConfig, tag);
  }

  private boolean isBlueService(String serviceName, EcsInfraConfig ecsInfraConfig) {
    DescribeServicesRequest describeServicesRequest = DescribeServicesRequest.builder()
                                                          .services(serviceName)
                                                          .cluster(ecsInfraConfig.getCluster())
                                                          .include(ServiceField.TAGS)
                                                          .build();
    DescribeServicesResponse describeServicesResponse =
        ecsV2Client.describeServices(awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO()),
            describeServicesRequest, ecsInfraConfig.getRegion());
    if (CollectionUtils.isNotEmpty(describeServicesResponse.services())) {
      Service service = describeServicesResponse.services().get(0);
      if (isServiceActive(service) && isServiceBGVersion(service.tags(), BG_BLUE)) {
        return true;
      }
    }
    return false;
  }

  private Optional<Service> fetchBGServiceWithTag(String serviceName, EcsInfraConfig ecsInfraConfig, String tag) {
    DescribeServicesRequest describeServicesRequest = DescribeServicesRequest.builder()
                                                          .services(serviceName)
                                                          .cluster(ecsInfraConfig.getCluster())
                                                          .include(ServiceField.TAGS)
                                                          .build();
    DescribeServicesResponse describeServicesResponse =
        ecsV2Client.describeServices(awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO()),
            describeServicesRequest, ecsInfraConfig.getRegion());
    if (CollectionUtils.isNotEmpty(describeServicesResponse.services())) {
      Service service = describeServicesResponse.services().get(0);
      if (isServiceActive(service) && isServiceBGVersion(service.tags(), tag)) {
        return Optional.ofNullable(service);
      }
    }
    return Optional.empty();
  }

  public String getNonBlueVersionServiceName(String servicePrefix, EcsInfraConfig ecsInfraConfig) {
    Optional<String> blueVersionServiceOptional = getBlueVersionServiceName(servicePrefix, ecsInfraConfig);
    String firstVersionService = servicePrefix + 1;
    if (!blueVersionServiceOptional.isPresent()) {
      return firstVersionService;
    } else if (firstVersionService.equals(blueVersionServiceOptional.get())) {
      return servicePrefix + 2;
    }
    return firstVersionService;
  }

  private CreateServiceRequest.Builder addTagInCreateServiceRequest(CreateServiceRequest serviceRequest, String tag) {
    List<Tag> tags = newArrayList();
    tags.addAll(serviceRequest.tags());

    Tag greenTag = Tag.builder().key(BG_VERSION).value(tag).build();
    tags.add(greenTag);
    return serviceRequest.toBuilder().tags(tags);
  }

  private String updateTargetGroupArn(
      String ecsServiceDefinitionManifestContent, String targetGroupArn, String targetGroupArnKey) {
    if (ecsServiceDefinitionManifestContent.contains(targetGroupArnKey)) {
      ecsServiceDefinitionManifestContent =
          ecsServiceDefinitionManifestContent.replace(targetGroupArnKey, targetGroupArn);
    } else {
      throw new InvalidRequestException(
          "target group expression: <+targetGroupArn> does not exist in service definition");
    }
    return ecsServiceDefinitionManifestContent;
  }

  public String getTargetGroupArnFromLoadBalancer(EcsInfraConfig ecsInfraConfig, String listenerArn,
      String listenerRuleArn, String loadBalancer, AwsInternalConfig awsInternalConfig) {
    DescribeLoadBalancersRequest describeLoadBalancersRequest =
        DescribeLoadBalancersRequest.builder().names(loadBalancer).build();
    DescribeLoadBalancersResponse describeLoadBalancersResponse =
        elbV2Client.describeLoadBalancer(awsInternalConfig, describeLoadBalancersRequest, ecsInfraConfig.getRegion());
    if (EmptyPredicate.isEmpty(describeLoadBalancersResponse.loadBalancers())) {
      throw new InvalidRequestException(
          "load balancer with name:" + loadBalancer + "is not present in this aws account");
    }
    String loadBalancerArn = describeLoadBalancersResponse.loadBalancers().get(0).loadBalancerArn();
    List<Listener> listeners = newArrayList();
    String nextToken = null;
    do {
      DescribeListenersRequest describeListenersRequest =
          DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).marker(nextToken).pageSize(10).build();
      DescribeListenersResponse describeListenersResponse =
          elbV2Client.describeListener(awsInternalConfig, describeListenersRequest, ecsInfraConfig.getRegion());
      listeners.addAll(describeListenersResponse.listeners());
      nextToken = describeLoadBalancersResponse.nextMarker();
    } while (nextToken != null);
    if (EmptyPredicate.isNotEmpty(listeners)) {
      for (Listener listener : listeners) {
        if (isListenerArnMatching(listenerArn, listener)) {
          return getFirstTargetGroupFromListener(awsInternalConfig, ecsInfraConfig, listenerArn, listenerRuleArn);
        }
      }
    }
    throw new InvalidRequestException(
        "listener with arn:" + listenerArn + "is not present in load balancer: " + loadBalancer);
  }

  List<Rule> getListenerRulesForListener(
      AwsInternalConfig awsInternalConfig, EcsInfraConfig ecsInfraConfig, String listenerArn) {
    List<Rule> rules = newArrayList();
    String nextToken = null;
    do {
      DescribeRulesRequest describeRulesRequest =
          DescribeRulesRequest.builder().listenerArn(listenerArn).marker(nextToken).pageSize(10).build();
      DescribeRulesResponse describeRulesResponse =
          elbV2Client.describeRules(awsInternalConfig, describeRulesRequest, ecsInfraConfig.getRegion());
      rules.addAll(describeRulesResponse.rules());
      nextToken = describeRulesResponse.nextMarker();
    } while (nextToken != null);

    return rules;
  }

  String getDefaultListenerRuleForListener(
      AwsInternalConfig awsInternalConfig, EcsInfraConfig ecsInfraConfig, String listenerArn) {
    List<Rule> rules = getListenerRulesForListener(awsInternalConfig, ecsInfraConfig, listenerArn);
    for (Rule rule : rules) {
      if (rule.isDefault()) {
        return rule.ruleArn();
      }
    }

    // throw error if default listener rule not found
    String errorMessage = format("Default listener rule not found for listener %s", listenerArn);
    throw new InvalidRequestException(errorMessage);
  }

  public void updateECSLoadbalancerConfigWithDefaultListenerRulesIfEmpty(EcsLoadBalancerConfig ecsLoadBalancerConfig,
      AwsInternalConfig awsInternalConfig, EcsInfraConfig ecsInfraConfig, LogCallback logCallback) {
    if (StringUtils.isEmpty(ecsLoadBalancerConfig.getProdListenerRuleArn())) {
      String defaultProdListenerRuleArn = getDefaultListenerRuleForListener(
          awsInternalConfig, ecsInfraConfig, ecsLoadBalancerConfig.getProdListenerArn());
      ecsLoadBalancerConfig.setProdListenerRuleArn(defaultProdListenerRuleArn);
      String message =
          format("Prod Listener Rule is not provided. Using default listener rule %s", defaultProdListenerRuleArn);
      logCallback.saveExecutionLog(message, LogLevel.INFO);
    }

    if (StringUtils.isEmpty(ecsLoadBalancerConfig.getStageListenerRuleArn())) {
      String defaultStageListenerRuleArn = getDefaultListenerRuleForListener(
          awsInternalConfig, ecsInfraConfig, ecsLoadBalancerConfig.getStageListenerArn());
      ecsLoadBalancerConfig.setStageListenerRuleArn(defaultStageListenerRuleArn);
      String message =
          format("Stage Listener Rule is not provided. Using default listener rule %s", defaultStageListenerRuleArn);
      logCallback.saveExecutionLog(message, LogLevel.INFO);
    }
  }

  private String getFirstTargetGroupFromListener(
      AwsInternalConfig awsInternalConfig, EcsInfraConfig ecsInfraConfig, String listenerArn, String listenerRuleArn) {
    List<Rule> rules = getListenerRulesForListener(awsInternalConfig, ecsInfraConfig, listenerArn);

    if (EmptyPredicate.isNotEmpty(rules)) {
      for (Rule rule : rules) {
        if (isListenerRuleArnMatching(listenerRuleArn, rule)) {
          return getFirstTargetGroupFromListenerRule(rule);
        }
      }
    }
    throw new InvalidRequestException(
        "listener rule with arn: " + listenerRuleArn + " is not present in listener: " + listenerArn);
  }

  private String getFirstTargetGroupFromListenerRule(Rule rule) {
    if (EmptyPredicate.isNotEmpty(rule.actions())) {
      Action action = rule.actions().stream().findFirst().orElse(null);
      if (action == null || EmptyPredicate.isEmpty(action.targetGroupArn())) {
        throw new InvalidRequestException(
            "No action is present in listener rule:" + rule.ruleArn() + " or there is no target group attached");
      }
      return action.targetGroupArn();
    }
    throw new InvalidRequestException("No action is present in listener rule: " + rule.ruleArn());
  }

  private boolean isListenerRuleArnMatching(String listenerRuleArn, Rule rule) {
    if (EmptyPredicate.isNotEmpty(rule.ruleArn()) && listenerRuleArn.equalsIgnoreCase(rule.ruleArn())) {
      return true;
    }
    return false;
  }

  private boolean isListenerArnMatching(String listenerArn, Listener listener) {
    if (EmptyPredicate.isNotEmpty(listener.listenerArn()) && listenerArn.equalsIgnoreCase(listener.listenerArn())) {
      return true;
    }
    return false;
  }

  public void deleteServices(
      List<Service> services, EcsInfraConfig ecsInfraConfig, LogCallback logCallback, long timeoutInMillis) {
    if (EmptyPredicate.isNotEmpty(services)) {
      services.forEach(service -> {
        deleteService(service.serviceName(), service.clusterArn(), ecsInfraConfig.getRegion(),
            ecsInfraConfig.getAwsConnectorDTO());

        ecsServiceInactiveStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(),
            service.serviceName(), ecsInfraConfig.getRegion(), (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));
      });
    }
  }

  private boolean isServiceBGVersion(List<Tag> tags, String version) {
    if (isEmpty(tags)) {
      return false;
    }
    Optional<Tag> tag =
        tags.stream()
            .filter(serviceTag -> BG_VERSION.equals(serviceTag.key()) && version.equals(serviceTag.value()))
            .findFirst();
    return tag.isPresent();
  }

  public boolean isServiceActive(Service service) {
    return service != null && service.status().equals("ACTIVE");
  }

  public boolean isServiceDraining(Service service) {
    return service != null && service.status().equals("DRAINING");
  }

  public EcsRunTaskResponse getEcsRunTaskResponse(TaskDefinition taskDefinition,
      String ecsRunTaskRequestDefinitionManifestContent, boolean isSkipSteadyStateCheck, long timeoutInMillis,
      EcsInfraConfig ecsInfraConfig, LogCallback runTaskLogCallback) {
    runTaskLogCallback.saveExecutionLog(color(format("%n ECS Task Request Definition Content %n"), White, Bold));
    ecsCommandTaskHelper.printManifestContent(ecsRunTaskRequestDefinitionManifestContent, runTaskLogCallback);
    String taskDefinitionArn = taskDefinition.taskDefinitionArn();
    String taskDefinitionName = getEcsTaskDefinitionName(taskDefinition);

    RunTaskRequest.Builder runTaskRequestBuilder = ecsCommandTaskHelper.parseYamlAsObject(
        ecsRunTaskRequestDefinitionManifestContent, RunTaskRequest.serializableBuilderClass());

    RunTaskRequest runTaskRequest =
        runTaskRequestBuilder.taskDefinition(taskDefinitionArn).cluster(ecsInfraConfig.getCluster()).build();

    runTaskLogCallback.saveExecutionLog(
        format("Triggering %s tasks with task definition %s",
            runTaskRequest.count() != null ? runTaskRequest.count() : 1, taskDefinitionName),
        LogLevel.INFO);

    RunTaskResponse runTaskResponse =
        ecsCommandTaskHelper.runTask(runTaskRequest, ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getRegion());

    runTaskLogCallback.saveExecutionLog(format("%d Tasks were triggered successfully and %d failures were received.",
                                            runTaskResponse.tasks().size(), runTaskResponse.failures().size()),
        LogLevel.INFO);

    List<Task> triggeredTasks = runTaskResponse.tasks();
    List<EcsTask> triggeredEcsTasks = null;
    if (triggeredTasks != null) {
      runTaskResponse.tasks().forEach(
          t -> runTaskLogCallback.saveExecutionLog(format("Task => %s succeeded", t.taskArn())));
    }

    if (runTaskResponse.failures() != null) {
      runTaskResponse.failures().forEach(f -> {
        runTaskLogCallback.saveExecutionLog(
            format("%s failed with reason => %s \nDetails: %s", f.arn(), f.reason(), f.detail()), LogLevel.ERROR,
            CommandExecutionStatus.FAILURE);
      });
    }

    if (triggeredTasks != null && isNotEmpty(triggeredTasks)) {
      List<String> triggeredTaskARNs = triggeredTasks.stream().map(task -> task.taskArn()).collect(Collectors.toList());
      if (!isSkipSteadyStateCheck) {
        ecsCommandTaskHelper.waitAndDoSteadyStateCheck(triggeredTaskARNs, timeoutInMillis,
            ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getRegion(), ecsInfraConfig.getCluster(),
            runTaskLogCallback);
      } else {
        runTaskLogCallback.saveExecutionLog(format("Skipped Steady State Check"), LogLevel.INFO);
      }

      triggeredEcsTasks =
          triggeredTasks.stream().map(task -> EcsMapper.toEcsTask(task, null)).collect(Collectors.toList());
    }

    EcsRunTaskResult ecsRunTaskResult =
        EcsRunTaskResult.builder().ecsTasks(triggeredEcsTasks).region(ecsInfraConfig.getRegion()).build();

    runTaskLogCallback.saveExecutionLog("Success.", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return EcsRunTaskResponse.builder()
        .ecsRunTaskResult(ecsRunTaskResult)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }

  public String getEcsTaskDefinitionName(TaskDefinition taskDefinition) {
    return taskDefinition.family() + ":" + taskDefinition.revision();
  }

  public DescribeTaskDefinitionResponse validateEcsTaskDefinition(
      String ecsTaskDefinition, EcsInfraConfig ecsInfraConfig, LogCallback logCallback) {
    DescribeTaskDefinitionRequest describeTaskDefinitionRequest =
        DescribeTaskDefinitionRequest.builder().taskDefinition(ecsTaskDefinition).build();
    try {
      return ecsCommandTaskHelper.describeTaskDefinition(
          describeTaskDefinitionRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
    } catch (Exception e) {
      log.error("Failed to describe Task Definition ", e);
      logCallback.saveExecutionLog("Failed to describe Task Definition ", ERROR);
      throw e;
    }
  }

  public RunTaskResponse runTask(RunTaskRequest runTaskRequest, AwsConnectorDTO awsConnectorDTO, String region) {
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    return ecsV2Client.runTask(awsInternalConfig, runTaskRequest, region);
  }

  public List<Task> getTasksFromTaskARNs(List<String> triggeredRunTaskArns, String cluster, String region,
      AwsConnectorDTO awsConnectorDTO, LogCallback logCallback) {
    return ecsV2Client
        .getTasks(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), cluster, triggeredRunTaskArns, region)
        .tasks();
  }

  public void waitAndDoSteadyStateCheck(List<String> triggeredRunTaskArns, Long timeout,
      AwsConnectorDTO awsConnectorDTO, String region, String clusterName, LogCallback logCallback) {
    try {
      HTimeLimiter.callInterruptible(timeLimiter, Duration.ofMillis(timeout), () -> {
        while (true) {
          List<Task> tasks =
              getTasksFromTaskARNs(triggeredRunTaskArns, clusterName, region, awsConnectorDTO, logCallback);

          List<Task> notInRunningStateTasks = tasks.stream()
                                                  .filter(t -> !DesiredStatus.RUNNING.name().equals(t.lastStatus()))
                                                  .collect(Collectors.toList());

          List<Task> tasksWithFailedContainers =
              tasks.stream()
                  .filter(task -> task.containers().stream().anyMatch(this::isEcsTaskContainerFailed))
                  .collect(Collectors.toList());

          if (EmptyPredicate.isNotEmpty(tasksWithFailedContainers)) {
            String errorMsg = tasksWithFailedContainers.stream()
                                  .flatMap(task -> task.containers().stream().filter(this::isEcsTaskContainerFailed))
                                  .map(container
                                      -> container.taskArn() + " => " + container.containerArn()
                                          + " => exit code : " + container.exitCode())
                                  .collect(Collectors.joining("\n"));
            logCallback.saveExecutionLog(
                "Containers in some tasks failed and are showing non zero exit code\n" + errorMsg, LogLevel.ERROR,
                CommandExecutionStatus.FAILURE);
            throw new CommandExecutionException(
                "Containers in some tasks failed and are showing non zero exit code\n " + errorMsg);
          }

          if (EmptyPredicate.isEmpty(notInRunningStateTasks)) {
            return true;
          }

          String taskStatusLog = tasks.stream()
                                     .map(task -> format("%s : %s", task.taskArn(), task.lastStatus()))
                                     .collect(Collectors.joining("\n"));

          logCallback.saveExecutionLog(format("%d tasks have not completed", notInRunningStateTasks.size()));
          logCallback.saveExecutionLog(taskStatusLog);
          sleep(ofSeconds(10));
        }
      });
    } catch (UncheckedTimeoutException e) {
      logCallback.saveExecutionLog(
          "Timed out waiting for run tasks to complete", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new TimeoutException(
          "Timed out waiting for tasks to be in running state", "Timeout", e, WingsException.EVERYBODY);
    } catch (WingsException e) {
      logCallback.saveExecutionLog(
          "Got Some exception while waiting for tasks to complete", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw e;
    } catch (Exception e) {
      logCallback.saveExecutionLog(
          "Got Some exception while waiting for tasks to complete", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new InvalidRequestException("Error while waiting for run tasks to complete", e);
    }
    logCallback.saveExecutionLog("All Tasks completed successfully.", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }

  public boolean isEcsTaskContainerFailed(Container container) {
    return (container.exitCode() != null && container.exitCode() != 0)
        || (container.lastStatus() != null && container.lastStatus().equals("STOPPED") && container.exitCode() == null);
  }

  public void printEcsManifestsContent(String taskDefinition, String serviceDefinition, List<String> scalableTargets,
      List<String> scalingPolicy, LogCallback logCallback) {
    if (isNotEmpty(taskDefinition)) {
      logCallback.saveExecutionLog(color(format("%n ECS Task Definition Content %n"), White, Bold) + taskDefinition);
    }
    logCallback.saveExecutionLog(
        color(format("%n ECS Service Definition Content %n"), White, Bold) + serviceDefinition);

    if (scalableTargets != null && isNotEmpty(scalableTargets)) {
      scalableTargets.forEach(content -> {
        logCallback.saveExecutionLog(color(format("%n ECS Scalable Target Content %n"), White, Bold) + content);
      });
    }

    if (scalingPolicy != null && isNotEmpty(scalingPolicy)) {
      scalingPolicy.forEach(content -> {
        logCallback.saveExecutionLog(color(format("%n ECS Scaling Policy Content %n"), White, Bold) + content);
      });
    }
  }

  public void printManifestContent(String manifestContent, LogCallback logCallback) {
    logCallback.saveExecutionLog(manifestContent);
  }

  public String toYaml(Object obj) throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    return objectMapper.writeValueAsString(obj);
  }

  public void sleepInSeconds(Integer delay) {
    sleep(ofSeconds(delay));
  }
}
