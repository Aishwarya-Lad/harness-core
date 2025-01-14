/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.HeaderConfig;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.ng.core.dto.PollingTriggerStatusUpdateDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.BulkTriggersRequestDTO;
import io.harness.ngtriggers.beans.dto.BulkTriggersResponseDTO;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerYamlDiffDTO;
import io.harness.ngtriggers.beans.dto.WebhookEventProcessingDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogItem;
import io.harness.ngtriggers.beans.source.GitMoveOperationType;
import io.harness.ngtriggers.beans.source.TriggerUpdateCount;
import io.harness.ngtriggers.validations.ValidationResult;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(PIPELINE)
public interface NGTriggerService {
  NGTriggerEntity create(NGTriggerEntity ngTriggerEntity);

  Optional<NGTriggerEntity> get(String accountId, String orgIdentifier, String projectIdentifier,
      String targetIdentifier, String identifier, boolean deleted);

  NGTriggerEntity update(NGTriggerEntity ngTriggerEntity, NGTriggerEntity oldNgTriggerEntity);

  BulkTriggersResponseDTO toggleTriggers(boolean enable, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String type);

  boolean updateTriggerStatus(NGTriggerEntity ngTriggerEntity, boolean status);

  boolean updateTriggerPollingStatus(String accountId, PollingTriggerStatusUpdateDTO statusUpdate);

  Page<NGTriggerEntity> list(Criteria criteria, Pageable pageable);

  List<NGTriggerEntity> listEnabledTriggersForCurrentProject(
      String accountId, String orgIdentifier, String projectIdentifier);

  List<NGTriggerEntity> listEnabledTriggersForAccount(String accountId);

  List<NGTriggerEntity> findTriggersForCustomWehbook(
      TriggerWebhookEvent triggerWebhookEvent, boolean isDeleted, boolean enabled);

  Optional<NGTriggerEntity> findTriggersForCustomWebhookViaCustomWebhookToken(String webhookToken);

  List<NGTriggerEntity> findTriggersForWehbookBySourceRepoType(
      TriggerWebhookEvent triggerWebhookEvent, boolean isDeleted, boolean enabled);
  List<NGTriggerEntity> findBuildTriggersByAccountIdAndSignature(String accountId, List<String> signatures);
  boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String targetIdentifier,
      String identifier, Long version);

  TriggerWebhookEvent addEventToQueue(TriggerWebhookEvent webhookEventQueueRecord);
  TriggerWebhookEvent updateTriggerWebhookEvent(TriggerWebhookEvent webhookEventQueueRecord);
  void deleteTriggerWebhookEvent(TriggerWebhookEvent webhookEventQueueRecord);
  List<ConnectorResponseDTO> fetchConnectorsByFQN(String accountId, List<String> fqns);

  void validateTriggerConfig(TriggerDetails triggerDetails);
  boolean deleteAllForPipeline(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  WebhookEventProcessingDetails fetchTriggerEventHistory(String accountId, String eventId);
  NGTriggerEntity updateTriggerWithValidationStatus(
      NGTriggerEntity ngTriggerEntity, ValidationResult validationResult, boolean whileExecution);

  TriggerDetails fetchTriggerEntityV1(String accountId, String orgId, String projectId, String pipelineId,
      String triggerId, NGTriggerConfigV2 config, NGTriggerEntity entity);

  TriggerDetails fetchTriggerEntity(String accountId, String orgId, String projectId, String pipelineId,
      String triggerId, String newYaml, boolean withServiceV2);
  Object fetchExecutionSummaryV2(String planExecutionId, String accountId, String orgId, String projectId);

  List<TriggerCatalogItem> getTriggerCatalog(String accountIdentifier);

  void validatePipelineRef(TriggerDetails triggerDetails);

  void checkAuthorization(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, List<HeaderConfig> headerConfigs);
  TriggerYamlDiffDTO getTriggerYamlDiff(TriggerDetails triggerDetails);

  TriggerUpdateCount updateBranchName(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, GitMoveOperationType operationType, String pipelineBranchName);

  BulkTriggersResponseDTO toggleTriggersInBulk(String accountIdentifier, BulkTriggersRequestDTO bulkTriggersRequestDTO);
}
