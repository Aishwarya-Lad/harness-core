package software.wings.helpers.ext.artifactory;

import static software.wings.beans.config.ArtifactoryConfig.Builder.anArtifactoryConfig;
import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import org.apache.commons.collections.CollectionUtils;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryClient;
import org.jfrog.artifactory.client.ArtifactoryRequest;
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl;
import org.jfrog.artifactory.client.model.PackageType;
import org.jfrog.artifactory.client.model.Repository;
import org.jfrog.artifactory.client.model.repository.settings.RepositorySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by sgurubelli on 6/27/17.
 */
public class ArtifactoryServiceImpl implements ArtifactoryService {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  public List<BuildDetails> getBuilds(
      ArtifactoryConfig artifactoryConfig, String repoKey, String imageName, int maxNumberOfBuilds) {
    Artifactory artifactory = getArtifactoryClient(artifactoryConfig);
    String apiUrl = "api/docker/" + repoKey + "/v2/" + imageName + "/tags/list";
    ArtifactoryRequest repositoryRequest = new ArtifactoryRequestImpl()
                                               .apiUrl(apiUrl)
                                               .method(ArtifactoryRequest.Method.GET)
                                               .responseType(ArtifactoryRequest.ContentType.JSON);
    Map response = artifactory.restCall(repositoryRequest);
    if (response != null) {
      List<String> tags = (List<String>) response.get("tags");
      if (CollectionUtils.isEmpty(tags)) {
        return null;
      }
      return tags.stream().map(s -> aBuildDetails().withNumber(s).build()).collect(Collectors.toList());
    }
    return null;
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(ArtifactoryConfig artifactoryConfig, String repositoryPath) {
    return null;
  }

  @Override
  public Map<String, String> getRepositories(ArtifactoryConfig artifactoryConfig) {
    Map<String, String> repositories = new HashMap<>();
    try {
      String apiUrl = "api/repositories/";
      Artifactory artifactory = getArtifactoryClient(artifactoryConfig);
      ArtifactoryRequest repositoryRequest = new ArtifactoryRequestImpl()
                                                 .apiUrl(apiUrl)
                                                 .method(ArtifactoryRequest.Method.GET)
                                                 .responseType(ArtifactoryRequest.ContentType.JSON);
      List<Map> response = artifactory.restCall(repositoryRequest);
      for (Map repository : response) {
        String repoKey = repository.get("key").toString();
        Repository repo = artifactory.repository(repoKey).get();
        RepositorySettings settings = repo.getRepositorySettings();
        PackageType packageType = settings.getPackageType();
        if (packageType.equals(PackageType.docker)) {
          repositories.put(repository.get("key").toString(), repository.get("key").toString());
        }
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      System.out.print("Hello");
    }
    return repositories;

    /* Map<String, String> repositories = new HashMap<>();
     List<LightweightRepository> repoList =
     getArtifactoryClient(artifactoryConfig).repositories().list(RepositoryTypeImpl.VIRTUAL); for (LightweightRepository
     lightweightRepository : repoList) { Repository repo =
     getArtifactoryClient(artifactoryConfig).repository(lightweightRepository.getKey()).get(); RepositorySettings
     settings = repo.getRepositorySettings(); PackageType packageType = settings.getPackageType(); if
     (packageType.equals(PackageType.docker)) { repositories.put(lightweightRepository.getKey(),
     lightweightRepository.getKey());
       }
     }
     return repositories;*/
  }

  @Override
  public List<String> getRepoPaths(ArtifactoryConfig artifactoryConfig, String repoKey) {
    Artifactory artifactory = getArtifactoryClient(artifactoryConfig);
    Repository repository = artifactory.repository(repoKey).get();
    RepositorySettings settings = repository.getRepositorySettings();
    PackageType packageType = settings.getPackageType();
    if (packageType.equals(PackageType.docker)) {
    }
    return listDockerImages(artifactory, repoKey);
  }

  public List<String> listDockerImages(Artifactory artifactory, String repoKey) {
    try {
      String apiUrl = "api/docker/" + repoKey + "/v2"
          + "/_catalog";
      ArtifactoryRequest repositoryRequest = new ArtifactoryRequestImpl()
                                                 .apiUrl(apiUrl)
                                                 .method(ArtifactoryRequest.Method.GET)
                                                 .responseType(ArtifactoryRequest.ContentType.JSON);
      Map response = artifactory.restCall(repositoryRequest);
      if (response != null) {
        List<String> repositories = (List<String>) response.get("repositories");
        if (CollectionUtils.isEmpty(repositories)) {
          return null;
        }
        return repositories;
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }

    return null;
  }

  /**
   * Get Artifactory Client
   * @param artifactoryConfig
   * @return Artifactory returns artifactory client
   */
  private Artifactory getArtifactoryClient(ArtifactoryConfig artifactoryConfig) {
    Artifactory artifactory = ArtifactoryClient.create(
        getBaseUrl(artifactoryConfig), artifactoryConfig.getUsername(), new String(artifactoryConfig.getPassword()));
    return artifactory;
  }

  private String getBaseUrl(ArtifactoryConfig artifactoryConfig) {
    String baseUrl = artifactoryConfig.getArtifactoryUrl();
    if (!baseUrl.endsWith("/")) {
      baseUrl = baseUrl + "/";
    }
    return baseUrl;
  }

  public static void main(String... args) {
    String url = "https://artifactory.harness.io/artifactory";
    // url = "http://localhost/artifactory/";

    ArtifactoryServiceImpl artifactoryService = new ArtifactoryServiceImpl();
    System.out.println("Hello welcome to Artifactory");
    ArtifactoryConfig artifactoryConfig = anArtifactoryConfig()
                                              .withArtifactoryUrl(url)
                                              .withUsername("admin")
                                              .withPassword("harness123!".toCharArray())
                                              .build();
    /* List<BuildDetails> buildDetails = new ArtifactoryServiceImpl().getBuilds(artifactoryConfig,
     "docker-local/wingsplugins/todolist", 1); for (BuildDetails buildDetail : buildDetails) { System.out.println("Build
     Number" +  buildDetail.getNumber());
     }*/

    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig);
    System.out.println("Repositories" + repositories);

    // List<String> images = artifactoryService.listDockerImages(artifactoryConfig, "docker");
    // images.forEach(s -> System.out.println(s));
  }
}
