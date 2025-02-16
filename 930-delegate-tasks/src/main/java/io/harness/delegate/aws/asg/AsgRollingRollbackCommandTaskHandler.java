/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static io.harness.aws.asg.manifest.AsgManifestType.AsgConfiguration;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgInstanceRefresh;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScalingPolicy;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScheduledUpdateGroupAction;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.aws.asg.AsgContentParser;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainFactory;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainState;
import io.harness.aws.asg.manifest.request.AsgConfigurationManifestRequest;
import io.harness.aws.asg.manifest.request.AsgInstanceRefreshManifestRequest;
import io.harness.aws.asg.manifest.request.AsgScalingPolicyManifestRequest;
import io.harness.aws.asg.manifest.request.AsgScheduledActionManifestRequest;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.AsgNGException;
import io.harness.delegate.task.aws.asg.AsgCommandRequest;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgRollingRollbackRequest;
import io.harness.delegate.task.aws.asg.AsgRollingRollbackResponse;
import io.harness.delegate.task.aws.asg.AsgRollingRollbackResult;
import io.harness.delegate.task.aws.asg.AsgTaskHelper;
import io.harness.delegate.task.aws.asg.AutoScalingGroupContainer;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.service.impl.AwsUtils;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class AsgRollingRollbackCommandTaskHandler extends AsgCommandTaskNGHandler {
  @Inject private AsgTaskHelper asgTaskHelper;
  @Inject private ElbV2Client elbV2Client;
  @Inject private AwsUtils awsUtils;

  @Override
  protected AsgCommandResponse executeTaskInternal(AsgCommandRequest asgCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws AsgNGException {
    if (!(asgCommandRequest instanceof AsgRollingRollbackRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("asgCommandRequest", "Must be instance of AsgRollingRollbackRequest"));
    }

    AsgRollingRollbackRequest asgRollingRollbackRequest = (AsgRollingRollbackRequest) asgCommandRequest;
    Map<String, List<String>> asgManifestsDataForRollback = asgRollingRollbackRequest.getAsgManifestsDataForRollback();
    String asgName = asgRollingRollbackRequest.getAsgName();
    Boolean skipMatching = true;
    Boolean useAlreadyRunningInstances = false;

    LogCallback logCallback = asgTaskHelper.getLogCallback(
        iLogStreamingTaskClient, AsgCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);

    try {
      AsgSdkManager asgSdkManager = asgTaskHelper.getAsgSdkManager(asgCommandRequest, logCallback, elbV2Client);
      AsgInfraConfig asgInfraConfig = asgCommandRequest.getAsgInfraConfig();
      String region = asgInfraConfig.getRegion();
      AwsInternalConfig awsInternalConfig = awsUtils.getAwsInternalConfig(asgInfraConfig.getAwsConnectorDTO(), region);

      asgSdkManager.info("Starting Rolling Rollback");

      AutoScalingGroupContainer autoScalingGroupContainer = executeRollingRollbackWithInstanceRefresh(asgSdkManager,
          asgManifestsDataForRollback, asgName, skipMatching, useAlreadyRunningInstances, awsInternalConfig, region);

      AsgRollingRollbackResult asgRollingRollbackResult =
          AsgRollingRollbackResult.builder().autoScalingGroupContainer(autoScalingGroupContainer).build();

      logCallback.saveExecutionLog(
          color("Rolling Rollback Finished Successfully", Green, Bold), INFO, CommandExecutionStatus.SUCCESS);

      return AsgRollingRollbackResponse.builder()
          .asgRollingRollbackResult(asgRollingRollbackResult)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();

    } catch (Exception e) {
      logCallback.saveExecutionLog(
          color(format("Rollback Failed"), LogColor.Red, LogWeight.Bold), ERROR, CommandExecutionStatus.FAILURE);
      log.error("Exception AsgRollingRollback", e);
      throw new AsgNGException(ExceptionMessageSanitizer.sanitizeException(e));
    }
  }

  @VisibleForTesting
  AutoScalingGroupContainer executeRollingRollbackWithInstanceRefresh(AsgSdkManager asgSdkManager,
      Map<String, List<String>> asgManifestsDataForRollback, String asgName, Boolean skipMatching,
      Boolean useAlreadyRunningInstances, AwsInternalConfig awsInternalConfig, String region) {
    if (isNotEmpty(asgManifestsDataForRollback)) {
      asgSdkManager.info("Rolling back to previous version of asg %s", asgName);

      boolean isInstanceRefreshInProgress = asgSdkManager.checkInstanceRefreshInProgress(asgName);

      if (isInstanceRefreshInProgress) {
        asgSdkManager.info("Instance refresh still in progress for ASG %s", asgName);
        asgSdkManager.deleteAsg(asgName);
      }

      // Get the content of all required manifest files
      String asgConfigurationContent = asgTaskHelper.getAsgConfigurationContent(asgManifestsDataForRollback);
      List<String> asgScalingPolicyContent = asgTaskHelper.getAsgScalingPolicyContent(asgManifestsDataForRollback);
      List<String> asgScheduledActionContent = asgTaskHelper.getAsgScheduledActionContent(asgManifestsDataForRollback);

      // Get ASG name from asg configuration manifest
      CreateAutoScalingGroupRequest createAutoScalingGroupRequest =
          AsgContentParser.parseJson(asgConfigurationContent, CreateAutoScalingGroupRequest.class, false);

      AsgManifestHandlerChainState initialChainState = AsgManifestHandlerChainState.builder().asgName(asgName).build();

      if (isNotEmpty(createAutoScalingGroupRequest.getLaunchTemplate().getVersion())) {
        initialChainState.setLaunchTemplateName(
            createAutoScalingGroupRequest.getLaunchTemplate().getLaunchTemplateName());
        initialChainState.setLaunchTemplateVersion(createAutoScalingGroupRequest.getLaunchTemplate().getVersion());
      }

      // Chain factory code to handle each manifest one by one in a chain
      AsgManifestHandlerChainFactory manifestHandlerChainFactory =
          (AsgManifestHandlerChainFactory) AsgManifestHandlerChainFactory.builder()
              .initialChainState(initialChainState)
              .asgSdkManager(asgSdkManager)
              .build()
              .addHandler(AsgConfiguration,
                  AsgConfigurationManifestRequest.builder()
                      .manifests(Arrays.asList(asgConfigurationContent))
                      .useAlreadyRunningInstances(useAlreadyRunningInstances)
                      .awsInternalConfig(awsInternalConfig)
                      .region(region)
                      .build())
              .addHandler(AsgScalingPolicy,
                  AsgScalingPolicyManifestRequest.builder().manifests(asgScalingPolicyContent).build())
              .addHandler(AsgScheduledUpdateGroupAction,
                  AsgScheduledActionManifestRequest.builder().manifests(asgScheduledActionContent).build());

      if (!isInstanceRefreshInProgress) {
        manifestHandlerChainFactory.addHandler(
            AsgInstanceRefresh, AsgInstanceRefreshManifestRequest.builder().skipMatching(skipMatching).build());
      }

      AsgManifestHandlerChainState chainState = manifestHandlerChainFactory.executeUpsert();

      AutoScalingGroup autoScalingGroup = chainState.getAutoScalingGroup();
      asgSdkManager.infoBold("Rolled back to previous version of asg %s successfully", asgName);

      return asgTaskHelper.mapToAutoScalingGroupContainer(autoScalingGroup);
    } else {
      asgSdkManager.deleteAsg(asgName);
      return AutoScalingGroupContainer.builder().build();
    }
  }
}
