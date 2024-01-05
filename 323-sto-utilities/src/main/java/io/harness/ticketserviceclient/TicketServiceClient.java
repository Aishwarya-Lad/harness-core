/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ticketserviceclient;

import io.harness.common.STOCommonEndpointConstants;

import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface TicketServiceClient {
  @GET(STOCommonEndpointConstants.TICKET_SERVICE_TOKEN_ENDPOINT)
  Call<JsonObject> generateTokenAllAccounts(@Header("X-Harness-Token") String globalToken);

  @GET(STOCommonEndpointConstants.TICKET_SERVICE_TOKEN_ENDPOINT)
  Call<JsonObject> generateToken(@Query("accountId") String accountId, @Header("X-Harness-Token") String globalToken);

  @DELETE(STOCommonEndpointConstants.TICKET_SERVICE_DELETE_ACCOUNT_DATA_ENDPOINT)
  Call<JsonObject> deleteAccountData(@Header("Authorization") String token, @Path("accountId") String accountId);

  @GET(STOCommonEndpointConstants.TICKET_SERVICE_ALL_EXTERNAL_TICKETS)
  Call<JsonObject> getAllExternalTickets(@Header("Authorization") String token, @Query("accountId") String accountId,
                                         @Query("page") String page, @Query("pageSize") String pageSize,
                                         @Query("orgId") String orgId, @Query("projectId") String projectId,
                                         @Query("module") String module, @Query("identifiers") String identifiers,
                                         @Query("optional") String optional, @Query("externalId") String externalId);
}
