/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.ng.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppRequestType;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@OwnedBy(CDP)
@EqualsAndHashCode(callSuper = true)
public class AzureWebAppSwapSlotsRequest extends AbstractWebAppTaskRequest {
  String targetSlot;
  private Integer timeoutIntervalInMin;

  @Builder
  public AzureWebAppSwapSlotsRequest(String accountId, CommandUnitsProgress commandUnitsProgress,
      AzureWebAppInfraDelegateConfig infrastructure, String targetSlot, Integer timeoutIntervalInMin) {
    super(accountId, commandUnitsProgress, infrastructure);
    this.targetSlot = targetSlot;
    this.timeoutIntervalInMin = timeoutIntervalInMin;
  }

  @Override
  public AzureWebAppRequestType getRequestType() {
    return AzureWebAppRequestType.SWAP_SLOTS;
  }
}
