/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo.security;

import static io.harness.annotations.dev.HarnessTeam.STO;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.security.shared.STOGenericStepInfo;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlAuth;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlImage;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlMendToolData;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.TypeAlias;

@Data
@EqualsAndHashCode(callSuper = false)
@JsonTypeName("Mend")
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("mendStepInfo")
@OwnedBy(STO)
@RecasterAlias("io.harness.beans.steps.stepinfo.security.MendStepInfo")
public class MendStepInfo extends STOGenericStepInfo {
  private static final String PRODUCT_NAME = "whitesource";
  @JsonProperty protected STOYamlAuth auth;

  @JsonProperty("tool") protected STOYamlMendToolData tool;

  @JsonProperty protected STOYamlImage image;

  @ApiModelProperty(hidden = true)
  public String getProductName() {
    return PRODUCT_NAME;
  }
}
