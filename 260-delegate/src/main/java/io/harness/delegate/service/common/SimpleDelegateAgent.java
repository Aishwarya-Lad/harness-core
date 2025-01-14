/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.common;

import io.harness.delegate.beans.DelegateTaskEvent;

import java.io.IOException;
import java.util.concurrent.Future;

public abstract class SimpleDelegateAgent<T, R> extends AbstractDelegateAgentService<T, R> {
  @Override
  protected void onDelegateStart() {}

  @Override
  protected void onDelegateRegistered() {}

  @Override
  protected void onHeartbeat() {}

  @Override
  protected void onPostExecute(final String delegateTaskId) {}

  @Override
  protected void onTaskResponse(final String taskId, R response) throws IOException {}

  @Override
  protected void onPostExecute(final String delegateTaskId, final Future<?> taskFuture) {}

  @Override
  protected boolean onPreExecute(final DelegateTaskEvent delegateTaskEvent, final String delegateTaskId) {
    return false;
  }

  @Override
  protected void onResponseSent(final String taskId) {}
}
