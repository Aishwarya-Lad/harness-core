/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.secret;

import io.harness.delegate.core.beans.EncryptionType;

import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;
import org.mapstruct.factory.Mappers;

@Mapper(collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedSourcePolicy = ReportingPolicy.ERROR,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EncryptionTypePojoProtoMapper {
  EncryptionTypePojoProtoMapper INSTANCE = Mappers.getMapper(EncryptionTypePojoProtoMapper.class);
  @ValueMappings({
    @ValueMapping(source = MappingConstants.NULL, target = "NOT_SET")
    , @ValueMapping(source = "KMS", target = "AWS_KMS"), @ValueMapping(source = "VAULT", target = "HASHICORP_VAULT"),
        @ValueMapping(source = MappingConstants.ANY_REMAINING, target = "UNKNOWN")
  })
  EncryptionType
  map(io.harness.security.encryption.EncryptionType type);
}
