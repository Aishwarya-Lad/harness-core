/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import static io.harness.cvng.CVConstants.SLO_RECORDS_TTL_DAYS;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket.getSLIRecordBucketsFromSLIRecords;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.analysis.entities.VerificationTaskBase;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.Version;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.jetbrains.annotations.NotNull;

@Data
@Builder(buildMethodName = "unsafeBuild")
@FieldNameConstants(innerTypeName = "CompositeSLORecordBucketKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.CVNG)
@Entity(value = "compositeSLORecordBuckets", noClassnameStored = true)
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
public class CompositeSLORecordBucket extends VerificationTaskBase implements PersistentEntity, UuidAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("verificationtaskid_bucketstarttime")
                 .field(CompositeSLORecordBucketKeys.verificationTaskId)
                 .field(CompositeSLORecordBucketKeys.bucketStartTime)
                 .build())
        .build();
  }

  public static CompositeSLORecordBucket getCompositeSLORecordBucketFromCompositeSLORecords(
      List<CompositeSLORecord> compositeSLORecords) {
    Preconditions.checkArgument(compositeSLORecords.size() == 5);
    Preconditions.checkArgument(compositeSLORecords.get(0).getEpochMinute() % 5 == 0);
    Map<String, SLIRecordBucket> scopedIdentifierSLIRecordBucketMap = getSLIRecordBucketMap(compositeSLORecords);
    return CompositeSLORecordBucket.builder()
        .version(compositeSLORecords.get(0).getVersion())
        .bucketStartTime(compositeSLORecords.get(0).getTimestamp())
        .sloVersion(compositeSLORecords.get(0).getSloVersion())
        .runningBadCount(compositeSLORecords.get(4).getRunningBadCount())
        .runningGoodCount(compositeSLORecords.get(4).getRunningGoodCount())
        .verificationTaskId(compositeSLORecords.get(0).getVerificationTaskId())
        .scopedIdentifierSLIRecordBucketMap(scopedIdentifierSLIRecordBucketMap)
        .build();
  }

  @NotNull
  private static Map<String, SLIRecordBucket> getSLIRecordBucketMap(List<CompositeSLORecord> compositeSLORecords) {
    Map<String, List<SLIRecord>> scopedIdentifierSLIRecords = new HashMap<>();
    for (CompositeSLORecord compositeSLORecord : compositeSLORecords) {
      if (Objects.nonNull(compositeSLORecord.getScopedIdentifierSLIRecordMap())) {
        for (Map.Entry<String, SLIRecord> entry : compositeSLORecord.getScopedIdentifierSLIRecordMap().entrySet()) {
          scopedIdentifierSLIRecords.put(
              entry.getKey(), scopedIdentifierSLIRecords.getOrDefault(entry.getKey(), new ArrayList<>()));
          scopedIdentifierSLIRecords.get(entry.getKey()).add(entry.getValue());
        }
      }
    }
    Map<String, SLIRecordBucket> scopedIdentifierSLIRecordBucketMap = new HashMap<>();
    for (Map.Entry<String, List<SLIRecord>> entry : scopedIdentifierSLIRecords.entrySet()) {
      if (entry.getValue().size() % 5 == 0) {
        List<SLIRecordBucket> sliRecordBucketsFromSLIRecords = getSLIRecordBucketsFromSLIRecords(entry.getValue());
        if (!sliRecordBucketsFromSLIRecords.isEmpty()) {
          scopedIdentifierSLIRecordBucketMap.put(entry.getKey(), sliRecordBucketsFromSLIRecords.get(0));
        }
      }
    }
    return scopedIdentifierSLIRecordBucketMap;
  }

  public static class CompositeSLORecordBucketBuilder {
    public CompositeSLORecordBucket build() {
      return unsafeBuild();
    }
  }
  @Version long version;
  @Id private String uuid;
  private String verificationTaskId;
  private Instant bucketStartTime; // minute
  private Double runningBadCount;
  private Double runningGoodCount;
  private Map<String, SLIRecordBucket> scopedIdentifierSLIRecordBucketMap;
  private int sloVersion;
  @Builder.Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusDays(SLO_RECORDS_TTL_DAYS).toInstant());
}
