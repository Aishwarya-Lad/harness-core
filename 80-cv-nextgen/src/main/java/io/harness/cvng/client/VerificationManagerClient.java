package io.harness.cvng.client;

import static io.harness.cvng.core.services.CVNextGenConstants.CV_DATA_COLLECTION_PATH;
import static io.harness.cvng.core.services.CVNextGenConstants.CV_NEXTGEN_RESOURCE_PREFIX;
import static io.harness.cvng.core.services.CVNextGenConstants.SPLUNK_RESOURCE_PATH;
import static io.harness.cvng.core.services.CVNextGenConstants.SPLUNK_SAVED_SEARCH_PATH;
import static io.harness.cvng.core.services.CVNextGenConstants.SPLUNK_VALIDATION_RESPONSE_PATH;

import io.harness.cvng.beans.AppdynamicsValidationResponse;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.beans.SplunkSavedSearch;
import io.harness.cvng.beans.SplunkValidationResponse;
import io.harness.rest.RestResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.container.ContainerRequestContext;

public interface VerificationManagerClient {
  @GET("account/feature-flag-enabled")
  Call<RestResponse<Boolean>> isFeatureEnabled(
      @Query("featureName") String featureName, @Query("accountId") String accountId);

  @POST(CV_DATA_COLLECTION_PATH + "/create-task")
  Call<RestResponse<String>> create(@Query("accountId") String accountId, @Body Map<String, String> params);

  @DELETE(CV_DATA_COLLECTION_PATH + "/delete-task")
  Call<RestResponse<Void>> deleteDataCollectionTask(
      @Query("accountId") String accountId, @Query("taskId") String taskId);

  @GET(SPLUNK_RESOURCE_PATH + SPLUNK_SAVED_SEARCH_PATH)
  Call<RestResponse<List<SplunkSavedSearch>>> getSavedSearches(@Query("accountId") String accountId,
      @Query("connectorId") String connectorId, @Query("requestGuid") String requestGuid);

  @GET(SPLUNK_RESOURCE_PATH + SPLUNK_VALIDATION_RESPONSE_PATH)
  Call<RestResponse<SplunkValidationResponse>> getSamples(@Query("accountId") String accountId,
      @Query("connectorId") String connectorId, @Query("query") String query, @Query("requestGuid") String requestGuid);

  @GET(CV_NEXTGEN_RESOURCE_PREFIX + "/auth/validate-token")
  Call<RestResponse<Boolean>> authenticateUser(
      @Query("containerRequestContext") ContainerRequestContext containerRequestContext);
  @POST("appdynamics"
      + "/metric-data")

  Call<RestResponse<Set<AppdynamicsValidationResponse>>>
  getAppDynamicsMetricData(@Query("accountId") String accountId, @Query("projectIdentifier") String projectIdentifier,
      @Query("connectorId") String connectorId, @Query("appdAppId") long appdAppId,
      @Query("appdTierId") long appdTierId, @Query("requestGuid") String requestGuid,
      @Body List<MetricPackDTO> metricPacks);
}
