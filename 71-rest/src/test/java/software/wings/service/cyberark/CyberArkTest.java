package software.wings.service.cyberark;

import static io.harness.persistence.HQuery.excludeAuthority;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.inject.Inject;

import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.RealMongo;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.KryoUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.CyberArkConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.KmsConfig;
import software.wings.beans.LocalEncryptionConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.User;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.dl.WingsPersistence;
import software.wings.features.api.PremiumFeature;
import software.wings.resources.CyberArkResource;
import software.wings.resources.SecretManagementResource;
import software.wings.security.UserThreadLocal;
import software.wings.security.encryption.EncryptedData;
import software.wings.security.encryption.EncryptedData.EncryptedDataKeys;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.security.CyberArkService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.KmsService;
import software.wings.service.intfc.security.LocalEncryptionService;
import software.wings.service.intfc.security.SecretManagementDelegateService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.security.SecretManagerConfigService;
import software.wings.settings.SettingValue.SettingVariableTypes;
import software.wings.settings.UsageRestrictions;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * @author marklu on 2019-08-09
 */
@RunWith(Parameterized.class)
public class CyberArkTest extends WingsBaseTest {
  @Inject private CyberArkResource cyberArkResource;
  @Inject private SecretManagementResource secretManagementResource;
  @Mock private AccountService accountService;
  @Inject @InjectMocks private KmsService kmsService;
  @Inject @InjectMocks private CyberArkService cyberArkService;
  @Inject @InjectMocks private SecretManagerConfigService secretManagerConfigService;
  @Inject private LocalEncryptionService localEncryptionService;
  @Inject private EncryptionService encryptionService;
  @Inject private SecretManager secretManager;
  @Inject private WingsPersistence wingsPersistence;
  @Mock private SecretManagementDelegateService secretManagementDelegateService;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private PremiumFeature secretsManagementFeature;
  private final int numOfEncryptedValsForCyberArk = 1;
  private final int numOfEncryptedValsForCyberKms = 3;
  private final String userEmail = "mark.lu@harness.io";
  private final String userName = "raghu";
  private final User user = User.Builder.anUser().withEmail(userEmail).withName(userName).build();
  private String userId;
  private String accountId;
  private String kmsId;
  private KmsConfig kmsConfig;
  private LocalEncryptionConfig localEncryptionConfig;

  @Parameter public boolean isGlobalKmsEnabled;

  @Parameters
  public static Collection<Object[]> data() {
    return asList(new Object[][] {{true}, {false}});
  }

  @Before
  public void setup() throws IOException, NoSuchFieldException, IllegalAccessException {
    initMocks(this);

    Account account = getAccount(AccountType.PAID);
    accountId = account.getUuid();
    when(accountService.get(accountId)).thenReturn(account);

    when(secretsManagementFeature.isAvailableForAccount(accountId)).thenReturn(true);

    when(secretManagementDelegateService.decrypt(anyObject(), any(KmsConfig.class))).then(invocation -> {
      Object[] args = invocation.getArguments();
      return decrypt((EncryptedData) args[0], (KmsConfig) args[1]);
    });

    when(secretManagementDelegateService.decrypt(anyObject(), any(CyberArkConfig.class))).then(invocation -> {
      Object[] args = invocation.getArguments();
      return decrypt((EncryptedData) args[0], (CyberArkConfig) args[1]);
    });
    when(delegateProxyFactory.get(eq(SecretManagementDelegateService.class), any(SyncTaskContext.class)))
        .thenReturn(secretManagementDelegateService);
    when(secretManagementDelegateService.encrypt(anyString(), anyObject(), any(KmsConfig.class))).then(invocation -> {
      Object[] args = invocation.getArguments();
      return encrypt((String) args[0], (char[]) args[1], (KmsConfig) args[2]);
    });

    FieldUtils.writeField(cyberArkService, "delegateProxyFactory", delegateProxyFactory, true);
    FieldUtils.writeField(kmsService, "delegateProxyFactory", delegateProxyFactory, true);
    FieldUtils.writeField(secretManager, "kmsService", kmsService, true);
    FieldUtils.writeField(secretManager, "cyberArkService", cyberArkService, true);
    FieldUtils.writeField(wingsPersistence, "secretManager", secretManager, true);
    FieldUtils.writeField(cyberArkResource, "cyberArkService", cyberArkService, true);
    FieldUtils.writeField(secretManagementResource, "secretManager", secretManager, true);
    userId = wingsPersistence.save(user);
    UserThreadLocal.set(user);

    if (isGlobalKmsEnabled) {
      kmsConfig = getKmsConfig();
      kmsConfig.setName("Global KMS");
      kmsConfig.setAccountId(Account.GLOBAL_ACCOUNT_ID);
      kmsId = kmsService.saveGlobalKmsConfig(accountId, kmsConfig);
      kmsConfig = kmsService.getKmsConfig(accountId, kmsId);
    } else {
      localEncryptionConfig = localEncryptionService.getEncryptionConfig(accountId);
    }
  }

