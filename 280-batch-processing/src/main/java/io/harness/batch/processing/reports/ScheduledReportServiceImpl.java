package io.harness.batch.processing.reports;

import static software.wings.graphql.datafetcher.billing.CloudBillingHelper.unified;
import static software.wings.security.UserThreadLocal.userGuard;

import com.google.inject.Singleton;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.mail.CEMailNotificationService;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.beans.EmbeddedUser;
import io.harness.ccm.billing.bigquery.BigQueryService;
import io.harness.ccm.views.entities.CEReportSchedule;
import io.harness.ccm.views.service.impl.CEReportScheduleServiceImpl;
import io.harness.ccm.views.service.impl.CEReportTemplateBuilderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.graphql.datafetcher.billing.CloudBillingHelper;
import software.wings.helpers.ext.mail.EmailData;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Singleton
@Slf4j
public class ScheduledReportServiceImpl {
  @Autowired private CEReportScheduleServiceImpl ceReportScheduleService;
  @Autowired private CEReportTemplateBuilderServiceImpl ceReportTemplateBuilderService;
  @Autowired private AccountShardService accountShardService;
  @Autowired private BatchMainConfig config;
  @Autowired private CEMailNotificationService emailNotificationService;
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Autowired private BigQueryService bigQueryService;
  @Autowired private CloudBillingHelper cloudBillingHelper;

  // TODO: CE_VIEW_URL, MAIL_SUBJECT to be finalized
  private static final String CE_VIEW_URL = "/account/%s/continuous-efficiency/views-explorer/%s";
  private static final String MAIL_SUBJECT = "Your Continous Efficiency scheduled report is here";
  private static final String URL = "url";
  private int scheduleCount;

  public void generateAndSendScheduledReport() {
    scheduleCount = 0;
    if (!config.getReportScheduleConfig().isEnabled()) {
      logger.warn("Batch job for ce scheduled report is disabled!");
      return;
    }
    // TODO: Change info to debug post integration with UI
    List<Account> ceEnabledAccounts = accountShardService.getCeEnabledAccounts();
    List<String> accountIds = ceEnabledAccounts.stream().map(Account::getUuid).collect(Collectors.toList());
    logger.info("ceEnabledAccounts ids list {}", accountIds);
    Date jobTime = new Date();
    logger.info("jobTime {}", jobTime.toInstant());

    accountIds.forEach(accountId -> {
      // N mongo calls for N accounts
      List<CEReportSchedule> schedules = ceReportScheduleService.getAllMatchingSchedules(accountId, jobTime);
      logger.info("Found schedules(total {}) : {} for account {}", schedules.size(), schedules, accountId);
      scheduleCount += schedules.size();
      schedules.forEach(schedule -> {
        for (String viewId : schedule.getViewsId()) {
          try {
            sendMail(accountId, schedule.getRecipients(), viewId, schedule.getUuid());
            setNextExecution(accountId, schedule);
          } catch (Exception e) {
            logger.info("ERROR in generateAndSendScheduledReport for viewId {}, accountId {}, reportId {}", viewId,
                accountId, schedule.getUuid());
            logger.error("ERROR", e);
            continue;
          }
        }
      });
    });
    logger.info("A total of {} reportSchedules were processed", scheduleCount);
  }

  private void sendMail(String accountId, String[] recipients, String viewId, String reportId) {
    Map<String, String> templateModel =
        ceReportTemplateBuilderService.getTemplatePlaceholders(accountId, viewId, reportId, bigQueryService.get(),
            cloudBillingHelper.getCloudProviderTableName(
                config.getBillingDataPipelineConfig().getGcpProjectId(), accountId, unified));
    String viewUrl = "";
    try {
      viewUrl = emailNotificationService.buildAbsoluteUrl(String.format(CE_VIEW_URL, accountId, viewId));
    } catch (URISyntaxException e) {
      logger.error("Error in forming View URL for Scheduled Report", e);
    }
    templateModel.put(URL, viewUrl);
    EmailData emailData = EmailData.builder()
                              .to(Arrays.asList(recipients))
                              .templateName(config.getReportScheduleConfig().getTemplateName())
                              .templateModel(templateModel)
                              .accountId(accountId)
                              .build();
    emailData.setCc(Collections.emptyList());
    emailData.setSubject(MAIL_SUBJECT);
    emailData.setRetries(0);
    // TODO: This opens new connection every time. Makes things slow. Modify this to use connection pools with sessions.
    emailNotificationService.send(emailData);
  }

  public void setNextExecution(String accountId, CEReportSchedule schedule) {
    // This is needed to keep lastUpdatedBy same as before.
    // Get existing user uuid from the record in collection. This can be null.
    EmbeddedUser euser = schedule.getLastUpdatedBy();
    // Get User record for this uuid.
    if (euser != null) {
      User user = cloudToHarnessMappingService.getUser(euser.getUuid());
      // Set this in threadlocal so that downstream update in Mongo works fine
      try (UserThreadLocal.Guard guard = userGuard(user)) {
        ceReportScheduleService.updateNextExecution(accountId, schedule);
      }
    } else {
      ceReportScheduleService.updateNextExecution(accountId, schedule);
    }
  }
}
