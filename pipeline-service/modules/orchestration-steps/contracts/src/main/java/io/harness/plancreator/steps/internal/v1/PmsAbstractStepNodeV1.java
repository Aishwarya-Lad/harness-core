/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.plancreator.steps.common.v1.StepElementParametersV1;
import io.harness.plancreator.steps.common.v1.StepElementParametersV1.StepElementParametersV1Builder;
import io.harness.plancreator.steps.common.v1.StepParametersUtilsV1;
import io.harness.plancreator.steps.v1.AbstractStepNodeV1;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepUtils;
import io.harness.yaml.core.failurestrategy.v1.FailureConfigV1;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import lombok.Data;

@Data
@OwnedBy(PIPELINE)
public abstract class PmsAbstractStepNodeV1 extends AbstractStepNodeV1 {
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) ParameterField<List<FailureConfigV1>> failure;
  ParameterField<Timeout> timeout;

  // TODO: set rollback parameters
  public StepElementParametersV1 getStepParameters(PlanCreationContext ctx, KryoSerializer kryoSerializer) {
    StepElementParametersV1Builder stepBuilder = StepParametersUtilsV1.getStepParameters(this);
    if (getSpec() instanceof WithDelegateSelector) {
      StepUtils.appendDelegateSelectorsV1((WithDelegateSelector) getSpec(), ctx, kryoSerializer);
    }
    stepBuilder.spec(getSpecParameters());
    return stepBuilder.build();
  }

  public abstract SpecParameters getSpecParameters();
}