  @Test
  @Category(UnitTests.class)
  public void validateConfig() {
    CyberArkConfig cyberArkConfig = getCyberArkConfig("invalidCertificate");
    cyberArkConfig.setAccountId(accountId);

    try {
      cyberArkResource.saveCyberArkConfig(cyberArkConfig.getAccountId(), cyberArkConfig);
      fail("Saved invalid CyberArk config");
    } catch (WingsException e) {
      assertThat(true).isTrue();
    }

    cyberArkConfig = getCyberArkConfig();
    cyberArkConfig.setAccountId(accountId);
    cyberArkConfig.setCyberArkUrl("invalidUrl");

    try {
      cyberArkResource.saveCyberArkConfig(cyberArkConfig.getAccountId(), cyberArkConfig);
      fail("Saved invalid CyberArk config");
    } catch (WingsException e) {
      assertThat(true).isTrue();
    }
  }

  @Test
  @Category(UnitTests.class)
  public void getCyberArkConfigForAccount() {
    CyberArkConfig cyberArkConfig = getCyberArkConfig();
    cyberArkConfig.setAccountId(accountId);

    cyberArkResource.saveCyberArkConfig(cyberArkConfig.getAccountId(), cyberArkConfig);

    CyberArkConfig savedConfig =
        (CyberArkConfig) secretManagerConfigService.getDefaultSecretManager(cyberArkConfig.getAccountId());
    assertEquals(cyberArkConfig.getName(), savedConfig.getName());
    assertEquals(cyberArkConfig.getAccountId(), savedConfig.getAccountId());
  }

  @Test
  @Category(UnitTests.class)
  public void saveAndEditConfig() {
    InputStream inputStream = CyberArkTest.class.getResourceAsStream("/certs/clientCert.pem");
    String clientCertificate = null;
    try {
      clientCertificate = IOUtils.toString(inputStream, "UTF-8");
    } catch (IOException e) {
      // Should not happen.
    }

    CyberArkConfig cyberArkConfig = saveCyberArkConfig(clientCertificate);
    String name = cyberArkConfig.getName();

    CyberArkConfig savedConfig =
        (CyberArkConfig) secretManagerConfigService.getDefaultSecretManager(cyberArkConfig.getAccountId());
    assertEquals(name, savedConfig.getName());
    List<EncryptedData> encryptedDataList =
        wingsPersistence.createQuery(EncryptedData.class, excludeAuthority).asList();
    if (isGlobalKmsEnabled) {
      assertEquals(numOfEncryptedValsForCyberArk + numOfEncryptedValsForCyberKms, encryptedDataList.size());
    } else {
      assertEquals(numOfEncryptedValsForCyberArk, encryptedDataList.size());
      for (EncryptedData encryptedData : encryptedDataList) {
        assertEquals(encryptedData.getName(), name + "_clientCertificate");
        assertEquals(1, encryptedData.getParentIds().size());
        assertEquals(savedConfig.getUuid(), encryptedData.getParentIds().iterator().next());
      }
    }

    name = UUID.randomUUID().toString();
    CyberArkConfig newConfig = getCyberArkConfig();
    savedConfig.setClientCertificate(newConfig.getClientCertificate());
    savedConfig.setName(name);
    cyberArkResource.saveCyberArkConfig(accountId, savedConfig);
    encryptedDataList =
        wingsPersistence.createQuery(EncryptedData.class).filter(EncryptedDataKeys.accountId, accountId).asList();
    assertEquals(numOfEncryptedValsForCyberArk, encryptedDataList.size());
    for (EncryptedData encryptedData : encryptedDataList) {
      assertEquals(1, encryptedData.getParentIds().size());
      assertEquals(savedConfig.getUuid(), encryptedData.getParentIds().iterator().next());
    }
  }

