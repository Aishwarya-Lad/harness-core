/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.terraformcloudconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.terraformcloudconnector.outcome.TerraformCloudTokenCredentialsOutcomeDTO;
import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@JsonTypeName(TerraformCloudConstants.API_TOKEN)
@ApiModel("TerraformCloudTokenCredentials")
@Schema(name = "TerraformCloudTokenCredentials",
    description = "This contains Terraform Cloud API TOKEN credentials connector details")
public class TerraformCloudTokenCredentialsDTO implements TerraformCloudCredentialSpecDTO {
  @ApiModelProperty(dataType = "string") @NotNull @JsonProperty("apiToken") @SecretReference SecretRefData apiToken;

  @Override
  public TerraformCloudTokenCredentialsOutcomeDTO toOutcome() {
    return TerraformCloudTokenCredentialsOutcomeDTO.builder().apiToken(this.apiToken).build();
  }
}
