/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s.trafficrouting;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.k8s.trafficrouting.K8sTrafficRoutingConfig;
import io.harness.delegate.task.k8s.trafficrouting.TrafficRoutingDestination;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.trafficrouting.TrafficRoutingInfoDTO;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@AllArgsConstructor
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public abstract class TrafficRoutingResourceCreator {
  private static final int K8S_RESOURCE_NAME_MAX = 253;
  private static final String STABLE_PLACE_HOLDER = "stable";
  private static final String STAGE_PLACE_HOLDER = "stage";
  private static final String CANARY_PLACE_HOLDER = "canary";
  protected static final String PATCH_REPLACE_JSON_FORMAT = "{ \"op\": \"replace\", \"path\": \"%s\", \"value\": %s}";

  public List<KubernetesResource> createTrafficRoutingResources(K8sTrafficRoutingConfig k8sTrafficRoutingConfig,
      String namespace, String releaseName, Set<String> availableApiVersions, LogCallback logCallback) {
    return createTrafficRoutingResources(
        k8sTrafficRoutingConfig, namespace, releaseName, null, null, availableApiVersions, logCallback);
  }

  public List<KubernetesResource> createTrafficRoutingResources(K8sTrafficRoutingConfig k8sTrafficRoutingConfig,
      String namespace, String releaseName, KubernetesResource primaryService, KubernetesResource secondaryService,
      Set<String> availableApiVersions, LogCallback logCallback) {
    Map<String, String> apiVersions = getApiVersions(availableApiVersions, logCallback);

    List<String> trafficRoutingManifests = getTrafficRoutingManifests(
        k8sTrafficRoutingConfig, namespace, releaseName, primaryService, secondaryService, apiVersions);

    logCallback.saveExecutionLog(
        format("Traffic Routing resources created: %n%s", String.join("\n---\n", trafficRoutingManifests)), INFO,
        CommandExecutionStatus.SUCCESS);
    return trafficRoutingManifests.stream()
        .map(ManifestHelper::getKubernetesResourcesFromSpec)
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }

  private List<String> getTrafficRoutingManifests(K8sTrafficRoutingConfig k8sTrafficRoutingConfig, String namespace,
      String releaseName, KubernetesResource stableService, KubernetesResource stageService,
      Map<String, String> apiVersions) {
    String stableName = stableService != null ? stableService.getResourceId().getName() : null;
    String stageName = stageService != null ? stageService.getResourceId().getName() : null;

    return getManifests(k8sTrafficRoutingConfig, namespace, releaseName, stableName, stageName, apiVersions);
  }

  protected abstract List<String> getManifests(K8sTrafficRoutingConfig k8sTrafficRoutingConfig, String namespace,
      String releaseName, String stableName, String stageName, Map<String, String> apiVersions);
  protected abstract Map<String, List<String>> getProviderVersionMap();

  public String updatePlaceHoldersIfExist(String host, String stable, String stage) {
    if (isEmpty(host)) {
      throw new InvalidArgumentsException("Host must be specified in the destination for traffic routing");
    } else if (STABLE_PLACE_HOLDER.equals(host) && isNotEmpty(stable)) {
      return stable;
    } else if ((STAGE_PLACE_HOLDER.equals(host) || CANARY_PLACE_HOLDER.equals(host)) && isNotEmpty(stage)) {
      return stage;
    } else {
      return host;
    }
  }

  public String getTrafficRoutingResourceName(String name, String suffix, String defaultName) {
    return name != null ? StringUtils.truncate(name, K8S_RESOURCE_NAME_MAX - suffix.length()) + suffix : defaultName;
  }

  public Map<String, String> getApiVersions(Set<String> clusterAvailableApis, LogCallback logCallback) {
    Map<String, String> apiVersions = new HashMap<>();
    getProviderVersionMap().forEach(
        (key, value)
            -> apiVersions.put(key,
                value.stream()
                    .sorted(Collections.reverseOrder())
                    .filter(clusterAvailableApis::contains)
                    .findFirst()
                    .orElseGet(() -> {
                      String firstVersion = value.get(value.size() - 1);
                      logCallback.saveExecutionLog(
                          format(
                              "CRD specification wasn't found for %s resource in the cluster. Version: %s will be used in case of creation of this resource.",
                              key, firstVersion),
                          LogLevel.WARN, CommandExecutionStatus.RUNNING);
                      return firstVersion;
                    })));
    return apiVersions;
  }

  public Optional<TrafficRoutingInfoDTO> getTrafficRoutingInfo(List<KubernetesResource> kubernetesResources) {
    return kubernetesResources.stream()
        .filter(resource -> resource.getResourceId().getKind().equals(getMainResourceKind()))
        .findFirst()
        .map(resource
            -> TrafficRoutingInfoDTO.builder()
                   .name(resource.getResourceId().getName())
                   .version(resource.getApiVersion())
                   .plural(getMainResourceKindPlural())
                   .build());
  }

  public void logDestinationsNormalization(Map<String, Pair<Integer, Integer>> destinations, LogCallback logCallback) {
    if (destinations != null) {
      for (String destinationHost : destinations.keySet()) {
        Pair<Integer, Integer> weights = destinations.get(destinationHost);
        if (weights.getLeft() != null && weights.getRight() != null
            && weights.getLeft().compareTo(weights.getRight()) != 0) {
          logCallback.saveExecutionLog(format("Destination [%s] weight will be normalized from [%s] to [%s].",
              color(destinationHost, Yellow, Bold), color(String.valueOf(weights.getLeft()), Yellow, Bold),
              color(String.valueOf(weights.getRight()), Yellow, Bold)));
        }
      }
    }
  }

  public Map<String, Pair<Integer, Integer>> destinationsToMap(List sourceDestinations) {
    Map<String, Pair<Integer, Integer>> outDestinations = new LinkedHashMap<>();
    if (sourceDestinations != null) {
      ((List<TrafficRoutingDestination>) sourceDestinations).forEach(sourceDestination -> {
        outDestinations.put(sourceDestination.getHost(),
            Pair.of(sourceDestination.getWeight() == null || sourceDestination.getWeight() < 0
                    ? null
                    : sourceDestination.getWeight(),
                null));
      });
    }
    return outDestinations;
  }

  protected List mapToDestinations(Map<String, Pair<Integer, Integer>> sourceDestinations) {
    List<TrafficRoutingDestination> outDestinations = new LinkedList<>();
    if (sourceDestinations != null) {
      for (String sourceDestinationKey : sourceDestinations.keySet()) {
        Pair<Integer, Integer> weights = sourceDestinations.get(sourceDestinationKey);
        outDestinations.add(TrafficRoutingDestination.builder()
                                .host(sourceDestinationKey)
                                .weight(weights.getRight() != null && weights.getRight() >= 0 ? weights.getRight()
                                        : weights.getLeft() != null && weights.getLeft() >= 0 ? weights.getLeft()
                                                                                              : null)
                                .build());
      }
    }
    return outDestinations;
  }

  public int sumDestinationsWeights(Map<String, Pair<Integer, Integer>> destinations) {
    return destinations != null && destinations.size() > 0
        ? destinations.keySet()
              .stream()
              .mapToInt(host
                  -> destinations.get(host).getRight() != null && destinations.get(host).getRight() >= 0
                      ? destinations.get(host).getRight()
                      : destinations.get(host).getLeft() != null && destinations.get(host).getLeft() >= 0
                      ? destinations.get(host).getLeft()
                      : 0)
              .sum()
        : 0;
  }

  int normalizeWeight(int sum, Integer weight, int groupSize, int cap) {
    if (sum == 0) {
      return (int) Math.round((double) cap / (double) groupSize);
    }
    return weight == null ? 0 : (int) Math.round((double) weight * (double) cap / (double) sum);
  }

  protected boolean shouldNormalizeDestinations(Pair<Map, Map> filteredDestinations) {
    if (filteredDestinations != null && filteredDestinations.getRight() != null
        && filteredDestinations.getRight().size() > 0) {
      return true;
    }
    return false;
  }

  public Pair<Map, Map> filterDestinations(Map<String, Pair<Integer, Integer>> destinations) {
    // recognizing matched and unmatched destinations
    Map<String, Pair<Integer, Integer>> matchedDestinations = new LinkedHashMap<>();
    Map<String, Pair<Integer, Integer>> nonMatchedDestinations = new LinkedHashMap<>();
    if (destinations != null) {
      for (String destinationHost : destinations.keySet()) {
        Pair<Integer, Integer> destWeights = destinations.get(destinationHost);
        if (destWeights != null) {
          if (destWeights.getRight() != null) {
            matchedDestinations.put(destinationHost, Pair.of(destWeights.getLeft(), destWeights.getRight()));
          } else {
            nonMatchedDestinations.put(destinationHost, Pair.of(destWeights.getLeft(), null));
          }
        }
      }
    }

    return Pair.of(nonMatchedDestinations, matchedDestinations);
  }

  protected Map<String, Pair<Integer, Integer>> normalizeFilteredDestinations(Pair<Map, Map> filteredDestinations) {
    // normalizing matched and unmatched destinations
    Map<String, Pair<Integer, Integer>> normalizedDestinations = new LinkedHashMap<>();
    Map nonMatchedDestinations = filteredDestinations.getLeft();
    Map matchedDestinations = filteredDestinations.getRight();
    int matchedDestinationsWeightsSum = sumDestinationsWeights(matchedDestinations);
    if (matchedDestinationsWeightsSum < 100) {
      normalizedDestinations.putAll(normalizeDestinations(nonMatchedDestinations, 100 - matchedDestinationsWeightsSum));
      normalizedDestinations.putAll(matchedDestinations);
    } else {
      normalizedDestinations.putAll(normalizeDestinations(nonMatchedDestinations, 0));
      normalizedDestinations.putAll(normalizeDestinations(matchedDestinations, 100));
    }
    return normalizedDestinations;
  }

  public Map<String, Pair<Integer, Integer>> normalizeDestinations(
      Map<String, Pair<Integer, Integer>> sourceDestinations, int cap) {
    Map<String, Pair<Integer, Integer>> normalizedDestinations = new LinkedHashMap<>();
    AtomicInteger normalizedSum = new AtomicInteger();
    if (sourceDestinations != null && sourceDestinations.size() > 0) {
      int sum = sumDestinationsWeights(sourceDestinations);

      for (String host : sourceDestinations.keySet()) {
        Pair<Integer, Integer> weights = sourceDestinations.get(host);
        int normalizedWeight = normalizeWeight(
            sum, weights.getRight() != null ? weights.getRight() : weights.getLeft(), sourceDestinations.size(), cap);
        normalizedDestinations.put(host, Pair.of(weights.getLeft(), normalizedWeight));
        normalizedSum.addAndGet(normalizedWeight);
      }
    }
    return correctNormalizedDestinations(normalizedDestinations, normalizedSum.get(), cap);
  }

  private Map<String, Pair<Integer, Integer>> correctNormalizedDestinations(
      Map<String, Pair<Integer, Integer>> normalizedDestinations, int normalizedSum, int cap) {
    Map<String, Pair<Integer, Integer>> normalizedCorrectedDestinations = new LinkedHashMap<>();
    if (normalizedDestinations != null && normalizedDestinations.size() > 0) {
      AtomicInteger correctionCount = new AtomicInteger(Math.abs(cap - normalizedSum));
      Comparator<Map.Entry<String, Pair<Integer, Integer>>> comparator =
          Map.Entry.comparingByValue(Comparator.comparing(Pair::getRight));
      int step = normalizedSum > cap ? -1 : 1;

      if (step < 0) {
        comparator = comparator.reversed();
      }

      normalizedDestinations.entrySet().stream().sorted(comparator).forEach(entry -> {
        if (correctionCount.get() > 0) {
          normalizedCorrectedDestinations.put(
              entry.getKey(), Pair.of(entry.getValue().getLeft(), entry.getValue().getRight() + step));
          correctionCount.getAndDecrement();
        } else {
          normalizedCorrectedDestinations.put(
              entry.getKey(), Pair.of(entry.getValue().getLeft(), entry.getValue().getRight()));
        }
      });
    }
    return normalizedCorrectedDestinations;
  }

  protected abstract String getMainResourceKind();

  protected abstract String getMainResourceKindPlural();
  public abstract Optional<String> getSwapTrafficRoutingPatch(String stable, String stage);

  public abstract Optional<String> generateTrafficRoutingPatch(
      K8sTrafficRoutingConfig k8sTrafficRoutingConfig, Object trafficRoutingClusterResource, LogCallback logCallback);
}
