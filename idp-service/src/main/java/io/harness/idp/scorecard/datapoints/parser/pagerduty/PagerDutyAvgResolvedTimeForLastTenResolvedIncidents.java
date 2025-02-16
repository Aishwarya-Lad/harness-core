/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.pagerduty;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import com.google.gson.internal.LinkedTreeMap;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class PagerDutyAvgResolvedTimeForLastTenResolvedIncidents implements DataPointParser {
  private static final String INCIDENTS_RESPONSE_KEY = "incidents";

  @Override
  public Object parseDataPoint(Map<String, Object> data, DataFetchDTO dataFetchDTO) {
    data = (Map<String, Object>) data.get(dataFetchDTO.getRuleIdentifier());
    String errorMessage = (String) data.get(ERROR_MESSAGE_KEY);
    if (!isEmpty(errorMessage)) {
      return constructDataPointInfo(dataFetchDTO, null, errorMessage);
    }

    List<LinkedTreeMap> incidents = new ArrayList<>();

    if (CommonUtils.findObjectByName(data, INCIDENTS_RESPONSE_KEY) != null) {
      incidents = (ArrayList) CommonUtils.findObjectByName(data, INCIDENTS_RESPONSE_KEY);
    }

    int noOfIncidentsForCalculation = Math.min(incidents.size(), 10);

    long sumOfResolvedTime = 0;
    for (int i = 0; i < noOfIncidentsForCalculation; i++) {
      sumOfResolvedTime += getDifferenceBetweenTimeInHours(
          incidents.get(i).get("created_at").toString(), incidents.get(i).get("resolved_at").toString());
    }

    if (noOfIncidentsForCalculation == 0) {
      return constructDataPointInfo(dataFetchDTO, 0, null);
    }

    return constructDataPointInfo(dataFetchDTO, sumOfResolvedTime / noOfIncidentsForCalculation, null);
  }

  private long getDifferenceBetweenTimeInHours(String createdAtTime, String resolvedTime) {
    Instant createdAtTimeParsed = Instant.parse(createdAtTime);
    Instant resolvedTimeParsed = Instant.parse(resolvedTime);

    // Convert Instant objects to ZonedDateTime
    ZonedDateTime createdAtTimeZoned = createdAtTimeParsed.atZone(ZoneId.of("UTC"));
    ZonedDateTime resolvedTimeParsedZoned = resolvedTimeParsed.atZone(ZoneId.of("UTC"));

    // Calculate the difference in hours
    Duration duration = Duration.between(createdAtTimeZoned, resolvedTimeParsedZoned);
    return duration.toMinutes();
  }
}