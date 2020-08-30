package io.harness.serializer.morphia;

import static io.harness.mongo.ClassRefactoringManager.movementsToMyModule;
import static io.harness.mongo.HObjectFactory.checkRegisteredClasses;
import static io.harness.rule.OwnerRule.GEORGE;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import io.harness.category.element.UnitTests;
import io.harness.ccm.cluster.entities.Cluster;
import io.harness.rule.Owner;
import io.harness.waiter.NotifyCallback;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.map.HashedMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import software.wings.WingsBaseTest;
import software.wings.api.DeploymentInfo;
import software.wings.beans.SecretManagerConfig;
import software.wings.beans.alert.AlertData;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.common.PartitionProcessorTest.SampleElement;
import software.wings.infra.InfraMappingInfrastructureProvider;
import software.wings.service.impl.WorkflowExecutionUpdateFake;
import software.wings.service.impl.analysis.DataCollectionInfo;
import software.wings.service.impl.analysis.LogDataCollectionInfoV2;
import software.wings.settings.SettingValue;
import software.wings.sm.ContextElement;
import software.wings.sm.State;
import software.wings.sm.StateMachineExecutionCallback;
import software.wings.sm.StateMachineExecutionCallbackMock;
import software.wings.sm.StateMachineTest.StateAsync;
import software.wings.sm.StateMachineTest.StateSync;
import software.wings.sm.StepExecutionSummary;
import software.wings.verification.CVConfiguration;

import java.util.Map;
import java.util.Set;
@Slf4j
public class ManagerMorphiaRegistrarTest extends WingsBaseTest {
  @Inject @Named("morphiaInterfaceImplementersClasses") Map<String, Class> morphiaInterfaceImplementers;

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testManagerImplementationClasses() {
    Map<String, Class> classes = new HashedMap(morphiaInterfaceImplementers);

    classes.put("software.wings.common.PartitionProcessorTest$SampleElement", SampleElement.class);
    classes.put("software.wings.sm.StateMachineTest$StateSync", StateSync.class);
    classes.put("software.wings.sm.StateMachineTest$StateAsync", StateAsync.class);
    classes.put("software.wings.sm.StateMachineExecutionCallbackMock", StateMachineExecutionCallbackMock.class);
    classes.put("software.wings.service.impl.WorkflowExecutionUpdateFake", WorkflowExecutionUpdateFake.class);

    final Set<Class> unwanted = checkRegisteredClasses(ImmutableSet.<Class>builder()
                                                           .add(AlertData.class)
                                                           .add(CommandUnit.class)
                                                           .add(ContextElement.class)
                                                           .add(DataCollectionInfo.class)
                                                           .add(SecretManagerConfig.class)
                                                           .add(SettingValue.class)
                                                           .add(State.class)
                                                           .add(StateMachineExecutionCallback.class)
                                                           .add(StepExecutionSummary.class)
                                                           .add(InfraMappingInfrastructureProvider.class)
                                                           .add(LogDataCollectionInfoV2.class)
                                                           .add(NotifyCallback.class)
                                                           .add(CVConfiguration.class)
                                                           .add(InstanceInfo.class)
                                                           .add(Cluster.class)
                                                           .add(DeploymentInfo.class)
                                                           .build(),
        classes);

    assertThat(unwanted).isEmpty();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testManagerKnownMovements() {
    ImmutableMap<Object, Object> known = ImmutableMap.builder().build();

    Map<String, String> myModuleMovements = movementsToMyModule(this.getClass(), morphiaInterfaceImplementers);

    assertThat(myModuleMovements).isEqualTo(known);
  }
}
