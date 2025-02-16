/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("azureWebAppSlotDeploymentStepParameters")
@RecasterAlias("io.harness.cdng.azure.webapp.AzureWebAppSlotDeploymentStepParameters")
public class AzureWebAppSlotDeploymentStepParameters
    extends AzureWebAppSlotDeploymentBaseStepInfo implements SpecParameters, AzureWebAppStepParameters {
  ParameterField<String> webApp;
  ParameterField<String> deploymentSlot;
  ParameterField<Boolean> clean;
  @Builder(builderMethodName = "infoBuilder")
  public AzureWebAppSlotDeploymentStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<String> webApp, ParameterField<String> deploymentSlot, ParameterField<Boolean> clean) {
    super(delegateSelectors);
    this.webApp = webApp;
    this.deploymentSlot = deploymentSlot;
    this.clean = clean;
  }
}
