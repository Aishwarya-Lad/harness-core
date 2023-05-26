/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.runner;

import io.harness.delegate.core.beans.InputData;

public interface TaskRunner {
  void init(String taskGroupId, InputData infra);
  void execute(String taskGroupId, InputData tasks);
  void cleanup(String taskGroupId);
}