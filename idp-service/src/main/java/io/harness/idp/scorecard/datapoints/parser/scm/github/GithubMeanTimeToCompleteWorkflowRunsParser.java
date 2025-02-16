/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.scm.github;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_CONDITIONAL_INPUT;

import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.DateUtils;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GithubMeanTimeToCompleteWorkflowRunsParser implements DataPointParser {
  private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

  @Override
  public Object parseDataPoint(Map<String, Object> data, DataFetchDTO dataFetchDTO) {
    Map<String, Object> dataPointData = new HashMap<>();
    List<InputValue> inputValues = dataFetchDTO.getInputValues();
    if (inputValues.size() != 1) {
      dataPointData.putAll(constructDataPointInfo(dataFetchDTO, null, INVALID_CONDITIONAL_INPUT));
    }
    data = (Map<String, Object>) data.get(dataFetchDTO.getRuleIdentifier());

    if (isEmpty(data) || !isEmpty((String) data.get(ERROR_MESSAGE_KEY))) {
      String errorMessage = (String) data.get(ERROR_MESSAGE_KEY);
      dataPointData.putAll(constructDataPointInfo(
          dataFetchDTO, null, !isEmpty(errorMessage) ? errorMessage : INVALID_CONDITIONAL_INPUT));
      return dataPointData;
    }

    List<Map<String, Object>> runs = (List<Map<String, Object>>) CommonUtils.findObjectByName(data, "workflow_runs");
    if (isEmpty(runs)) {
      dataPointData.putAll(constructDataPointInfo(dataFetchDTO, null, "No workflow runs found"));
      return dataPointData;
    }

    int numberOfRuns = runs.size();
    long totalTimeToComplete = 0;
    for (Map<String, Object> run : runs) {
      long createdAtMillis = DateUtils.parseTimestamp((String) run.get("created_at"), DATE_FORMAT);
      long completedAtMillis = DateUtils.parseTimestamp((String) run.get("updated_at"), DATE_FORMAT);
      long timeToCompleteMillis = completedAtMillis - createdAtMillis;
      totalTimeToComplete += timeToCompleteMillis;
    }

    double meanTimeToCompleteMillis = (double) totalTimeToComplete / numberOfRuns;
    long value = (long) (meanTimeToCompleteMillis / (60 * 1000));
    dataPointData.putAll(constructDataPointInfo(dataFetchDTO, value, null));
    return dataPointData;
  }
}
