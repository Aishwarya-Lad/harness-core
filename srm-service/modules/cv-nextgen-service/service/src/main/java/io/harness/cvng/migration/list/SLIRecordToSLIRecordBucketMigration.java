/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.SRMPersistence;
import io.harness.cvng.core.utils.CVNGObjectUtils;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordKeys;
import io.harness.persistence.UuidAware;

import com.google.inject.Inject;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SLIRecordToSLIRecordBucketMigration implements CVNGMigration {
  @Inject SRMPersistence hPersistence;

  private static final int BATCH_SIZE = 1000;

  @Override
  public void migrate() {
    log.info("Starting cleanup for older sli records and creating bucket for newer sliRecords");
    Query<SLIRecord> queryToDeleteOlderRecords =
        hPersistence.createQuery(SLIRecord.class)
            .field(SLIRecordKeys.timestamp)
            .lessThan(Date.from(OffsetDateTime.now().minusMonths(3).toInstant()))
            .project(UuidAware.UUID_KEY, true);
    while (true) {
      List<SLIRecord> recordsToBeDeleted = queryToDeleteOlderRecords.find(new FindOptions().limit(BATCH_SIZE)).toList();
      Set<?> recordIdsTobeDeleted = recordsToBeDeleted.stream()
                                        .map(recordToBeDeleted -> ((UuidAware) recordToBeDeleted).getUuid())
                                        .map(CVNGObjectUtils::convertToObjectIdIfRequired)
                                        .collect(Collectors.toSet());
      if (recordIdsTobeDeleted.size() == 0) {
        break;
      }
      hPersistence.delete(hPersistence.createQuery(SLIRecord.class).field(UuidAware.UUID_KEY).in(recordIdsTobeDeleted));
      log.info("[SLI Bucket Migration] Deleted {} sli records", recordsToBeDeleted.size());
    }
    log.info("[SLI Bucket Migration] Deleted all older sli records");
  }

  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.NA;
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.NA;
  }
}
