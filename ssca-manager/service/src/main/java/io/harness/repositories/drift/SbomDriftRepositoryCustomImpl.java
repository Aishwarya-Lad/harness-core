/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.drift;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ssca.entities.drift.DriftEntity;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.SSCA)
@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
public class SbomDriftRepositoryCustomImpl implements SbomDriftRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public boolean exists(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.exists(query, DriftEntity.class);
  }

  @Override
  public DriftEntity find(Criteria criteria) {
    return mongoTemplate.findOne(new Query(criteria), DriftEntity.class);
  }

  @Override
  public DriftEntity update(Query query, Update update) {
    return mongoTemplate.findAndModify(query, update, new FindAndModifyOptions().returnNew(true), DriftEntity.class);
  }

  @Override
  public <T> List<T> aggregate(Aggregation aggregation, Class<T> resultClass) {
    return mongoTemplate.aggregate(aggregation, DriftEntity.class, resultClass).getMappedResults();
  }

  @Override
  public DriftEntity findOne(Query query) {
    return mongoTemplate.findOne(query, DriftEntity.class);
  }
}
