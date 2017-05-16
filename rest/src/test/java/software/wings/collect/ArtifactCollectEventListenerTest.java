package software.wings.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.BambooConfig.Builder.aBambooConfig;
import static software.wings.beans.JenkinsConfig.Builder.aJenkinsConfig;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.TaskType.BAMBOO_COLLECTION;
import static software.wings.beans.TaskType.JENKINS_COLLECTION;
import static software.wings.beans.artifact.Artifact.Builder.anArtifact;
import static software.wings.beans.artifact.BambooArtifactStream.Builder.aBambooArtifactStream;
import static software.wings.beans.artifact.JenkinsArtifactStream.Builder.aJenkinsArtifactStream;
import static software.wings.collect.CollectEvent.Builder.aCollectEvent;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_PATH;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_NAME;
import static software.wings.utils.WingsTestConstants.JENKINS_URL;
import static software.wings.utils.WingsTestConstants.JOB_NAME;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.DelegateTask;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.Artifact.Status;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.service.impl.EventEmitter;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.waitnotify.WaitNotifyEngine;

import java.util.Arrays;
import javax.inject.Inject;

/**
 * Created by peeyushaggarwal on 5/11/16.
 */
public class ArtifactCollectEventListenerTest extends WingsBaseTest {
  private final Application APP = anApplication().withUuid(APP_ID).withAccountId(ACCOUNT_ID).build();

  @InjectMocks @Inject private ArtifactCollectEventListener artifactCollectEventListener;

  @Mock private AppService appService;

  @Mock private ArtifactService artifactService;

  @Mock private DelegateService delegateService;

  @Mock private WaitNotifyEngine waitNotifyEngine;

  @Mock private SettingsService settingsService;

  @Mock private ArtifactStreamService artifactStreamService;

  @Mock private EventEmitter eventEmitter;

  /**
   * Setup mocks.ARTIFACT_SOURCE
   */
  @Before
  public void setupMocks() {
    when(appService.get(APP_ID)).thenReturn(APP);
  }

  /**
   * Should shouldSendTask
   *
   * @throws Exception the exception
   */
  @Test
  public void shouldSendJenkinsTask() throws Exception {
    SettingAttribute SETTING_ATTRIBUTE = aSettingAttribute()
                                             .withValue(aJenkinsConfig()
                                                            .withJenkinsUrl(JENKINS_URL)
                                                            .withUsername(USER_NAME)
                                                            .withPassword(PASSWORD.toCharArray())
                                                            .withAccountId(ACCOUNT_ID)
                                                            .build())
                                             .build();
    when(settingsService.get(SETTING_ID)).thenReturn(SETTING_ATTRIBUTE);

    ArtifactStream ARTIFACT_SOURCE = aJenkinsArtifactStream()
                                         .withSourceName(ARTIFACT_STREAM_NAME)
                                         .withAppId(APP_ID)
                                         .withSettingId(SETTING_ID)
                                         .withJobname(JOB_NAME)
                                         .withArtifactPaths(Arrays.asList(ARTIFACT_PATH))
                                         .build();
    when(artifactStreamService.get(APP_ID, ARTIFACT_STREAM_ID)).thenReturn(ARTIFACT_SOURCE);

    artifactCollectEventListener.onMessage(
        aCollectEvent()
            .withArtifact(
                anArtifact().withUuid(ARTIFACT_ID).withAppId(APP_ID).withArtifactStreamId(ARTIFACT_STREAM_ID).build())
            .build());

    verify(artifactService).updateStatus(ARTIFACT_ID, APP_ID, Status.RUNNING);

    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(delegateTaskArgumentCaptor.capture());
    assertThat(delegateTaskArgumentCaptor.getValue())
        .isNotNull()
        .hasFieldOrPropertyWithValue("taskType", JENKINS_COLLECTION)
        .hasFieldOrProperty("parameters");
  }

  /**
   * Should shouldSendTask
   *
   * @throws Exception the exception
   */
  @Test
  public void shouldSendBambooTask() throws Exception {
    SettingAttribute SETTING_ATTRIBUTE = aSettingAttribute()
                                             .withValue(aBambooConfig()
                                                            .withBambooUrl(JENKINS_URL)
                                                            .withUsername(USER_NAME)
                                                            .withPassword(PASSWORD.toCharArray())
                                                            .build())
                                             .build();
    when(settingsService.get(SETTING_ID)).thenReturn(SETTING_ATTRIBUTE);

    ArtifactStream ARTIFACT_SOURCE = aBambooArtifactStream()
                                         .withSourceName(ARTIFACT_STREAM_NAME)
                                         .withAppId(APP_ID)
                                         .withSettingId(SETTING_ID)
                                         .withJobname(JOB_NAME)
                                         .withArtifactPaths(Arrays.asList(ARTIFACT_PATH))
                                         .build();

    when(artifactStreamService.get(APP_ID, ARTIFACT_STREAM_ID)).thenReturn(ARTIFACT_SOURCE);

    artifactCollectEventListener.onMessage(
        aCollectEvent()
            .withArtifact(
                anArtifact().withUuid(ARTIFACT_ID).withAppId(APP_ID).withArtifactStreamId(ARTIFACT_STREAM_ID).build())
            .build());

    verify(artifactService).updateStatus(ARTIFACT_ID, APP_ID, Status.RUNNING);

    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(delegateTaskArgumentCaptor.capture());
    assertThat(delegateTaskArgumentCaptor.getValue())
        .isNotNull()
        .hasFieldOrPropertyWithValue("taskType", BAMBOO_COLLECTION)
        .hasFieldOrProperty("parameters");
  }

  /**
   * Should fail to collect artifact when source is missing.
   *
   * @throws Exception the exception
   */
  @Test
  public void shouldFailToCollectArtifactWhenSourceIsMissing() throws Exception {
    artifactCollectEventListener.onMessage(
        aCollectEvent().withArtifact(anArtifact().withUuid(ARTIFACT_ID).withAppId(APP_ID).build()).build());

    verify(artifactService).updateStatus(ARTIFACT_ID, APP_ID, Status.RUNNING);
    verify(artifactService).updateStatus(ARTIFACT_ID, APP_ID, Status.FAILED);
  }
}
