package software.wings.resources;

import static software.wings.security.PermissionAttribute.ResourceType.APPLICATION;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.Yaml;
import io.swagger.annotations.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.wings.beans.Application;
import software.wings.beans.RestResponse;
import software.wings.beans.Service;
import software.wings.beans.command.ServiceCommand;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.yaml.AppYaml;
import software.wings.yaml.ServiceYaml;
import software.wings.yaml.YamlHelper;
import software.wings.yaml.YamlPayload;

import java.util.List;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

/**
 * Service Resource class.
 *
 * @author bsollish
 */
@Api("/serviceYaml")
@Path("/serviceYaml")
@Produces("application/json")
@AuthRule(APPLICATION)
public class ServiceYamlResource {
  private ServiceResourceService serviceResourceService;

  private final Logger logger = LoggerFactory.getLogger(getClass());

  /**
   * Instantiates a new app yaml resource.
   *
   * @param serviceResourceService the service (resource) service
   */
  @Inject
  public ServiceYamlResource(ServiceResourceService serviceResourceService) {
    this.serviceResourceService = serviceResourceService;
  }

  /**
   * Gets the yaml version of a service by serviceId
   *
   * @param appId  the app id
   * @param serviceId  the service id
   * @return the rest response
   */
  @GET
  @Path("/{appId}/{serviceId}")
  @Timed
  @ExceptionMetered
  public RestResponse<YamlPayload> getYaml(@PathParam("serviceId") String serviceId, @PathParam("appId") String appId) {
    RestResponse rr = new RestResponse<>();

    Service service = serviceResourceService.get(appId, serviceId);
    List<ServiceCommand> serviceCommands = service.getServiceCommands();

    logger.info("***************** serviceCommands: " + serviceCommands);

    ServiceYaml serviceYaml = new ServiceYaml();
    serviceYaml.setServiceCommands(serviceCommands);

    Yaml yaml = new Yaml(YamlHelper.getRepresenter(), YamlHelper.getDumperOptions());
    String dumpedYaml = yaml.dump(serviceYaml);

    // remove first line of Yaml:
    dumpedYaml = dumpedYaml.substring(dumpedYaml.indexOf('\n') + 1);

    YamlPayload yp = new YamlPayload(dumpedYaml);
    yp.setName(service.getName() + ".yaml");

    rr.setResponseMessages(yp.getResponseMessages());

    if (yp.getYaml() != null && !yp.getYaml().isEmpty()) {
      rr.setResource(yp);
    }

    return rr;
  }

  /**
   * Save the changes reflected in serviceYaml (in a JSON "wrapper")
   *
   * @param serviceId  the service id
   * @param yamlPayload the yaml version of service
   * @return the rest response
   */
  @POST
  @Path("/{serviceId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Application> saveFromYaml(@QueryParam("serviceId") String serviceId, YamlPayload yamlPayload) {
    String yaml = yamlPayload.getYaml();
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    RestResponse rr = new RestResponse<>();
    rr.setResponseMessages(yamlPayload.getResponseMessages());

    /* TODO
    Application app = null;

    if (yaml != null && !yaml.isEmpty()) {
      try {
        app = mapper.readValue(yaml, Application.class);
        app.setAccountId(accountId);

        app = setupYamlService.save(app);

        if (app != null) {
          rr.setResource(app);
        }
      } catch (Exception e) {
        addUnrecognizedFieldsMessage(rr);
      }
    }
    */

    return rr;
  }

  /**
   * Update a service that is sent as Yaml (in a JSON "wrapper")
   *
   * @param serviceId  the service id
   * @param yamlPayload the yaml version of service
   * @return the rest response
   */
  @PUT
  @Path("/{serviceId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Application> updateFromYaml(@QueryParam("serviceId") String serviceId, YamlPayload yamlPayload) {
    String yaml = yamlPayload.getYaml();
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    RestResponse rr = new RestResponse<>();
    rr.setResponseMessages(yamlPayload.getResponseMessages());

    /* TODO
    Application app = null;

    if (yaml != null && !yaml.isEmpty()) {
      try {
        app = mapper.readValue(yaml, Application.class);
        app.setUuid(appId);

        app = setupYamlService.update(app);

        if (app != null) {
          rr.setResource(app);
        }
      } catch (Exception e) {
        addUnrecognizedFieldsMessage(rr);
      }
    }
    */

    return rr;
  }
}
