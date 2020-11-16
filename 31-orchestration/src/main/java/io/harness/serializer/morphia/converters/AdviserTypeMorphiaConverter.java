package io.harness.serializer.morphia.converters;

import io.harness.persistence.converters.ProtoMessageConverter;
import io.harness.pms.advisers.AdviserType;

public class AdviserTypeMorphiaConverter extends ProtoMessageConverter<AdviserType> {
  public AdviserTypeMorphiaConverter() {
    super(AdviserType.class);
  }
}
