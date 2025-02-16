/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.trafficrouting;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogLevel.WARN;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.k8s.trafficrouting.HeaderConfig;
import io.harness.delegate.task.k8s.trafficrouting.K8sTrafficRoutingConfig;
import io.harness.delegate.task.k8s.trafficrouting.RouteType;
import io.harness.delegate.task.k8s.trafficrouting.SMIProviderConfig;
import io.harness.delegate.task.k8s.trafficrouting.TrafficRoute;
import io.harness.delegate.task.k8s.trafficrouting.TrafficRouteRule;
import io.harness.delegate.task.k8s.trafficrouting.TrafficRoutingDestination;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.smi.Backend;
import io.harness.k8s.model.smi.HttpRouteGroup;
import io.harness.k8s.model.smi.Match;
import io.harness.k8s.model.smi.Metadata;
import io.harness.k8s.model.smi.RouteMatch;
import io.harness.k8s.model.smi.RouteSpec;
import io.harness.k8s.model.smi.RouteSpecGsonDeserializer;
import io.harness.k8s.model.smi.SMIRoute;
import io.harness.k8s.model.smi.TrafficSplit;
import io.harness.k8s.model.smi.TrafficSplitSpec;
import io.harness.logging.LogCallback;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.kubernetes.client.util.Yaml;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;

