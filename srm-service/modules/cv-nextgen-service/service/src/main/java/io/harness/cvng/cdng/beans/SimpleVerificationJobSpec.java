/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.job.Sensitivity;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.verificationjob.entities.SimpleVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob.RuntimeParameter;
import io.harness.cvng.verificationjob.entities.VerificationJob.VerificationJobBuilder;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@JsonTypeName("SimpleVerification")
@OwnedBy(HarnessTeam.CV)
@SuperBuilder
@NoArgsConstructor
public class SimpleVerificationJobSpec extends VerificationJobSpec {
  @Override
  public String getType() {
    return "SimpleVerification";
  }

  @Override
  public VerificationJobBuilder verificationJobBuilder() {
    return SimpleVerificationJob.builder().sensitivity(
        RuntimeParameter.builder().isRuntimeParam(false).value(Sensitivity.MEDIUM.getValue()).build());
  }

  @Override
  protected void validateParams() {}

  @Override
  protected ParameterField<String> getBaseline() {
    return null;
  }
  @Override
  public List<VerificationType> getSupportedDataTypesForVerification() {
    return List.of(VerificationType.TIME_SERIES);
  }
}