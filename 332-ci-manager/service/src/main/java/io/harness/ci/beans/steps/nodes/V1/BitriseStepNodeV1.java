/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.nodes.V1;

import static io.harness.annotations.dev.HarnessTeam.CI;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.V1.BitriseStepInfoV1;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("bitrise")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.nodes.V1.BitriseStepNodeV1")
public class BitriseStepNodeV1 extends CIAbstractStepNode {
  @JsonProperty("type") @NotNull BitriseStepNodeV1.StepType type = StepType.BITRISE;
  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  BitriseStepInfoV1 bitriseStepInfoV1;

  @Override
  public String getType() {
    return CIStepInfoType.BITRISE_V1.getDisplayName();
  }

  @Override
  public StepSpecType getStepSpecType() {
    return bitriseStepInfoV1;
  }

  enum StepType {
    @JsonProperty("bitrise") BITRISE(CIStepInfoType.BITRISE_V1.getDisplayName());
    @Getter final String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
