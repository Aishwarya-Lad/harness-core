package software.wings.service.impl.appdynamics;

import static software.wings.beans.DelegateTask.Context.Builder.aContext;

import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.Base;
import software.wings.beans.DelegateTask.Context;
import software.wings.beans.ErrorCode;
import software.wings.beans.SettingAttribute;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.dl.WingsPersistence;
import software.wings.exception.WingsException;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.appdynamics.AppdynamicsDelegateService;
import software.wings.service.intfc.appdynamics.AppdynamicsService;
import software.wings.waitnotify.WaitNotifyEngine;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.validation.executable.ValidateOnExecution;

/**
 * Created by rsingh on 4/17/17.
 */
@ValidateOnExecution
public class AppdynamicsServiceImpl implements AppdynamicsService {
  private static final long APPDYNAMICS_CALL_TIMEOUT = TimeUnit.MINUTES.toMillis(1L);

  @com.google.inject.Inject private SettingsService settingsService;

  @Inject private WingsPersistence wingsPersistence;

  @Inject private DelegateProxyFactory delegateProxyFactory;

  @Inject private WaitNotifyEngine waitNotifyEngine;

  @Inject private DelegateService delegateService;

  @Override
  public List<AppdynamicsApplication> getApplications(final String settingId) throws IOException {
    final SettingAttribute settingAttribute = settingsService.get(settingId);
    Context context = aContext().withAccountId(settingAttribute.getAccountId()).withAppId(Base.GLOBAL_APP_ID).build();
    return delegateProxyFactory.get(AppdynamicsDelegateService.class, context)
        .getAllApplications((AppDynamicsConfig) settingAttribute.getValue());
  }

  @Override
  public List<AppdynamicsTier> getTiers(String settingId, int appdynamicsAppId) throws IOException {
    final SettingAttribute settingAttribute = settingsService.get(settingId);
    Context context = aContext().withAccountId(settingAttribute.getAccountId()).withAppId(Base.GLOBAL_APP_ID).build();
    return delegateProxyFactory.get(AppdynamicsDelegateService.class, context)
        .getTiers((AppDynamicsConfig) settingAttribute.getValue(), appdynamicsAppId);
  }

  @Override
  public List<AppdynamicsNode> getNodes(String settingId, int appdynamicsAppId, int tierId) throws IOException {
    final SettingAttribute settingAttribute = settingsService.get(settingId);
    Context context = aContext().withAccountId(settingAttribute.getAccountId()).withAppId(Base.GLOBAL_APP_ID).build();
    return delegateProxyFactory.get(AppdynamicsDelegateService.class, context)
        .getNodes((AppDynamicsConfig) settingAttribute.getValue(), appdynamicsAppId, tierId);
  }

  @Override
  public List<AppdynamicsBusinessTransaction> getBusinessTransactions(String settingId, long appdynamicsAppId)
      throws IOException {
    final SettingAttribute settingAttribute = settingsService.get(settingId);
    Context context = aContext().withAccountId(settingAttribute.getAccountId()).withAppId(Base.GLOBAL_APP_ID).build();

    return delegateProxyFactory.get(AppdynamicsDelegateService.class, context)
        .getBusinessTransactions((AppDynamicsConfig) settingAttribute.getValue(), appdynamicsAppId);
  }

  @Override
  public List<AppdynamicsMetric> getTierBTMetrics(String settingId, int appdynamicsAppId, int tierId)
      throws IOException {
    final SettingAttribute settingAttribute = settingsService.get(settingId);
    Context context = aContext().withAccountId(settingAttribute.getAccountId()).withAppId(Base.GLOBAL_APP_ID).build();
    context.setTimeOut(APPDYNAMICS_CALL_TIMEOUT);
    return delegateProxyFactory.get(AppdynamicsDelegateService.class, context)
        .getTierBTMetrics((AppDynamicsConfig) settingAttribute.getValue(), appdynamicsAppId, tierId);
  }

  @Override
  public List<AppdynamicsMetricData> getTierBTMetricData(
      String settingId, int appdynamicsAppId, int tierId, String btName, int durantionInMinutes) throws IOException {
    final SettingAttribute settingAttribute = settingsService.get(settingId);
    Context context = aContext().withAccountId(settingAttribute.getAccountId()).withAppId(Base.GLOBAL_APP_ID).build();
    context.setTimeOut(APPDYNAMICS_CALL_TIMEOUT);
    return delegateProxyFactory.get(AppdynamicsDelegateService.class, context)
        .getTierBTMetricData(
            (AppDynamicsConfig) settingAttribute.getValue(), appdynamicsAppId, tierId, btName, durantionInMinutes);
  }

  @Override
  public void validateConfig(final SettingAttribute settingAttribute) {
    try {
      Context context = aContext().withAccountId(settingAttribute.getAccountId()).withAppId(Base.GLOBAL_APP_ID).build();
      delegateProxyFactory.get(AppdynamicsDelegateService.class, context)
          .validateConfig((AppDynamicsConfig) settingAttribute.getValue());
    } catch (Exception e) {
      throw new WingsException(ErrorCode.APPDYNAMICS_CONFIGURATION_ERROR, "reason", e.getMessage());
    }
  }

  @Override
  public Boolean saveMetricData(
      String accountId, long appdynamicsAppId, long tierId, List<AppdynamicsMetricData> metricDataList) {
    for (AppdynamicsMetricData metricData : metricDataList) {
      List<AppdynamicsMetricDataRecord> metricDataRecords =
          AppdynamicsMetricDataRecord.generateDataRecords(accountId, appdynamicsAppId, tierId, metricData);
      wingsPersistence.saveIgnoringDuplicateKeys(metricDataRecords);
    }
    return true;
  }
}
