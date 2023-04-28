/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine;

import static io.harness.pms.contracts.execution.events.OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE;
import static io.harness.pms.contracts.execution.events.OrchestrationEventType.ORCHESTRATION_END;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.expressions.usages.ExpressionUsagesEventHandler;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.steps.PodCleanupUpdateEventHandler;

import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationExecutionPmsEventHandlerRegistrar {
  public static Map<OrchestrationEventType, Set<Class<? extends OrchestrationEventHandler>>> getEngineEventHandlers() {
    Map<OrchestrationEventType, Set<Class<? extends OrchestrationEventHandler>>> handlerMap = new HashMap<>();
    handlerMap.put(ORCHESTRATION_END, Sets.newHashSet(ExpressionUsagesEventHandler.class));
    handlerMap.put(NODE_EXECUTION_STATUS_UPDATE, Sets.newHashSet(PodCleanupUpdateEventHandler.class));

    return handlerMap;
  }
}