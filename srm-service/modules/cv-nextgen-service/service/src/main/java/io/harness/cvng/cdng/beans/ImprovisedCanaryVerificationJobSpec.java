/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.verificationjob.entities.BlueGreenVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob.VerificationJobBuilder;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@JsonTypeName("Rolling")
@OwnedBy(HarnessTeam.CV)
@SuperBuilder
@NoArgsConstructor
@RecasterAlias("io.harness.cvng.cdng.beans.ImprovisedCanaryVerificationJobSpec")
public class ImprovisedCanaryVerificationJobSpec extends BlueGreenCanaryVerificationJobSpec {
  @Override
  public String getType() {
    return "Rolling";
  }

  @Override
  protected VerificationJobBuilder verificationJobBuilder() {
    return addFieldValues(BlueGreenVerificationJob.builder());
  }

  @Override
  protected ParameterField<String> getBaseline() {
    return null;
  }
}
