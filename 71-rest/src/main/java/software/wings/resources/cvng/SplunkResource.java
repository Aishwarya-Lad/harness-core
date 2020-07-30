package software.wings.resources.cvng;

import static io.harness.cvng.core.services.CVNextGenConstants.SPLUNK_RESOURCE_PATH;
import static io.harness.cvng.core.services.CVNextGenConstants.SPLUNK_SAVED_SEARCH_PATH;
import static io.harness.cvng.core.services.CVNextGenConstants.SPLUNK_VALIDATION_RESPONSE_PATH;

import com.google.inject.Inject;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.beans.SplunkSavedSearch;
import io.harness.cvng.beans.SplunkValidationResponse;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.LearningEngineAuth;
import io.swagger.annotations.Api;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.splunk.SplunkAnalysisService;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api(SPLUNK_RESOURCE_PATH)
@Path(SPLUNK_RESOURCE_PATH)
@Produces("application/json")
@Scope(PermissionAttribute.ResourceType.SETTING)
@ExposeInternalException(withStackTrace = true)
@LearningEngineAuth
public class SplunkResource {
  @Inject private SplunkAnalysisService splunkAnalysisService;
  @GET
  @Path(SPLUNK_SAVED_SEARCH_PATH)
  @Timed
  @ExceptionMetered
  public RestResponse<List<SplunkSavedSearch>> getSavedSearches(@QueryParam("accountId") @Valid final String accountId,
      @QueryParam("connectorId") String connectorId, @QueryParam("requestGuid") @NotNull String requestGuid) {
    return new RestResponse<>(splunkAnalysisService.getSavedSearches(accountId, connectorId, requestGuid));
  }

  @GET
  @Path(SPLUNK_VALIDATION_RESPONSE_PATH)
  @Timed
  @ExceptionMetered
  public RestResponse<SplunkValidationResponse> getValidationResponse(
      @QueryParam("accountId") @Valid final String accountId, @QueryParam("connectorId") String connectorId,
      @QueryParam("query") String query, @QueryParam("requestGuid") @NotNull String requestGuid) {
    return new RestResponse<>(splunkAnalysisService.getValidationResponse(accountId, connectorId, query, requestGuid));
  }
}