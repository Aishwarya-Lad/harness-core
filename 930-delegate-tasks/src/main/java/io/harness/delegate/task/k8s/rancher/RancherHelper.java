/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.rancher;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.rancher.RancherConnectorBearerTokenAuthenticationDTO;
import io.harness.delegate.task.k8s.RancherK8sInfraDelegateConfig;
import io.harness.k8s.model.KubernetesConfig;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@UtilityClass
public class RancherHelper {
  public String getRancherUrl(RancherK8sInfraDelegateConfig rancherK8sInfraDelegateConfig) {
    if (rancherUrlExists(rancherK8sInfraDelegateConfig)) {
      return rancherK8sInfraDelegateConfig.getRancherConnectorDTO().getConfig().getConfig().getRancherUrl();
    }
    return StringUtils.EMPTY;
  }

  public String getRancherBearerToken(RancherK8sInfraDelegateConfig rancherK8sInfraDelegateConfig) {
    if (rancherBearerTokenExists(rancherK8sInfraDelegateConfig)) {
      return String.valueOf(getBearerTokenFromRancherConnector(rancherK8sInfraDelegateConfig));
    }
    return StringUtils.EMPTY;
  }

  public String getKubeConfigTokenName(KubernetesConfig kubernetesConfig) {
    if (kubernetesConfig == null) {
      return StringUtils.EMPTY;
    }
    String rancherAccessAndSecretKey = kubernetesConfig.getServiceAccountTokenSupplier().get();
    String[] keys = rancherAccessAndSecretKey.split(":", 2);
    if (keys.length == 2) {
      return keys[0];
    }
    return StringUtils.EMPTY;
  }

  private boolean rancherUrlExists(RancherK8sInfraDelegateConfig rancherK8sInfraDelegateConfig) {
    if (isRancherConnectorConfigEmpty(rancherK8sInfraDelegateConfig)) {
      return false;
    }
    return isNotEmpty(rancherK8sInfraDelegateConfig.getRancherConnectorDTO().getConfig().getConfig().getRancherUrl());
  }

  private boolean rancherBearerTokenExists(RancherK8sInfraDelegateConfig rancherK8sInfraDelegateConfig) {
    if (isRancherConnectorConfigEmpty(rancherK8sInfraDelegateConfig)) {
      return false;
    }
    if (rancherK8sInfraDelegateConfig.getRancherConnectorDTO().getConfig().getConfig().getCredentials() == null) {
      return false;
    }
    if (rancherK8sInfraDelegateConfig.getRancherConnectorDTO().getConfig().getConfig().getCredentials().getAuth()
        == null) {
      return false;
    }

    char[] decryptedBearerToken = getBearerTokenFromRancherConnector(rancherK8sInfraDelegateConfig);
    return isNotEmpty(String.valueOf(decryptedBearerToken));
  }

  private char[] getBearerTokenFromRancherConnector(RancherK8sInfraDelegateConfig rancherK8sInfraDelegateConfig) {
    return ((RancherConnectorBearerTokenAuthenticationDTO) rancherK8sInfraDelegateConfig.getRancherConnectorDTO()
                .getConfig()
                .getConfig()
                .getCredentials()
                .getAuth())
        .getPasswordRef()
        .getDecryptedValue();
  }

  private boolean isRancherConnectorConfigEmpty(RancherK8sInfraDelegateConfig rancherK8sInfraDelegateConfig) {
    if (rancherK8sInfraDelegateConfig == null) {
      return true;
    }
    if (rancherK8sInfraDelegateConfig.getRancherConnectorDTO() == null) {
      return true;
    }
    if (rancherK8sInfraDelegateConfig.getRancherConnectorDTO().getConfig() == null) {
      return true;
    }
    return rancherK8sInfraDelegateConfig.getRancherConnectorDTO().getConfig().getConfig() == null;
  }
}
