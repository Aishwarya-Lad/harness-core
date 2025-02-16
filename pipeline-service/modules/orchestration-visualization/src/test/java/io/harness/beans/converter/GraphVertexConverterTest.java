/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans.converter;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.DelegateInfoHelper;
import io.harness.OrchestrationVisualizationTestBase;
import io.harness.beans.GraphVertex;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.category.element.UnitTests;
import io.harness.execution.NodeExecution;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.rule.Owner;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class GraphVertexConverterTest extends OrchestrationVisualizationTestBase {
  StepType stepType = StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build();
  Map<String, Object> stepParams = new HashMap<>() {
    { put("a", "b"); }
  };
  Ambiance ambiance = Ambiance.newBuilder()
                          .setPlanExecutionId("planUuid")
                          .addLevels(Level.newBuilder()
                                         .setStepType(stepType)
                                         .setNodeType(NodeType.PLAN_NODE.name())
                                         .setSetupId("planUuid")
                                         .setSetupId("setUpId")
                                         .setIdentifier("identifier")
                                         .build())
                          .build();
  @Mock private DelegateInfoHelper delegateInfoHelper;
  @InjectMocks private GraphVertexConverter graphVertexConverter;

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testConvertFromNodeExecutionToGraphVertex() {
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid("uuid")
                                      .ambiance(ambiance)
                                      .name("name")
                                      .startTs(10L)
                                      .endTs(20L)
                                      .initialWaitDuration(Duration.ZERO)
                                      .lastUpdatedAt(40L)
                                      .status(Status.FAILED)
                                      .stepType(stepType)
                                      .retryId("retryId")
                                      .executionInputConfigured(true)
                                      .mode(ExecutionMode.SYNC)
                                      .build();
    GraphVertex graphVertex = graphVertexConverter.convertFrom(nodeExecution, null);
    assertThat(graphVertex).isNotNull();
    assertThat(graphVertex.getUuid()).isEqualTo(nodeExecution.getUuid());
    assertThat(graphVertex.getCurrentLevel()).isEqualTo(AmbianceUtils.obtainCurrentLevel(ambiance));
    assertThat(graphVertex.getPlanNodeId()).isEqualTo("setUpId");
    assertThat(graphVertex.getIdentifier()).isEqualTo("identifier");
    assertThat(graphVertex.getStartTs()).isEqualTo(10L);
    assertThat(graphVertex.getEndTs()).isEqualTo(20L);
    assertThat(graphVertex.getLastUpdatedAt()).isEqualTo(40L);
    assertThat(graphVertex.getInitialWaitDuration()).isEqualTo(Duration.ZERO);
    assertThat(graphVertex.getStepType()).isEqualTo("DUMMY");
    assertThat(graphVertex.getMode()).isEqualTo(ExecutionMode.SYNC);
    assertThat(graphVertex.getRetryIds()).isEqualTo(nodeExecution.getRetryIds());
    assertThat(graphVertex.getStatus()).isEqualTo(Status.FAILED);
    assertThat(graphVertex.getExecutionInputConfigured()).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testConvertFromNodeExecutionWithNodeExecutionInfoAsNullToGraphVertex() {
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid("uuid")
                                      .ambiance(ambiance)
                                      .name("name")
                                      .startTs(10L)
                                      .endTs(20L)
                                      .initialWaitDuration(Duration.ZERO)
                                      .lastUpdatedAt(40L)
                                      .status(Status.FAILED)
                                      .stepType(stepType)
                                      .retryId("retryId")
                                      .executionInputConfigured(true)
                                      .resolvedParams(PmsStepParameters.parse(stepParams))
                                      .mode(ExecutionMode.SYNC)
                                      .build();
    GraphVertex graphVertex = graphVertexConverter.convertFrom(nodeExecution, null, null, null);
    assertThat(graphVertex).isNotNull();
    assertThat(graphVertex.getUuid()).isEqualTo(nodeExecution.getUuid());
    assertThat(graphVertex.getCurrentLevel()).isEqualTo(AmbianceUtils.obtainCurrentLevel(ambiance));
    assertThat(graphVertex.getPlanNodeId()).isEqualTo("setUpId");
    assertThat(graphVertex.getIdentifier()).isEqualTo("identifier");
    assertThat(graphVertex.getStartTs()).isEqualTo(10L);
    assertThat(graphVertex.getEndTs()).isEqualTo(20L);
    assertThat(graphVertex.getLastUpdatedAt()).isEqualTo(40L);
    assertThat(graphVertex.getInitialWaitDuration()).isEqualTo(Duration.ZERO);
    assertThat(graphVertex.getStepType()).isEqualTo("DUMMY");
    assertThat(graphVertex.getMode()).isEqualTo(ExecutionMode.SYNC);
    assertThat(graphVertex.getRetryIds()).isEqualTo(nodeExecution.getRetryIds());
    assertThat(graphVertex.getStatus()).isEqualTo(Status.FAILED);
    assertThat(graphVertex.getExecutionInputConfigured()).isTrue();
    assertThat(graphVertex.getStepParameters().size()).isEqualTo(1);
    assertThat(graphVertex.getStepParameters().get("a")).isEqualTo("b");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testConvertFromNodeExecutionWithNodeExecutionInfoToGraphVertex() {
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid("uuid")
                                      .ambiance(ambiance)
                                      .name("name")
                                      .startTs(10L)
                                      .endTs(20L)
                                      .initialWaitDuration(Duration.ZERO)
                                      .lastUpdatedAt(40L)
                                      .status(Status.FAILED)
                                      .stepType(stepType)
                                      .retryId("retryId")
                                      .executionInputConfigured(true)
                                      .resolvedStepParameters(stepParams)
                                      .mode(ExecutionMode.SYNC)
                                      .build();
    GraphVertex graphVertex = graphVertexConverter.convertFrom(nodeExecution, null,
        NodeExecutionsInfo.builder().resolvedInputs(PmsStepParameters.parse(stepParams)).build(), null);
    assertThat(graphVertex).isNotNull();
    assertThat(graphVertex.getUuid()).isEqualTo(nodeExecution.getUuid());
    assertThat(graphVertex.getCurrentLevel()).isEqualTo(AmbianceUtils.obtainCurrentLevel(ambiance));
    assertThat(graphVertex.getPlanNodeId()).isEqualTo("setUpId");
    assertThat(graphVertex.getIdentifier()).isEqualTo("identifier");
    assertThat(graphVertex.getStartTs()).isEqualTo(10L);
    assertThat(graphVertex.getEndTs()).isEqualTo(20L);
    assertThat(graphVertex.getLastUpdatedAt()).isEqualTo(40L);
    assertThat(graphVertex.getInitialWaitDuration()).isEqualTo(Duration.ZERO);
    assertThat(graphVertex.getStepType()).isEqualTo("DUMMY");
    assertThat(graphVertex.getMode()).isEqualTo(ExecutionMode.SYNC);
    assertThat(graphVertex.getRetryIds()).isEqualTo(nodeExecution.getRetryIds());
    assertThat(graphVertex.getStatus()).isEqualTo(Status.FAILED);
    assertThat(graphVertex.getExecutionInputConfigured()).isTrue();
    assertThat(graphVertex.getStepParameters().size()).isEqualTo(1);
    assertThat(graphVertex.getStepParameters().get("a")).isEqualTo("b");
  }
}
