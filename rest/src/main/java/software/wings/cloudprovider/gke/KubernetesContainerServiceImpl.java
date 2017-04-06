package software.wings.cloudprovider.gke;

import static org.awaitility.Awaitility.with;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import io.fabric8.kubernetes.api.model.DoneableService;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ClientResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.wings.beans.KubernetesConfig;
import software.wings.service.impl.KubernetesHelperService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by brett on 2/9/17
 */
@Singleton
public class KubernetesContainerServiceImpl implements KubernetesContainerService {
  private static final String RUNNING = "Running";

  private final Logger logger = LoggerFactory.getLogger(getClass());
  @Inject private KubernetesHelperService kubernetesHelperService = new KubernetesHelperService();

  @Override
  public ReplicationController createController(KubernetesConfig kubernetesConfig, ReplicationController definition) {
    logger.info("Creating controller {}", definition.getMetadata().getName());
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig)
        .replicationControllers()
        .createOrReplace(definition);
  }

  @Override
  public ReplicationController getController(KubernetesConfig kubernetesConfig, String name) {
    return name != null
        ? kubernetesHelperService.getKubernetesClient(kubernetesConfig).replicationControllers().withName(name).get()
        : null;
  }

  @Override
  public ReplicationControllerList listControllers(KubernetesConfig kubernetesConfig) {
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig).replicationControllers().list();
  }

  @Override
  public void deleteController(KubernetesConfig kubernetesConfig, String name) {
    logger.info("Deleting controller {}", name);
    kubernetesHelperService.getKubernetesClient(kubernetesConfig).replicationControllers().withName(name).delete();
  }

  @Override
  public void setControllerPodCount(KubernetesConfig kubernetesConfig, String name, int number) {
    kubernetesHelperService.getKubernetesClient(kubernetesConfig).replicationControllers().withName(name).scale(number);
    logger.info("Scaled controller {} to {} instances", name, number);
  }

  @Override
  public int getControllerPodCount(KubernetesConfig kubernetesConfig, String name) {
    return getController(kubernetesConfig, name).getSpec().getReplicas();
  }

  @Override
  public Multimap<String, String> getPodInfo(
      KubernetesConfig kubernetesConfig, String replicationControllerName, int number) {
    Multimap<String, String> result = Multimaps.newListMultimap(new HashMap<>(), ArrayList::new);

    waitForPodsToBeRunning(kubernetesConfig, replicationControllerName, number).forEach(pod -> {
      result.put("podNames", pod.getMetadata().getName());
      result.putAll("containerIds",
          pod.getStatus()
              .getContainerStatuses()
              .stream()
              .map(containerStatus -> containerStatus.getContainerID().substring(9, 21))
              .collect(Collectors.toList()));
    });

    return result;
  }

  @Override
  public Service createService(KubernetesConfig kubernetesConfig, Service definition) {
    logger.info("Creating service {}", definition.getMetadata().getName());
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig).services().createOrReplace(definition);
  }

  @Override
  public Service getService(KubernetesConfig kubernetesConfig, String name) {
    return name != null ? kubernetesHelperService.getKubernetesClient(kubernetesConfig).services().withName(name).get()
                        : null;
  }

  @Override
  public ServiceList listServices(KubernetesConfig kubernetesConfig) {
    return kubernetesHelperService.getKubernetesClient(kubernetesConfig).services().list();
  }

  @Override
  public void deleteService(KubernetesConfig kubernetesConfig, String name) {
    ClientResource<Service, DoneableService> service =
        kubernetesHelperService.getKubernetesClient(kubernetesConfig).services().withName(name);
    service.delete();
    logger.info("Deleted service {}", name);
  }

  private List<Pod> waitForPodsToBeRunning(
      KubernetesConfig kubernetesConfig, String replicationControllerName, int number) {
    Map<String, String> labels = getController(kubernetesConfig, replicationControllerName).getMetadata().getLabels();

    with().pollInterval(1, TimeUnit.SECONDS).await().atMost(60, TimeUnit.SECONDS).until(() -> {
      List<Pod> pods =
          kubernetesHelperService.getKubernetesClient(kubernetesConfig).pods().withLabels(labels).list().getItems();
      if (pods.size() != number) {
        return false;
      }
      boolean allRunning = true;
      for (Pod pod : pods) {
        allRunning = allRunning && pod.getStatus().getPhase().equals(RUNNING);
      }
      return allRunning;
    });

    return kubernetesHelperService.getKubernetesClient(kubernetesConfig).pods().withLabels(labels).list().getItems();
  }

  public void checkStatus(KubernetesConfig kubernetesConfig, String rcName, String serviceName) {
    KubernetesClient client = kubernetesHelperService.getKubernetesClient(kubernetesConfig);
    String masterUrl = client.getMasterUrl().toString();
    ReplicationController rc = client.replicationControllers().inNamespace("default").withName(rcName).get();
    if (rc != null) {
      String rcLink = masterUrl + rc.getMetadata().getSelfLink().substring(1);
      logger.info("Replication controller {}: {}", rcName, rcLink);
    } else {
      logger.info("Replication controller {} does not exist", rcName);
    }
    Service service = client.services().withName(serviceName).get();
    if (service != null) {
      String serviceLink = masterUrl + service.getMetadata().getSelfLink().substring(1);
      logger.info("Service %s: {}", serviceName, serviceLink);
    } else {
      logger.info("Service {} does not exist", serviceName);
    }
  }

  public void cleanup(KubernetesConfig kubernetesConfig) {
    KubernetesClient client = kubernetesHelperService.getKubernetesClient(kubernetesConfig);
    if (client.services().list().getItems() != null) {
      client.services().delete();
      logger.info("Deleted existing services");
    }
    if (client.replicationControllers().list().getItems() != null) {
      client.replicationControllers().delete();
      logger.info("Deleted existing replication controllers");
    }
  }
}
