/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.LogCallback;

import io.kubernetes.client.openapi.ApiClient;
import java.lang.reflect.Type;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@OwnedBy(HarnessTeam.CDP)
public class WorkloadDetails {
  Type workloadType;
  ApiClient apiClient;
  KubernetesResourceId k8sWorkload;
  LogCallback logCallback;
  boolean errorFramework;

  public WorkloadDetails(Type type, ApiClient apiClient, KubernetesResourceId k8sWorkload, LogCallback logCallback,
      boolean errorFramework) {
    this.workloadType = type;
    this.apiClient = apiClient;
    this.k8sWorkload = k8sWorkload;
    this.logCallback = logCallback;
    this.errorFramework = errorFramework;
  }
}