  @Test
  @Category(UnitTests.class)
  @RealMongo
  public void testEncryptDecryptArtifactoryConfig() {
    String queryAsPath = "Address=components;Username=svc_account";
    String secretName = "TestSecret";
    String secretValue = "MySecretValue";

    saveCyberArkConfig();

    // Encrypt of path reference will use a CyberArk decryption to validate the reference
    EncryptedData encryptedData = secretManager.encrypt(EncryptionType.CYBERARK, accountId,
        SettingVariableTypes.ARTIFACTORY, null, queryAsPath, null, secretName, new UsageRestrictions());
    assertNotNull(encryptedData);
    assertEquals(EncryptionType.CYBERARK, encryptedData.getEncryptionType());
    assertEquals(SettingVariableTypes.ARTIFACTORY, encryptedData.getType());
    assertThat(encryptedData.getEncryptedValue()).isNull();

    // Encrypt of real secret text will use a LOCAL Harness SecretStore to encrypt, since CyberArk doesn't support
    // creating new reference now.
    encryptedData = secretManager.encrypt(EncryptionType.CYBERARK, accountId, SettingVariableTypes.ARTIFACTORY,
        secretValue.toCharArray(), null, null, secretName, new UsageRestrictions());
    assertNotNull(encryptedData);
    if (isGlobalKmsEnabled) {
      assertEquals(EncryptionType.KMS, encryptedData.getEncryptionType());
      assertEquals(kmsId, encryptedData.getKmsId());
    } else {
      assertEquals(EncryptionType.LOCAL, encryptedData.getEncryptionType());
    }
    assertEquals(SettingVariableTypes.ARTIFACTORY, encryptedData.getType());
    assertNotNull(encryptedData.getEncryptedValue());
  }

  @Test
  @Category(UnitTests.class)
  @RealMongo
  public void testEncryptDecryptJenkinsConfig() {
    saveCyberArkConfig();

    String password = UUID.randomUUID().toString();
    JenkinsConfig jenkinsConfig = getJenkinsConfig(accountId, password);
    SettingAttribute settingAttribute = getSettingAttribute(jenkinsConfig);
    String savedAttributeId = wingsPersistence.save(KryoUtils.clone(settingAttribute));

    SettingAttribute savedAttribute = wingsPersistence.get(SettingAttribute.class, savedAttributeId);
    JenkinsConfig savedJenkinsConfig = (JenkinsConfig) savedAttribute.getValue();

    List<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptionDetails(savedJenkinsConfig, accountId, null);
    assertEquals(2, encryptedDataDetails.size());

    char[] decryptedValue;

    EncryptedData encryptedPasswordData =
        wingsPersistence.get(EncryptedData.class, savedJenkinsConfig.getEncryptedPassword());
    assertNotNull(encryptedPasswordData);
    assertEquals(SettingVariableTypes.JENKINS, encryptedPasswordData.getType());
    if (isGlobalKmsEnabled) {
      assertEquals(EncryptionType.KMS, encryptedPasswordData.getEncryptionType());
      assertEquals(kmsId, encryptedPasswordData.getKmsId());
      decryptedValue = kmsService.decrypt(encryptedPasswordData, accountId, kmsConfig);
    } else {
      assertEquals(EncryptionType.LOCAL, encryptedPasswordData.getEncryptionType());
      decryptedValue = localEncryptionService.decrypt(encryptedPasswordData, accountId, localEncryptionConfig);
    }

    assertNotNull(decryptedValue);
    assertEquals(new String(jenkinsConfig.getPassword()), new String(decryptedValue));

    EncryptedData encryptedTokenData =
        wingsPersistence.get(EncryptedData.class, savedJenkinsConfig.getEncryptedToken());
    assertNotNull(encryptedTokenData);
    assertEquals(SettingVariableTypes.JENKINS, encryptedTokenData.getType());
    if (isGlobalKmsEnabled) {
      assertEquals(EncryptionType.KMS, encryptedTokenData.getEncryptionType());
      assertEquals(kmsId, encryptedTokenData.getKmsId());
      decryptedValue = kmsService.decrypt(encryptedTokenData, accountId, kmsConfig);
    } else {
      assertEquals(EncryptionType.LOCAL, encryptedTokenData.getEncryptionType());
      decryptedValue = localEncryptionService.decrypt(encryptedTokenData, accountId, localEncryptionConfig);
    }

    assertEquals(null, decryptedValue);
  }

  private CyberArkConfig saveCyberArkConfig() {
    return saveCyberArkConfig(null);
  }

  private CyberArkConfig saveCyberArkConfig(String clientCertificate) {
    String name = UUID.randomUUID().toString();
    CyberArkConfig cyberArkConfig = getCyberArkConfig();
    cyberArkConfig.setName(name);
    cyberArkConfig.setAccountId(accountId);
    cyberArkConfig.setClientCertificate(clientCertificate);

    cyberArkResource.saveCyberArkConfig(cyberArkConfig.getAccountId(), cyberArkConfig);
    return cyberArkConfig;
  }
}
