/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.consumers.sdk.response;

import static io.harness.OrchestrationEventsFrameworkConstants.SDK_RESPONSE_SPAWN_EVENT_CONSUMER;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.pms.events.base.PmsAbstractRedisConsumer;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class SdkResponseSpawnEventRedisConsumer extends PmsAbstractRedisConsumer<SdkResponseSpawnEventMessageListener> {
  @Inject
  public SdkResponseSpawnEventRedisConsumer(@Named(SDK_RESPONSE_SPAWN_EVENT_CONSUMER) Consumer redisConsumer,
      SdkResponseSpawnEventMessageListener sdkResponseSpawnMessageListener,
      @Named("pmsEventsCache") Cache<String, Integer> eventsCache, QueueController queueController,
      @Named("EngineExecutorService") ExecutorService executorService) {
    super(redisConsumer, sdkResponseSpawnMessageListener, eventsCache, queueController, executorService);
  }
}
