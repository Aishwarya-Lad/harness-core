/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.SSCA;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(SSCA)
public interface SBOMComponentRepoCustom {
  Page<NormalizedSBOMComponentEntity> findAll(Criteria criteria, Pageable pageable);
  List<NormalizedSBOMComponentEntity> findAllByQuery(Query query);
  List<String> findDistinctOrchestrationIds(Criteria criteria);
  <T> List<T> aggregate(Aggregation aggregation, Class<T> resultClass);
}
