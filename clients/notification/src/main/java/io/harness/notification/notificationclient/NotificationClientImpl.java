/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.notificationclient;

import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.NotificationRequest;
import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.messageclient.MessageClient;
import io.harness.notification.model.NotificationRuleReferenceDTO;
import io.harness.notification.remote.NotificationHTTPClient;
import io.harness.notification.remote.dto.EmailDTO;
import io.harness.notification.remote.dto.NotificationRequestDTO;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.remote.dto.TemplateDTO;
import io.harness.notification.templates.PredefinedTemplate;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;

@Getter
@Setter
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NotificationClientImpl implements NotificationClient {
  private MessageClient messageClient;
  private NotificationHTTPClient notificationHTTPClient;

  @Override
  public NotificationResult sendNotificationAsync(NotificationChannel notificationChannel) {
    NotificationRequest notificationRequest = notificationChannel.buildNotificationRequest();

    this.messageClient.send(notificationRequest, notificationChannel.getAccountId());
    return NotificationResultWithoutStatus.builder().notificationId(notificationRequest.getId()).build();
  }

  @Override
  public Response<ResponseDTO<NotificationTaskResponse>> sendNotificationSync(NotificationChannel notificationChannel)
      throws IOException {
    NotificationRequest notificationRequest = notificationChannel.buildNotificationRequest();
    NotificationRequestDTO notificationRequestDTO =
        NotificationRequestDTO.builder().bytes(notificationRequest.toByteArray()).build();
    return notificationHTTPClient.sendNotificationSync(notificationRequestDTO).execute();
  }

  public Response<ResponseDTO<NotificationTaskResponse>> sendEmail(EmailDTO emailDTO) throws IOException {
    return notificationHTTPClient.sendEmail(emailDTO).execute();
  }

  @Override
  public List<NotificationResult> sendBulkNotificationAsync(List<NotificationChannel> notificationChannels) {
    return notificationChannels.stream().map(this::sendNotificationAsync).collect(Collectors.toList());
  }

  @Override
  public boolean testNotificationChannel(NotificationSettingDTO notificationSettingDTO) {
    return getResponse(notificationHTTPClient.testChannelSettings(notificationSettingDTO));
  }

  @Override
  public TemplateDTO saveNotificationTemplate(Team team, PredefinedTemplate template, Boolean harnessManaged) {
    String filePath = template.getPath();
    String identifier = template.getIdentifier();

    byte[] bytes;
    try {
      URL url = getClass().getClassLoader().getResource(filePath);
      bytes = Resources.toByteArray(url);
    } catch (IOException e) {
      log.error("Unexpected error while saving notification template", e);
      return null;
    }
    final MultipartBody.Part formData =
        MultipartBody.Part.createFormData("file", null, RequestBody.create(MultipartBody.FORM, bytes));
    return getResponse(notificationHTTPClient.saveNotificationTemplate(formData, team, identifier, harnessManaged));
  }

  @Override
  public NotificationResult sendNotificationTrigger(NotificationTriggerRequest notificationTriggerRequest) {
    messageClient.send(notificationTriggerRequest);
    return NotificationResultWithoutStatus.builder().notificationId(notificationTriggerRequest.getId()).build();
  }

  @Override
  public List<NotificationResult> sendBulkNotificationTrigger(
      List<NotificationTriggerRequest> notificationTriggerRequest) {
    return notificationTriggerRequest.stream().map(this::sendNotificationTrigger).collect(Collectors.toList());
  }

  @Override
  public NotificationRuleReferenceDTO getNotificationRule(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String notificationEntity, String notificationEvent) {
    return getResponse(notificationHTTPClient.getNotificationRule(
        accountIdentifier, orgIdentifier, projectIdentifier, notificationEntity, notificationEvent));
  }

  @Override
  public boolean isDefaultSMTPPresent(String accountId) {
    return getResponse(notificationHTTPClient.isDefaultSMTPPresent(accountId));
  }
}
