/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.cdng.provision.terraform.TerraformStepHelper.OPTIONAL_VAR_FILES;
import static io.harness.cdng.provision.terraform.TerraformStepHelper.TF_BACKEND_CONFIG_FILE;
import static io.harness.cdng.provision.terraform.TerraformStepHelper.TF_CONFIG_FILES;
import static io.harness.cdng.provision.terraform.TerraformStepHelper.TF_ENCRYPTED_JSON_OUTPUT_NAME;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.yaml.TerraformCommandFlagType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.provision.ProvisionerOutputHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3StoreTFDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.delegate.task.terraform.InlineTerraformVarFileInfo;
import io.harness.delegate.task.terraform.RemoteTerraformVarFileInfo;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.delegate.task.terraform.TerraformVarFileInfo;
import io.harness.delegate.task.terraform.provider.TerraformAwsProviderCredentialDelegateInfo;
import io.harness.delegate.task.terraform.provider.TerraformProviderType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.telemetry.helpers.DeploymentsInstrumentationHelper;
import io.harness.telemetry.helpers.StepExecutionTelemetryEventDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.powermock.core.classloader.annotations.PrepareForTest;

@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({TaskRequestsUtils.class})
@OwnedBy(HarnessTeam.CDP)
public class TerraformApplyStepV2Test extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private KryoSerializer kryoSerializer;
  @Mock private TerraformStepHelper terraformStepHelper;
  @Mock private TerraformConfigHelper terraformConfigHelper;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @InjectMocks private TerraformApplyStepV2 terraformApplyStepV2;
  @Mock private StepHelper stepHelper;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private ProvisionerOutputHelper provisionerOutputHelper;
  @Mock private DeploymentsInstrumentationHelper deploymentsInstrumentationHelper;

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "test-account")
        .putSetupAbstractions("projectIdentifier", "test-project")
        .putSetupAbstractions("orgIdentifier", "test-org")
        .build();
  }

  @Captor ArgumentCaptor<List<EntityDetail>> captor;

  @Before
  public void setUpMocks() {
    doNothing().when(provisionerOutputHelper).saveProvisionerOutputByStepIdentifier(any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testValidateResourcesWithGithubStore() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();
    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("VarFiles/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();

    TerraformApplyStepParameters applyStepParameters =
        TerraformStepDataGenerator.generateApplyStepPlan(StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    terraformApplyStepV2.validateResources(ambiance, stepElementParameters);
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(ambiance), captor.capture(), eq(true));

    assertThat("true").isEqualTo("true");
    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(2);
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("terraform");
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(1).getEntityRef().getIdentifier()).isEqualTo("terraform");
    assertThat(entityDetails.get(1).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testValidateResourcesWithArtifactoryStore() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreConfigFiles =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder()
            .connectorRef("connectorRef")
            .repositoryName("repositoryPath")
            .build();
    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreVarFiles =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder()
            .connectorRef("connectorRef2")
            .repositoryName("repositoryPathtoVars")
            .build();

    TerraformApplyStepParameters applyStepParameters = TerraformStepDataGenerator.generateApplyStepPlan(
        StoreConfigType.ARTIFACTORY, artifactoryStoreConfigFiles, artifactoryStoreVarFiles);
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    terraformApplyStepV2.validateResources(ambiance, stepElementParameters);
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(ambiance), captor.capture(), eq(true));

    assertThat("true").isEqualTo("true");
    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(2);
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("connectorRef");
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(1).getEntityRef().getIdentifier()).isEqualTo("connectorRef2");
    assertThat(entityDetails.get(1).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test(expected = NullPointerException.class)
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testValidateResourcesNegativeScenario() {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("provId_$"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INLINE)
                               .spec(TerraformExecutionDataParameters.builder().build())
                               .build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    terraformApplyStepV2.validateResources(ambiance, stepElementParameters);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkWithGithub() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();

    TerraformApplyStepParameters applyStepParameters =
        TerraformStepDataGenerator.generateApplyStepPlanWithInlineVarFiles(
            StoreConfigType.GITHUB, gitStoreConfigFiles, false);

    applyStepParameters.getConfiguration().getIsSkipTerraformRefresh().setValue(true);
    applyStepParameters.getConfiguration().setCliOptions(
        List.of(TerraformCliOptionFlag.builder()
                    .commandType(TerraformCommandFlagType.APPLY)
                    .flag(ParameterField.createValueField("-lock-timeout=0s"))
                    .build()));

    GitConfigDTO gitConfigDTO = GitConfigDTO.builder()
                                    .gitAuthType(GitAuthType.HTTP)
                                    .gitConnectionType(GitConnectionType.ACCOUNT)
                                    .delegateSelectors(Collections.singleton("delegateName"))
                                    .url("https://github.com/wings-software")
                                    .branchName("master")
                                    .build();
    applyStepParameters.getConfiguration().setSkipStateStorage(ParameterField.createValueField(true));
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder().branch("master").connectorName("terraform").gitConfigDTO(gitConfigDTO).build();
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder()
                                                  .identifier("terraform")
                                                  .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                                  .succeedIfFileNotFound(false)
                                                  .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("APPLY", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent("var-file-inline").build());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfoWithIdentifierAndManifest(any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(false).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    doReturn(TerraformAwsProviderCredentialDelegateInfo.builder().roleArn("roleArn").build())
        .when(terraformStepHelper)
        .getProviderCredentialDelegateInfo(any(), any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformApplyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();
    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);
    assertThat(taskParameters.isSkipTerraformRefresh()).isTrue();
    assertThat(taskParameters.getTerraformCommandFlags().get("APPLY")).isEqualTo("-lock-timeout=0s");
    assertThat(taskParameters.isSkipStateStorage()).isTrue();

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles).isFalse();
    assertThat(terraformPassThroughData.hasS3Files).isFalse();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(0)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());

    assertThat(taskParameters.getProviderCredentialDelegateInfo()).isNotNull();
    assertThat(taskParameters.getProviderCredentialDelegateInfo().getType()).isEqualTo(TerraformProviderType.AWS);
    TerraformAwsProviderCredentialDelegateInfo awsCredentialDelegateInfo =
        (TerraformAwsProviderCredentialDelegateInfo) taskParameters.getProviderCredentialDelegateInfo();
    assertThat(awsCredentialDelegateInfo.getRoleArn()).isEqualTo("roleArn");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkWithGithubWithRemoteWarFile() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();

    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("VarFiles/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();

    TerraformApplyStepParameters applyStepParameters =
        TerraformStepDataGenerator.generateApplyStepPlan(StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);

    applyStepParameters.getConfiguration().getIsSkipTerraformRefresh().setValue(true);
    applyStepParameters.getConfiguration().setCliOptions(
        List.of(TerraformCliOptionFlag.builder()
                    .commandType(TerraformCommandFlagType.APPLY)
                    .flag(ParameterField.createValueField("-lock-timeout=0s"))
                    .build()));

    GitConfigDTO gitConfigDTO = GitConfigDTO.builder()
                                    .gitAuthType(GitAuthType.HTTP)
                                    .gitConnectionType(GitConnectionType.ACCOUNT)
                                    .delegateSelectors(Collections.singleton("delegateName"))
                                    .url("https://github.com/wings-software")
                                    .branchName("master")
                                    .build();
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder().branch("master").connectorName("terraform").gitConfigDTO(gitConfigDTO).build();
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder()
                                                  .identifier("terraform")
                                                  .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                                  .succeedIfFileNotFound(false)
                                                  .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("APPLY", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(RemoteTerraformVarFileInfo.builder().gitFetchFilesConfig(gitFetchFilesConfig).build());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfoWithIdentifierAndManifest(any(), any());

    doReturn(varFileInfo).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(true).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformApplyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .fetchRemoteVarFiles(tfPassThroughDataArgumentCaptor.capture(), any(), any(), any(), any(), any());

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles).isTrue();
    assertThat(terraformPassThroughData.hasS3Files).isFalse();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(1)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
    verify(terraformStepHelper, times(0)).executeTerraformTask(any(), any(), any(), any(), any(), any());

    TerraformTaskNGParameters taskParameters = terraformPassThroughData.getTerraformTaskNGParametersBuilder().build();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);
    assertThat(taskParameters.isSkipTerraformRefresh()).isTrue();
    assertThat(taskParameters.getTerraformCommandFlags().get("APPLY")).isEqualTo("-lock-timeout=0s");
    assertThat(taskParameters.getConfigFile()).isNotNull();
    assertThat(taskParameters.isSkipStateStorage()).isFalse();
    assertThat(((RemoteTerraformVarFileInfo) taskParameters.getVarFileInfos().get(0)).getGitFetchFilesConfig())
        .isNotNull();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkWithGithubWithRemoteWarFileS3() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();

    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("VarFiles/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();

    TerraformApplyStepParameters applyStepParameters =
        TerraformStepDataGenerator.generateApplyStepPlan(StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);

    applyStepParameters.getConfiguration().getIsSkipTerraformRefresh().setValue(true);
    applyStepParameters.getConfiguration().setCliOptions(
        List.of(TerraformCliOptionFlag.builder()
                    .commandType(TerraformCommandFlagType.APPLY)
                    .flag(ParameterField.createValueField("-lock-timeout=0s"))
                    .build()));

    GitConfigDTO gitConfigDTO = GitConfigDTO.builder()
                                    .gitAuthType(GitAuthType.HTTP)
                                    .gitConnectionType(GitConnectionType.ACCOUNT)
                                    .delegateSelectors(Collections.singleton("delegateName"))
                                    .url("https://github.com/wings-software")
                                    .branchName("master")
                                    .build();
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder().branch("master").connectorName("terraform").gitConfigDTO(gitConfigDTO).build();
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder()
                                                  .identifier("terraform")
                                                  .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                                  .succeedIfFileNotFound(false)
                                                  .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("APPLY", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(RemoteTerraformVarFileInfo.builder()
                        .filestoreFetchFilesConfig(S3StoreTFDelegateConfig.builder()
                                                       .bucketName("test-bucket")
                                                       .region("test-region")
                                                       .path("test-path")
                                                       .connectorDTO(ConnectorInfoDTO.builder().build())
                                                       .build())
                        .build());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfoWithIdentifierAndManifest(any(), any());

    doReturn(varFileInfo).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(false).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(true).when(terraformStepHelper).hasS3VarFiles(any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformApplyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .fetchRemoteVarFiles(tfPassThroughDataArgumentCaptor.capture(), any(), any(), any(), any(), any());

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles).isFalse();
    assertThat(terraformPassThroughData.hasS3Files).isTrue();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(1)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
    verify(terraformStepHelper, times(0)).executeTerraformTask(any(), any(), any(), any(), any(), any());

    TerraformTaskNGParameters taskParameters = terraformPassThroughData.getTerraformTaskNGParametersBuilder().build();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);
    assertThat(taskParameters.isSkipTerraformRefresh()).isTrue();
    assertThat(taskParameters.getTerraformCommandFlags().get("APPLY")).isEqualTo("-lock-timeout=0s");
    assertThat(taskParameters.getConfigFile()).isNotNull();
    assertThat(((RemoteTerraformVarFileInfo) taskParameters.getVarFileInfos().get(0)).getFilestoreFetchFilesConfig())
        .isNotNull();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContext() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();

    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("VarFiles/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();

    TerraformApplyStepParameters applyStepParameters =
        TerraformStepDataGenerator.generateApplyStepPlan(StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder()
            .hasS3Files(true)
            .terraformTaskNGParametersBuilder(TerraformTaskNGParameters.builder())
            .build();

    doReturn(TaskChainResponse.builder().taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeNextLink(eq(ambiance), any(), eq(terraformPassThroughData), any(), eq(stepElementParameters), any());

    GitFetchResponse gitFetchResponse = GitFetchResponse.builder().build();

    terraformApplyStepV2.executeNextLinkWithSecurityContext(ambiance, stepElementParameters,
        StepInputPackage.builder().build(), terraformPassThroughData, () -> gitFetchResponse);

    verify(terraformStepHelper, times(1)).executeNextLink(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkWithGithubWhenTFCloudCli() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();

    TerraformApplyStepParameters applyStepParameters =
        TerraformStepDataGenerator.generateApplyStepPlanWithInlineVarFiles(
            StoreConfigType.GITHUB, gitStoreConfigFiles, false);
    applyStepParameters.getConfiguration().getSpec().getIsTerraformCloudCli().setValue(true);

    applyStepParameters.getConfiguration().getIsSkipTerraformRefresh().setValue(true);
    applyStepParameters.getConfiguration().setCliOptions(
        List.of(TerraformCliOptionFlag.builder()
                    .commandType(TerraformCommandFlagType.APPLY)
                    .flag(ParameterField.createValueField("-lock-timeout=0s"))
                    .build()));

    GitConfigDTO gitConfigDTO = GitConfigDTO.builder()
                                    .gitAuthType(GitAuthType.HTTP)
                                    .gitConnectionType(GitConnectionType.ACCOUNT)
                                    .delegateSelectors(Collections.singleton("delegateName"))
                                    .url("https://github.com/wings-software")
                                    .branchName("master")
                                    .build();
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder().branch("master").connectorName("terraform").gitConfigDTO(gitConfigDTO).build();
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder()
                                                  .identifier("terraform")
                                                  .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                                  .succeedIfFileNotFound(false)
                                                  .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("APPLY", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent("var-file-inline").build());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfoWithIdentifierAndManifest(any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(false).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformApplyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();
    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);
    assertThat(taskParameters.getEncryptionConfig()).isNull();
    assertThat(taskParameters.getWorkspace()).isNull();
    assertThat(taskParameters.isTerraformCloudCli()).isTrue();
    assertThat(taskParameters.isSkipTerraformRefresh()).isTrue();
    assertThat(taskParameters.getTerraformCommandFlags().get("APPLY")).isEqualTo("-lock-timeout=0s");

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles).isFalse();
    assertThat(terraformPassThroughData.hasS3Files).isFalse();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(0)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkWithArtifactory() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreConfigFiles =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder()
            .connectorRef("connectorRef")
            .repositoryName("repositoryPath")
            .build();

    TerraformApplyStepParameters applyStepParameters =
        TerraformStepDataGenerator.generateApplyStepPlanWithInlineVarFiles(
            StoreConfigType.ARTIFACTORY, artifactoryStoreConfigFiles, false);

    ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig =
        TerraformStepDataGenerator.createStoreDelegateConfig();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(artifactoryStoreDelegateConfig)
        .when(terraformStepHelper)
        .getFileStoreFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent("var-file-inline").build());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfoWithIdentifierAndManifest(any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(false).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformApplyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();
    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);
    assertThat(taskParameters.getFileStoreConfigFiles().getConnectorDTO().getConnectorType().toString())
        .isEqualTo(ConnectorType.ARTIFACTORY.toString());

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles).isFalse();
    assertThat(terraformPassThroughData.hasS3Files).isFalse();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(0)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
  }

  @Test(expected = NullPointerException.class) // configFile is Absent
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkNegativeScenario() {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INLINE)
                               .spec(TerraformExecutionDataParameters.builder().build())
                               .build())
            .build();
    GitFetchFilesConfig gitFetchFilesConfig =
        GitFetchFilesConfig.builder().identifier("terraform").succeedIfFileNotFound(false).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());

    terraformApplyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkInheritPlan() {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_PLAN)
                               .commandFlags(List.of(TerraformCliOptionFlag.builder()
                                                         .commandType(TerraformCommandFlagType.APPLY)
                                                         .flag(ParameterField.createValueField("-lock-timeout=0s"))
                                                         .build()))
                               .build())
            .build();
    GitConfigDTO gitConfigDTO = GitConfigDTO.builder()
                                    .gitAuthType(GitAuthType.HTTP)
                                    .gitConnectionType(GitConnectionType.ACCOUNT)
                                    .delegateSelectors(Collections.singleton("delegateName"))
                                    .url("https://github.com/wings-software")
                                    .branchName("master")
                                    .build();
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder().branch("master").connectorName("terraform").gitConfigDTO(gitConfigDTO).build();
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder()
                                                  .identifier("terraform")
                                                  .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                                  .succeedIfFileNotFound(false)
                                                  .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("APPLY", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());

    TerraformBackendConfigFileConfig backendConfigFileConfig =
        TerraformInlineBackendConfigFileConfig.builder().backendConfigFileContent("test-backend-config").build();
    TerraformInheritOutput inheritOutput = TerraformInheritOutput.builder()
                                               .backendConfig("back-content")
                                               .backendConfigurationFileConfig(backendConfigFileConfig)
                                               .workspace("w1")
                                               .planName("plan")
                                               .useConnectorCredentials(true)
                                               .build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformApplyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();
    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);
    assertThat(taskParameters.getTerraformCommandFlags().get("APPLY")).isEqualTo("-lock-timeout=0s");
    assertThat(taskParameters.isTfModuleSourceInheritSSH()).isTrue();

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles).isFalse();
    assertThat(terraformPassThroughData.hasS3Files).isFalse();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(0)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkInheritPlanWithArtifactory() {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_PLAN)
                               .commandFlags(List.of(TerraformCliOptionFlag.builder()
                                                         .commandType(TerraformCommandFlagType.APPLY)
                                                         .flag(ParameterField.createValueField("-lock-timeout=0s"))
                                                         .build()))
                               .build())
            .build();

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();

    ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig =
        TerraformStepDataGenerator.createStoreDelegateConfig();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(artifactoryStoreDelegateConfig)
        .when(terraformStepHelper)
        .getFileStoreFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());

    TerraformInheritOutput inheritOutput =
        TerraformInheritOutput.builder().backendConfig("back-content").workspace("w1").planName("plan").build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformApplyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();
    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);
  }

  @Test // Unknown configuration Type: [InheritFromApply]
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkNegativeScenarioInherit() {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_APPLY)
                               .build())
            .build();
    GitFetchFilesConfig gitFetchFilesConfig =
        GitFetchFilesConfig.builder().identifier("terraform").succeedIfFileNotFound(false).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());

    TerraformInheritOutput inheritOutput =
        TerraformInheritOutput.builder().backendConfig("back-content").workspace("w1").planName("plan").build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());
    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    String message = "Unknown configuration Type: [InheritFromApply]";
    try {
      terraformApplyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    } catch (InvalidRequestException invalidRequestException) {
      assertThat(invalidRequestException.getMessage()).isEqualTo(message);
    }
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecution_Inherited() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_PLAN)
                               .build())
            .build();
    GitConfigDTO gitConfigDTO = GitConfigDTO.builder()
                                    .gitAuthType(GitAuthType.HTTP)
                                    .gitConnectionType(GitConnectionType.ACCOUNT)
                                    .delegateSelectors(Collections.singleton("delegateName"))
                                    .url("https://github.com/wings-software")
                                    .branchName("master")
                                    .build();
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder().branch("master").connectorName("terraform").gitConfigDTO(gitConfigDTO).build();
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder()
                                                  .identifier("terraform")
                                                  .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                                  .succeedIfFileNotFound(false)
                                                  .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    TerraformInheritOutput inheritOutput =
        TerraformInheritOutput.builder().backendConfig("back-content").workspace("w1").planName("plan").build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    Map<String, String> commitIdForConfigFilesMap = new HashMap<>();
    commitIdForConfigFilesMap.put(TF_CONFIG_FILES, "commitId_1");
    commitIdForConfigFilesMap.put(TF_BACKEND_CONFIG_FILE, "commitId_2");

    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commitIdForConfigFilesMap(commitIdForConfigFilesMap)
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasGitFiles(false).hasS3Files(false).build();

    StepResponse stepResponse = terraformApplyStepV2.finalizeExecutionWithSecurityContextAndNodeInfo(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecution_Inline() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INLINE)
                               .skipStateStorage(ParameterField.createValueField(false))
                               .build())
            .build();
    GitConfigDTO gitConfigDTO = GitConfigDTO.builder()
                                    .gitAuthType(GitAuthType.HTTP)
                                    .gitConnectionType(GitConnectionType.ACCOUNT)
                                    .delegateSelectors(Collections.singleton("delegateName"))
                                    .url("https://github.com/wings-software")
                                    .branchName("master")
                                    .build();
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder().branch("master").connectorName("terraform").gitConfigDTO(gitConfigDTO).build();
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder()
                                                  .identifier("terraform")
                                                  .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                                  .succeedIfFileNotFound(false)
                                                  .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    TerraformInheritOutput inheritOutput =
        TerraformInheritOutput.builder().backendConfig("back-content").workspace("w1").planName("plan").build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasGitFiles(false).hasS3Files(false).build();

    StepResponse stepResponse = terraformApplyStepV2.finalizeExecutionWithSecurityContextAndNodeInfo(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    verify(terraformStepHelper, times(1)).getRevisionsMap(any(TerraformPassThroughData.class), any());
    verify(terraformStepHelper).addTerraformRevisionOutcomeIfRequired(any(), any());
    verify(terraformStepHelper, times(1)).updateParentEntityIdAndVersion(any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContextAndArtifactoryAsStore_Inline() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INLINE)
                               .skipStateStorage(ParameterField.createValueField(false))
                               .build())
            .build();
    ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig =
        TerraformStepDataGenerator.createStoreDelegateConfig();

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(artifactoryStoreDelegateConfig)
        .when(terraformStepHelper)
        .getFileStoreFetchFilesConfig(any(), any(), any());
    TerraformInheritOutput inheritOutput =
        TerraformInheritOutput.builder().backendConfig("back-content").workspace("w1").planName("plan").build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasGitFiles(false).hasS3Files(false).build();

    StepResponse stepResponse = terraformApplyStepV2.finalizeExecutionWithSecurityContextAndNodeInfo(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    verify(terraformStepHelper, times(1)).updateParentEntityIdAndVersion(any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(terraformApplyStepV2.getStepParametersClass()).isEqualTo(StepBaseParameters.class);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionNegativeScenario() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_APPLY)
                               .build())
            .build();
    GitConfigDTO gitConfigDTO = GitConfigDTO.builder()
                                    .gitAuthType(GitAuthType.HTTP)
                                    .gitConnectionType(GitConnectionType.ACCOUNT)
                                    .delegateSelectors(Collections.singleton("delegateName"))
                                    .url("https://github.com/wings-software")
                                    .branchName("master")
                                    .build();
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder().branch("master").connectorName("terraform").gitConfigDTO(gitConfigDTO).build();
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder()
                                                  .identifier("terraform")
                                                  .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                                  .succeedIfFileNotFound(false)
                                                  .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    TerraformInheritOutput inheritOutput =
        TerraformInheritOutput.builder().backendConfig("back-content").workspace("w1").planName("plan").build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    String message = "Unknown configuration Type: [InheritFromApply]";
    try {
      TerraformPassThroughData terraformPassThroughData =
          TerraformPassThroughData.builder().hasGitFiles(false).hasS3Files(false).build();

      terraformApplyStepV2.finalizeExecutionWithSecurityContextAndNodeInfo(
          ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);
    } catch (InvalidRequestException invalidRequestException) {
      assertThat(invalidRequestException.getMessage()).isEqualTo(message);
    }
  }

  @Test // Different Status
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void handleFinalizeExecutionDifferentStatus() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_PLAN)
                               .build())
            .build();
    GitConfigDTO gitConfigDTO = GitConfigDTO.builder()
                                    .gitAuthType(GitAuthType.HTTP)
                                    .gitConnectionType(GitConnectionType.ACCOUNT)
                                    .delegateSelectors(Collections.singleton("delegateName"))
                                    .url("https://github.com/wings-software")
                                    .branchName("master")
                                    .build();
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder().branch("master").connectorName("terraform").gitConfigDTO(gitConfigDTO).build();
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder()
                                                  .identifier("terraform")
                                                  .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                                  .succeedIfFileNotFound(false)
                                                  .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    TerraformInheritOutput inheritOutput =
        TerraformInheritOutput.builder().backendConfig("back-content").workspace("w1").planName("plan").build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    Map<String, String> commitIdForConfigFilesMap = new HashMap<>();
    commitIdForConfigFilesMap.put(TF_CONFIG_FILES, "commitId_1");
    commitIdForConfigFilesMap.put(TF_BACKEND_CONFIG_FILE, "commitId_2");
    TerraformTaskNGResponse terraformTaskNGResponseFailure = TerraformTaskNGResponse.builder()
                                                                 .commitIdForConfigFilesMap(commitIdForConfigFilesMap)
                                                                 .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                                 .unitProgressData(unitProgressData)
                                                                 .build();

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasGitFiles(false).hasS3Files(false).build();

    StepResponse stepResponse = terraformApplyStepV2.finalizeExecutionWithSecurityContextAndNodeInfo(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponseFailure);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();

    TerraformTaskNGResponse terraformTaskNGResponseRunning = TerraformTaskNGResponse.builder()
                                                                 .commitIdForConfigFilesMap(commitIdForConfigFilesMap)
                                                                 .commandExecutionStatus(CommandExecutionStatus.RUNNING)
                                                                 .unitProgressData(unitProgressData)
                                                                 .build();
    stepResponse = terraformApplyStepV2.finalizeExecutionWithSecurityContextAndNodeInfo(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponseRunning);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.RUNNING);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();

    TerraformTaskNGResponse terraformTaskNGResponseQueued = TerraformTaskNGResponse.builder()
                                                                .commitIdForConfigFilesMap(commitIdForConfigFilesMap)
                                                                .commandExecutionStatus(CommandExecutionStatus.QUEUED)
                                                                .unitProgressData(unitProgressData)
                                                                .build();
    stepResponse = terraformApplyStepV2.finalizeExecutionWithSecurityContextAndNodeInfo(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponseQueued);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.QUEUED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    String message =
        String.format("Unhandled type CommandExecutionStatus: " + CommandExecutionStatus.SKIPPED, WingsException.USER);
    try {
      TerraformTaskNGResponse terraformTaskNGResponseSkipped =
          TerraformTaskNGResponse.builder()
              .commandExecutionStatus(CommandExecutionStatus.SKIPPED)
              .unitProgressData(unitProgressData)
              .build();
      terraformApplyStepV2.finalizeExecutionWithSecurityContextAndNodeInfo(
          ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponseSkipped);
    } catch (InvalidRequestException invalidRequestException) {
      assertThat(invalidRequestException.getMessage()).isEqualTo(message);
    }
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecution_InlineWithOutputs() throws Exception {
    Ambiance ambiance = getAmbiance();

    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();
    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("VarFiles/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();

    TerraformApplyStepParameters applyStepParameters =
        TerraformStepDataGenerator.generateApplyStepPlan(StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());

    String tfJsonOutput =
        "{   \"test-output-name1\": {     \"sensitive\": false,     \"type\": \"string\",     \"value\": "
        + "\"test-output-value1\"   },   \"test-output-name2\": {     \"sensitive\": false,     \"type\": \"string\",    "
        + " \"value\": \"test-output-value2\"   } }";

    when(terraformStepHelper.parseTerraformOutputs(eq(tfJsonOutput))).thenReturn(new HashMap<>() {
      {
        put("test-output-name1", "test-output-value1");
        put("test-output-name2", "test-output-value2");
      }
    });
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .outputs(tfJsonOutput)
                                                          .build();

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasGitFiles(false).hasS3Files(false).build();

    StepResponse stepResponse = terraformApplyStepV2.finalizeExecutionWithSecurityContextAndNodeInfo(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();

    StepResponse.StepOutcome stepOutcome = ((List<StepResponse.StepOutcome>) stepResponse.getStepOutcomes()).get(0);
    assertThat(stepOutcome.getOutcome()).isInstanceOf(TerraformApplyOutcome.class);
    assertThat(stepOutcome.getName()).isEqualTo("output");
    TerraformApplyOutcome terraformApplyOutcome = (TerraformApplyOutcome) stepOutcome.getOutcome();
    assertThat(terraformApplyOutcome.size()).isEqualTo(2);
    assertThat(terraformApplyOutcome.get("test-output-name1")).isEqualTo("test-output-value1");
    assertThat(terraformApplyOutcome.get("test-output-name2")).isEqualTo("test-output-value2");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecution_InlineWithOutputsAsSecret() throws Exception {
    Ambiance ambiance = getAmbiance();

    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();
    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("VarFiles/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();

    TerraformApplyStepParameters applyStepParameters =
        TerraformStepDataGenerator.generateApplyStepPlan(StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);
    applyStepParameters.getConfiguration().setEncryptOutputSecretManager(
        TerraformEncryptOutput.builder()
            .outputSecretManagerRef(ParameterField.createValueField("test-secret-manager-ref"))
            .build());

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());

    String tfJsonOutput =
        "{   \"test-output-name1\": {     \"sensitive\": false,     \"type\": \"string\",     \"value\": "
        + "\"test-output-value1\"   },   \"test-output-name2\": {     \"sensitive\": false,     \"type\": \"string\",    "
        + " \"value\": \"test-output-value2\"   } }";

    when(terraformStepHelper.encryptTerraformJsonOutput(eq(tfJsonOutput), eq(ambiance), any(), any()))
        .thenReturn(new HashMap<>() {
          { put(TF_ENCRYPTED_JSON_OUTPUT_NAME, "<+secrets.getValue(\"account.test-json-1\")>"); }
        });

    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .outputs(tfJsonOutput)
                                                          .build();

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasGitFiles(false).hasS3Files(false).build();

    StepResponse stepResponse = terraformApplyStepV2.finalizeExecutionWithSecurityContextAndNodeInfo(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();

    StepResponse.StepOutcome stepOutcome = ((List<StepResponse.StepOutcome>) stepResponse.getStepOutcomes()).get(0);
    assertThat(stepOutcome.getOutcome()).isInstanceOf(TerraformApplyOutcome.class);
    assertThat(stepOutcome.getName()).isEqualTo("output");
    TerraformApplyOutcome terraformApplyOutcome = (TerraformApplyOutcome) stepOutcome.getOutcome();
    assertThat(terraformApplyOutcome.size()).isEqualTo(1);
    assertThat(terraformApplyOutcome.get(TF_ENCRYPTED_JSON_OUTPUT_NAME))
        .isEqualTo("<+secrets.getValue(\"account.test-json-1\")>");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecution_InheritedWithOutput() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_PLAN)
                               .build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());

    String tfJsonOutput =
        "{   \"test-output-name1\": {     \"sensitive\": false,     \"type\": \"string\",     \"value\": "
        + "\"test-output-value1\"   },   \"test-output-name2\": {     \"sensitive\": false,     \"type\": \"string\",    "
        + " \"value\": \"test-output-value2\"   } }";

    when(terraformStepHelper.parseTerraformOutputs(eq(tfJsonOutput))).thenReturn(new HashMap<>() {
      {
        put("test-output-name1", "test-output-value1");
        put("test-output-name2", "test-output-value2");
      }
    });
    TerraformInheritOutput inheritOutput = TerraformInheritOutput.builder().skipStateStorage(true).build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());

    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    Map<String, String> commitIdForConfigFilesMap = new HashMap<>();
    commitIdForConfigFilesMap.put(TF_CONFIG_FILES, "commitId_1");
    commitIdForConfigFilesMap.put(TF_BACKEND_CONFIG_FILE, "commitId_2");
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commitIdForConfigFilesMap(commitIdForConfigFilesMap)
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .outputs(tfJsonOutput)
                                                          .build();

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasGitFiles(false).hasS3Files(false).build();

    StepResponse stepResponse = terraformApplyStepV2.finalizeExecutionWithSecurityContextAndNodeInfo(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    StepResponse.StepOutcome stepOutcome = ((List<StepResponse.StepOutcome>) stepResponse.getStepOutcomes()).get(0);
    assertThat(stepOutcome.getOutcome()).isInstanceOf(TerraformApplyOutcome.class);
    assertThat(stepOutcome.getName()).isEqualTo("output");
    TerraformApplyOutcome terraformApplyOutcome = (TerraformApplyOutcome) stepOutcome.getOutcome();
    assertThat(terraformApplyOutcome.size()).isEqualTo(2);
    assertThat(terraformApplyOutcome.get("test-output-name1")).isEqualTo("test-output-value1");
    assertThat(terraformApplyOutcome.get("test-output-name2")).isEqualTo("test-output-value2");
    verify(terraformStepHelper, times(0)).updateParentEntityIdAndVersion(any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecution_InheritedWithOutputAsSecret() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_PLAN)
                               .encryptOutput(TerraformEncryptOutput.builder()
                                                  .outputSecretManagerRef(
                                                      ParameterField.createValueField("test-secret-manager-ref"))
                                                  .build())
                               .build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());

    String tfJsonOutput =
        "{   \"test-output-name1\": {     \"sensitive\": false,     \"type\": \"string\",     \"value\": "
        + "\"test-output-value1\"   },   \"test-output-name2\": {     \"sensitive\": false,     \"type\": \"string\",    "
        + " \"value\": \"test-output-value2\"   } }";

    when(terraformStepHelper.encryptTerraformJsonOutput(eq(tfJsonOutput), eq(ambiance), any(), any()))
        .thenReturn(new HashMap<>() {
          { put(TF_ENCRYPTED_JSON_OUTPUT_NAME, "<+secrets.getValue(\"account.test-json-1\")>"); }
        });
    TerraformInheritOutput inheritOutput = TerraformInheritOutput.builder().skipStateStorage(false).build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());
    Map<String, String> commitIdForConfigFilesMap = new HashMap<>();
    commitIdForConfigFilesMap.put(TF_CONFIG_FILES, "commitId_1");
    commitIdForConfigFilesMap.put(TF_BACKEND_CONFIG_FILE, "commitId_2");
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commitIdForConfigFilesMap(commitIdForConfigFilesMap)
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .outputs(tfJsonOutput)
                                                          .build();

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasGitFiles(false).hasS3Files(false).build();

    StepResponse stepResponse = terraformApplyStepV2.finalizeExecutionWithSecurityContextAndNodeInfo(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();

    StepResponse.StepOutcome stepOutcome = ((List<StepResponse.StepOutcome>) stepResponse.getStepOutcomes()).get(0);
    assertThat(stepOutcome.getOutcome()).isInstanceOf(TerraformApplyOutcome.class);
    assertThat(stepOutcome.getName()).isEqualTo("output");
    TerraformApplyOutcome terraformApplyOutcome = (TerraformApplyOutcome) stepOutcome.getOutcome();
    assertThat(terraformApplyOutcome.size()).isEqualTo(1);
    assertThat(terraformApplyOutcome.get(TF_ENCRYPTED_JSON_OUTPUT_NAME))
        .isEqualTo("<+secrets.getValue(\"account.test-json-1\")>");
    verify(terraformStepHelper, times(1)).updateParentEntityIdAndVersion(any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionAndFailed() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(
                TerraformStepConfigurationParameters.builder().type(TerraformStepConfigurationType.INLINE).build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();

    StepExceptionPassThroughData terraformPassThroughData =
        StepExceptionPassThroughData.builder()
            .unitProgressData(UnitProgressData.builder().unitProgresses(new ArrayList<>()).build())
            .errorMessage("Exception Error in executing terraform plan task or fetching remote var files")
            .build();

    doReturn(StepResponse.builder()
                 .status(Status.FAILED)
                 .failureInfo(FailureInfo.newBuilder()
                                  .setErrorMessage(
                                      "Exception Error in executing terraform plan task or fetching remote var files")
                                  .build())
                 .build())
        .when(terraformStepHelper)
        .handleStepExceptionFailure(any());

    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                          .unitProgressData(UnitProgressData.builder().build())
                                                          .build();

    StepResponse stepResponse = terraformApplyStepV2.finalizeExecutionWithSecurityContextAndNodeInfo(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getErrorMessage())
        .isEqualTo("Exception Error in executing terraform plan task or fetching remote var files");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionAndMultipleCommandUnits() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(
                TerraformStepConfigurationParameters.builder().type(TerraformStepConfigurationType.INLINE).build())
            .build();

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();

    List<UnitProgress> unitProgressesPlan = new ArrayList<>();
    UnitProgress planUP = UnitProgress.newBuilder().setUnitName("Plan").setStatus(UnitStatus.SUCCESS).build();
    unitProgressesPlan.add(planUP);

    List<UnitProgress> unitProgressesFetch = new ArrayList<>();
    UnitProgress fetchFilesUP =
        UnitProgress.newBuilder().setUnitName("Fetch Files").setStatus(UnitStatus.SUCCESS).build();
    unitProgressesFetch.add(fetchFilesUP);

    UnitProgressData unitProgressDataPlan = UnitProgressData.builder().unitProgresses(unitProgressesPlan).build();

    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressDataPlan)
                                                          .detailedExitCode(2)
                                                          .build();

    TerraformPassThroughData terraformPassThroughData = TerraformPassThroughData.builder()
                                                            .hasGitFiles(true)
                                                            .hasS3Files(true)
                                                            .unitProgresses(unitProgressesFetch)
                                                            .build();

    StepResponse stepResponse = terraformApplyStepV2.finalizeExecutionWithSecurityContextAndNodeInfo(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    assertThat(stepResponse.getUnitProgressList().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetStepExecutionTelemetryEventDTO() {
    Ambiance ambiance = getAmbiance();
    LinkedHashMap<String, TerraformVarFile> varFiles = new LinkedHashMap();
    varFiles.put("", TerraformVarFile.builder().build());
    TerraformApplyStepParameters stepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INLINE)
                               .spec(TerraformExecutionDataParameters.builder().varFiles(varFiles).build())
                               .build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();
    doReturn(true).when(terraformStepHelper).hasOptionalVarFiles(any());

    StepExecutionTelemetryEventDTO stepExecutionTelemetryEventDTO =
        terraformApplyStepV2.getStepExecutionTelemetryEventDTO(
            ambiance, stepElementParameters, TerraformPassThroughData.builder().build());

    assertThat(stepExecutionTelemetryEventDTO.getStepType()).isEqualTo(TerraformApplyStepV2.STEP_TYPE.getType());
    assertThat(stepExecutionTelemetryEventDTO.getProperties().get(OPTIONAL_VAR_FILES)).isEqualTo(true);
  }
}