@NoArgsConstructor
@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public class SMITrafficRoutingResourceCreator extends TrafficRoutingResourceCreator {
  public static final String PLURAL = "trafficsplits";

  private static final String TRAFFIC_SPLIT_SUFFIX = "-traffic-split";
  private static final String HTTP_ROUTE_GROUP_SUFFIX = "-http-route-group";
  // toDo this needs to be revisited, should not be hardcoded
  private static final String TRAFFIC_SPLIT_DEFAULT_NAME = "harness-traffic-routing-traffic-split";

  static final String SPLIT = "split";
  static final String SPECS = "specs";
  private static final String BACKENDS_PATH = "/spec/backends";
  private static final Map<String, List<String>> SUPPORTED_API_MAP = Map.of(SPLIT,
      List.of("split.smi-spec.io/v1alpha1", "split.smi-spec.io/v1alpha2", "split.smi-spec.io/v1alpha3",
          "split.smi-spec.io/v1alpha4"),
      SPECS,
      List.of("specs.smi-spec.io/v1alpha1", "specs.smi-spec.io/v1alpha2", "specs.smi-spec.io/v1alpha3",
          "specs.smi-spec.io/v1alpha4"));
  private Gson gson = new GsonBuilder().registerTypeAdapter(RouteSpec.class, new RouteSpecGsonDeserializer()).create();

  @Override
  protected List<String> getManifests(K8sTrafficRoutingConfig k8sTrafficRoutingConfig, String namespace,
      String releaseName, String stableName, String stageName, Map<String, String> apiVersions) {
    TrafficSplit trafficSplit =
        getTrafficSplit(k8sTrafficRoutingConfig, namespace, releaseName, stableName, stageName, apiVersions.get(SPLIT));

    List<SMIRoute> smiRoutes =
        getSMIRoutes(k8sTrafficRoutingConfig.getRoutes(), namespace, releaseName, apiVersions.get(SPECS));

    applyRoutesToTheTrafficSplit(trafficSplit, smiRoutes);
    List<String> trafficRoutingManifests = new ArrayList<>();
    trafficRoutingManifests.add(Yaml.dump(trafficSplit));
    trafficRoutingManifests.addAll(smiRoutes.stream().map(Yaml::dump).collect(Collectors.toList()));

    return trafficRoutingManifests;
  }

  @Override
  protected Map<String, List<String>> getProviderVersionMap() {
    return SUPPORTED_API_MAP;
  }

  @Override
  protected String getMainResourceKind() {
    return "TrafficSplit";
  }

  @Override
  protected String getMainResourceKindPlural() {
    return PLURAL;
  }

  @Override
  public Optional<String> getSwapTrafficRoutingPatch(String stable, String stage) {
    if (isNotEmpty(stable) && isNotEmpty(stage)) {
      List<Backend> backends = List.of(
          Backend.builder().service(stable).weight(100).build(), Backend.builder().service(stage).weight(0).build());

      try {
        return Optional.of(format(format("[%s]", PATCH_REPLACE_JSON_FORMAT), BACKENDS_PATH,
            new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL).writeValueAsString(backends)));
      } catch (JsonProcessingException e) {
        log.warn("Failed to Deserialize List of Backends", e);
      }
    }
    return Optional.empty();
  }

  private TrafficSplit getTrafficSplit(K8sTrafficRoutingConfig k8sTrafficRoutingConfig, String namespace,
      String releaseName, String stableName, String stageName, String apiVersion) {
    String name = getTrafficRoutingResourceName(stableName, TRAFFIC_SPLIT_SUFFIX, TRAFFIC_SPLIT_DEFAULT_NAME);
    Metadata metadata = getMetadata(name, namespace, releaseName);
    String rootService = getRootService((SMIProviderConfig) k8sTrafficRoutingConfig.getProviderConfig(), stableName);
    rootService = updatePlaceHoldersIfExist(rootService, stableName, stageName);

    Map<String, Pair<Integer, Integer>> destinations = destinationsToMap(k8sTrafficRoutingConfig.getDestinations());

    Map<String, Pair<Integer, Integer>> normalizedDestinations = normalizeDestinations(destinations, 100);

    return TrafficSplit.builder()
        .metadata(metadata)
        .apiVersion(apiVersion)
        .spec(TrafficSplitSpec.builder()
                  .backends(getBackends(normalizedDestinations, stableName, stageName))
                  .service(rootService)
                  .build())
        .build();
  }

  private List<Backend> getBackends(
      Map<String, Pair<Integer, Integer>> normalizedDestinations, String stableName, String stageName) {
    return normalizedDestinations.keySet()
        .stream()
        .map(host
            -> Backend.builder()
                   .service(updatePlaceHoldersIfExist(host, stableName, stageName))
                   .weight(normalizedDestinations.get(host).getRight() != null
                           ? normalizedDestinations.get(host).getRight()
                           : normalizedDestinations.get(host).getLeft())
                   .build())
        .collect(Collectors.toList());
  }

  private String getRootService(SMIProviderConfig k8sTrafficRoutingConfig, String stableName) {
    String rootService = k8sTrafficRoutingConfig.getRootService();
    if (isEmpty(rootService)) {
      if (isEmpty(stableName)) {
        throw NestedExceptionUtils.hintWithExplanationException(
            format(KubernetesExceptionHints.TRAFFIC_ROUTING_MISSING_FIELD, "rootService", "SMI"),
            format(KubernetesExceptionExplanation.TRAFFIC_ROUTING_MISSING_FIELD, "rootService"),
            new InvalidArgumentsException(
                "Root service must be provided in the Traffic Routing config for SMI provider"));
      }
      rootService = stableName;
    }
    return rootService;
  }

  private Metadata getMetadata(String name, String namespace, String releaseName) {
    return Metadata.builder()
        .name(name)
        .namespace(namespace)
        .labels(Map.of(HarnessLabels.releaseName, releaseName))
        .build();
  }

  private List<SMIRoute> getSMIRoutes(List<TrafficRoute> routes, String namespace, String releaseName, String api) {
    List<SMIRoute> smiRoutes = new ArrayList<>();
    if (routes != null) {
      smiRoutes.addAll(getHttpRoutes(routes, namespace, releaseName, api));
    }
    return smiRoutes;
  }

  private List<HttpRouteGroup> getHttpRoutes(
      List<TrafficRoute> routes, String namespace, String releaseName, String api) {
    return routes.stream()
        .filter(route -> route.getRouteType() == RouteType.HTTP)
        .flatMap(route
            -> route.getRules() == null
                ? Stream.empty()
                : route.getRules().stream().map(rule -> mapToHttpRouteGroup(rule, namespace, releaseName, api)))
        .collect(Collectors.toList());
  }

  private HttpRouteGroup mapToHttpRouteGroup(
      TrafficRouteRule rule, String namespace, String releaseName, String apiVersion) {
    String defaultName =
        String.format("harness%s-%s", HTTP_ROUTE_GROUP_SUFFIX, RandomStringUtils.randomAlphanumeric(4));

    String resourceName = getTrafficRoutingResourceName(rule.getName(), HTTP_ROUTE_GROUP_SUFFIX, defaultName);
    Map<String, String> headerConfig = rule.getHeaderConfigs() == null
        ? null
        : rule.getHeaderConfigs().stream().collect(Collectors.toMap(HeaderConfig::getKey, HeaderConfig::getValue));

    return HttpRouteGroup.builder()
        .metadata(getMetadata(resourceName, namespace, releaseName))
        .apiVersion(apiVersion)
        .spec(RouteSpec.builder()
                  .matches(List.of(
                      Match.createMatch(rule.getRuleType().name(), rule.getName(), rule.getValue(), headerConfig)))
                  .build())
        .build();
  }

  private void applyRoutesToTheTrafficSplit(TrafficSplit trafficSplit, List<SMIRoute> smiRoutes) {
    if (isNotEmpty(smiRoutes)) {
      trafficSplit.getSpec().setMatches(
          smiRoutes.stream()
              .map(route -> RouteMatch.builder().kind(route.getKind()).name(route.getMetadata().getName()).build())
              .collect(Collectors.toList()));
    }
  }

  @Override
  public Optional<String> generateTrafficRoutingPatch(
      K8sTrafficRoutingConfig k8sTrafficRoutingConfig, Object trafficRoutingClusterResource, LogCallback logCallback) {
    List<String> listOfPatches = new ArrayList<>();
    try {
      String trafficRoutingClusterResourceJson = gson.toJson(trafficRoutingClusterResource);
      TrafficSplit trafficSplit = gson.fromJson(trafficRoutingClusterResourceJson, TrafficSplit.class);

      listOfPatches.addAll(createPatchForTrafficRoutingResourceDestinations(
          super.destinationsToMap(k8sTrafficRoutingConfig.getDestinations()), trafficSplit.getSpec().getBackends(),
          logCallback));
    } catch (Exception e) {
      log.error("Failed to parse Traffic Split resource from the cluster.", e);
      throw e;
    }

    if (listOfPatches.size() > 0) {
      return Optional.of(listOfPatches.toString());
    }

    return Optional.empty();
  }

  protected Collection<String> createPatchForTrafficRoutingResourceDestinations(
      Map<String, Pair<Integer, Integer>> configuredDestinations, List<Backend> backendList, LogCallback logCallback) {
    List<String> patches = new ArrayList<>();
    if (backendList != null) {
      // looping through destinations and checking for matching destinations
      Map<String, Pair<Integer, Integer>> clusterResourceDestinations = destinationsToMap(backendList);

      for (String trafficRoutingDestinationHost : configuredDestinations.keySet()) {
        if (clusterResourceDestinations.containsKey(trafficRoutingDestinationHost)) {
          clusterResourceDestinations.put(trafficRoutingDestinationHost,
              Pair.of(clusterResourceDestinations.get(trafficRoutingDestinationHost).getLeft(),
                  configuredDestinations.get(trafficRoutingDestinationHost).getLeft()));
        } else {
          String warningMessage = format("Traffic Routing Destination [%s] not found in the Virtual Service resource",
              trafficRoutingDestinationHost);
          log.warn(warningMessage);
          logCallback.saveExecutionLog(warningMessage, WARN);
        }
      }

      Pair<Map, Map> filteredDestinations = filterDestinations(clusterResourceDestinations);
      if (shouldNormalizeDestinations(filteredDestinations)) {
        Map<String, Pair<Integer, Integer>> normalizedDestinations =
            normalizeFilteredDestinations(filteredDestinations);
        logDestinationsNormalization(normalizedDestinations, logCallback);
        // creating a patch for this particular route type and route with updated destinations
        try {
          patches.add(format(PATCH_REPLACE_JSON_FORMAT, BACKENDS_PATH,
              new ObjectMapper()
                  .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                  .writeValueAsString(mapToDestinations(normalizedDestinations))));
        } catch (JsonProcessingException e) {
          log.warn("Failed to Deserialize List of VirtualServiceDetails", e);
        }
      }
    }
    return patches;
  }

  @Override
  public Map<String, Pair<Integer, Integer>> destinationsToMap(List sourceDestinations) {
    Map<String, Pair<Integer, Integer>> outDestinations = new LinkedHashMap<>();
    if (sourceDestinations != null && sourceDestinations.size() > 0) {
      if (sourceDestinations.get(0) instanceof TrafficRoutingDestination) {
        return super.destinationsToMap(sourceDestinations);
      }

      ((List<Backend>) sourceDestinations).forEach(sourceDestination -> {
        outDestinations.put(sourceDestination.getService(),
            Pair.of(sourceDestination.getWeight() == null || sourceDestination.getWeight() < 0
                    ? null
                    : sourceDestination.getWeight(),
                null));
      });
    }
    return outDestinations;
  }

  @Override
  protected List<Backend> mapToDestinations(Map<String, Pair<Integer, Integer>> sourceDestinations) {
    List<Backend> outDestinations = new ArrayList<>();
    if (sourceDestinations != null && sourceDestinations.size() > 0) {
      for (String sourceDestinationKey : sourceDestinations.keySet()) {
        Pair<Integer, Integer> weights = sourceDestinations.get(sourceDestinationKey);
        outDestinations.add(Backend.builder()
                                .service(sourceDestinationKey)
                                .weight(weights.getRight() != null && weights.getRight() >= 0 ? weights.getRight()
                                        : weights.getLeft() != null && weights.getLeft() >= 0 ? weights.getLeft()
                                                                                              : null)
                                .build());
      }
    }
    return outDestinations;
  }
}
