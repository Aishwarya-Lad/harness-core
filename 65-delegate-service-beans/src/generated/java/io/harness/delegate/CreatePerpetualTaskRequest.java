// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: io/harness/delegate/delegate_service.proto

package io.harness.delegate;

/**
 * Protobuf type {@code io.harness.delegate.CreatePerpetualTaskRequest}
 */
@javax.annotation.Generated(value = "protoc", comments = "annotations:CreatePerpetualTaskRequest.java.pb.meta")
public final class CreatePerpetualTaskRequest extends com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:io.harness.delegate.CreatePerpetualTaskRequest)
    CreatePerpetualTaskRequestOrBuilder {
  private static final long serialVersionUID = 0L;
  // Use CreatePerpetualTaskRequest.newBuilder() to construct.
  private CreatePerpetualTaskRequest(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private CreatePerpetualTaskRequest() {
    type_ = "";
  }

  @java.
  lang.Override
  public final com.google.protobuf.UnknownFieldSet getUnknownFields() {
    return this.unknownFields;
  }
  private CreatePerpetualTaskRequest(
      com.google.protobuf.CodedInputStream input, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    this();
    if (extensionRegistry == null) {
      throw new java.lang.NullPointerException();
    }
    int mutable_bitField0_ = 0;
    com.google.protobuf.UnknownFieldSet.Builder unknownFields = com.google.protobuf.UnknownFieldSet.newBuilder();
    try {
      boolean done = false;
      while (!done) {
        int tag = input.readTag();
        switch (tag) {
          case 0:
            done = true;
            break;
          case 10: {
            io.harness.delegate.AccountId.Builder subBuilder = null;
            if (accountId_ != null) {
              subBuilder = accountId_.toBuilder();
            }
            accountId_ = input.readMessage(io.harness.delegate.AccountId.parser(), extensionRegistry);
            if (subBuilder != null) {
              subBuilder.mergeFrom(accountId_);
              accountId_ = subBuilder.buildPartial();
            }

            break;
          }
          case 18: {
            java.lang.String s = input.readStringRequireUtf8();

            type_ = s;
            break;
          }
          case 26: {
            io.harness.perpetualtask.PerpetualTaskSchedule.Builder subBuilder = null;
            if (schedule_ != null) {
              subBuilder = schedule_.toBuilder();
            }
            schedule_ = input.readMessage(io.harness.perpetualtask.PerpetualTaskSchedule.parser(), extensionRegistry);
            if (subBuilder != null) {
              subBuilder.mergeFrom(schedule_);
              schedule_ = subBuilder.buildPartial();
            }

            break;
          }
          case 34: {
            io.harness.perpetualtask.PerpetualTaskContext.Builder subBuilder = null;
            if (context_ != null) {
              subBuilder = context_.toBuilder();
            }
            context_ = input.readMessage(io.harness.perpetualtask.PerpetualTaskContext.parser(), extensionRegistry);
            if (subBuilder != null) {
              subBuilder.mergeFrom(context_);
              context_ = subBuilder.buildPartial();
            }

            break;
          }
          case 40: {
            allowDuplicate_ = input.readBool();
            break;
          }
          default: {
            if (!parseUnknownField(input, unknownFields, extensionRegistry, tag)) {
              done = true;
            }
            break;
          }
        }
      }
    } catch (com.google.protobuf.InvalidProtocolBufferException e) {
      throw e.setUnfinishedMessage(this);
    } catch (java.io.IOException e) {
      throw new com.google.protobuf.InvalidProtocolBufferException(e).setUnfinishedMessage(this);
    } finally {
      this.unknownFields = unknownFields.build();
      makeExtensionsImmutable();
    }
  }
  public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
    return io.harness.delegate.DelegateServiceOuterClass
        .internal_static_io_harness_delegate_CreatePerpetualTaskRequest_descriptor;
  }

  @java.
  lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
    return io.harness.delegate.DelegateServiceOuterClass
        .internal_static_io_harness_delegate_CreatePerpetualTaskRequest_fieldAccessorTable
        .ensureFieldAccessorsInitialized(io.harness.delegate.CreatePerpetualTaskRequest.class,
            io.harness.delegate.CreatePerpetualTaskRequest.Builder.class);
  }

  public static final int ACCOUNT_ID_FIELD_NUMBER = 1;
  private io.harness.delegate.AccountId accountId_;
  /**
   * <code>.io.harness.delegate.AccountId account_id = 1;</code>
   */
  public boolean hasAccountId() {
    return accountId_ != null;
  }
  /**
   * <code>.io.harness.delegate.AccountId account_id = 1;</code>
   */
  public io.harness.delegate.AccountId getAccountId() {
    return accountId_ == null ? io.harness.delegate.AccountId.getDefaultInstance() : accountId_;
  }
  /**
   * <code>.io.harness.delegate.AccountId account_id = 1;</code>
   */
  public io.harness.delegate.AccountIdOrBuilder getAccountIdOrBuilder() {
    return getAccountId();
  }

  public static final int TYPE_FIELD_NUMBER = 2;
  private volatile java.lang.Object type_;
  /**
   * <code>string type = 2;</code>
   */
  public java.lang.String getType() {
    java.lang.Object ref = type_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      type_ = s;
      return s;
    }
  }
  /**
   * <code>string type = 2;</code>
   */
  public com.google.protobuf.ByteString getTypeBytes() {
    java.lang.Object ref = type_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
      type_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  public static final int SCHEDULE_FIELD_NUMBER = 3;
  private io.harness.perpetualtask.PerpetualTaskSchedule schedule_;
  /**
   * <code>.io.harness.perpetualtask.PerpetualTaskSchedule schedule = 3;</code>
   */
  public boolean hasSchedule() {
    return schedule_ != null;
  }
  /**
   * <code>.io.harness.perpetualtask.PerpetualTaskSchedule schedule = 3;</code>
   */
  public io.harness.perpetualtask.PerpetualTaskSchedule getSchedule() {
    return schedule_ == null ? io.harness.perpetualtask.PerpetualTaskSchedule.getDefaultInstance() : schedule_;
  }
  /**
   * <code>.io.harness.perpetualtask.PerpetualTaskSchedule schedule = 3;</code>
   */
  public io.harness.perpetualtask.PerpetualTaskScheduleOrBuilder getScheduleOrBuilder() {
    return getSchedule();
  }

  public static final int CONTEXT_FIELD_NUMBER = 4;
  private io.harness.perpetualtask.PerpetualTaskContext context_;
  /**
   * <code>.io.harness.perpetualtask.PerpetualTaskContext context = 4;</code>
   */
  public boolean hasContext() {
    return context_ != null;
  }
  /**
   * <code>.io.harness.perpetualtask.PerpetualTaskContext context = 4;</code>
   */
  public io.harness.perpetualtask.PerpetualTaskContext getContext() {
    return context_ == null ? io.harness.perpetualtask.PerpetualTaskContext.getDefaultInstance() : context_;
  }
  /**
   * <code>.io.harness.perpetualtask.PerpetualTaskContext context = 4;</code>
   */
  public io.harness.perpetualtask.PerpetualTaskContextOrBuilder getContextOrBuilder() {
    return getContext();
  }

  public static final int ALLOW_DUPLICATE_FIELD_NUMBER = 5;
  private boolean allowDuplicate_;
  /**
   * <code>bool allow_duplicate = 5;</code>
   */
  public boolean getAllowDuplicate() {
    return allowDuplicate_;
  }

  private byte memoizedIsInitialized = -1;
  @java.lang.Override
  public final boolean isInitialized() {
    byte isInitialized = memoizedIsInitialized;
    if (isInitialized == 1)
      return true;
    if (isInitialized == 0)
      return false;

    memoizedIsInitialized = 1;
    return true;
  }

  @java.lang.Override
  public void writeTo(com.google.protobuf.CodedOutputStream output) throws java.io.IOException {
    if (accountId_ != null) {
      output.writeMessage(1, getAccountId());
    }
    if (!getTypeBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 2, type_);
    }
    if (schedule_ != null) {
      output.writeMessage(3, getSchedule());
    }
    if (context_ != null) {
      output.writeMessage(4, getContext());
    }
    if (allowDuplicate_ != false) {
      output.writeBool(5, allowDuplicate_);
    }
    unknownFields.writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1)
      return size;

    size = 0;
    if (accountId_ != null) {
      size += com.google.protobuf.CodedOutputStream.computeMessageSize(1, getAccountId());
    }
    if (!getTypeBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(2, type_);
    }
    if (schedule_ != null) {
      size += com.google.protobuf.CodedOutputStream.computeMessageSize(3, getSchedule());
    }
    if (context_ != null) {
      size += com.google.protobuf.CodedOutputStream.computeMessageSize(4, getContext());
    }
    if (allowDuplicate_ != false) {
      size += com.google.protobuf.CodedOutputStream.computeBoolSize(5, allowDuplicate_);
    }
    size += unknownFields.getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public boolean equals(final java.lang.Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof io.harness.delegate.CreatePerpetualTaskRequest)) {
      return super.equals(obj);
    }
    io.harness.delegate.CreatePerpetualTaskRequest other = (io.harness.delegate.CreatePerpetualTaskRequest) obj;

    if (hasAccountId() != other.hasAccountId())
      return false;
    if (hasAccountId()) {
      if (!getAccountId().equals(other.getAccountId()))
        return false;
    }
    if (!getType().equals(other.getType()))
      return false;
    if (hasSchedule() != other.hasSchedule())
      return false;
    if (hasSchedule()) {
      if (!getSchedule().equals(other.getSchedule()))
        return false;
    }
    if (hasContext() != other.hasContext())
      return false;
    if (hasContext()) {
      if (!getContext().equals(other.getContext()))
        return false;
    }
    if (getAllowDuplicate() != other.getAllowDuplicate())
      return false;
    if (!unknownFields.equals(other.unknownFields))
      return false;
    return true;
  }

  @java.lang.Override
  public int hashCode() {
    if (memoizedHashCode != 0) {
      return memoizedHashCode;
    }
    int hash = 41;
    hash = (19 * hash) + getDescriptor().hashCode();
    if (hasAccountId()) {
      hash = (37 * hash) + ACCOUNT_ID_FIELD_NUMBER;
      hash = (53 * hash) + getAccountId().hashCode();
    }
    hash = (37 * hash) + TYPE_FIELD_NUMBER;
    hash = (53 * hash) + getType().hashCode();
    if (hasSchedule()) {
      hash = (37 * hash) + SCHEDULE_FIELD_NUMBER;
      hash = (53 * hash) + getSchedule().hashCode();
    }
    if (hasContext()) {
      hash = (37 * hash) + CONTEXT_FIELD_NUMBER;
      hash = (53 * hash) + getContext().hashCode();
    }
    hash = (37 * hash) + ALLOW_DUPLICATE_FIELD_NUMBER;
    hash = (53 * hash) + com.google.protobuf.Internal.hashBoolean(getAllowDuplicate());
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static io.harness.delegate.CreatePerpetualTaskRequest parseFrom(java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static io.harness.delegate.CreatePerpetualTaskRequest parseFrom(
      java.nio.ByteBuffer data, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static io.harness.delegate.CreatePerpetualTaskRequest parseFrom(com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static io.harness.delegate.CreatePerpetualTaskRequest parseFrom(
      com.google.protobuf.ByteString data, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static io.harness.delegate.CreatePerpetualTaskRequest parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static io.harness.delegate.CreatePerpetualTaskRequest parseFrom(
      byte[] data, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static io.harness.delegate.CreatePerpetualTaskRequest parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input);
  }
  public static io.harness.delegate.CreatePerpetualTaskRequest parseFrom(java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static io.harness.delegate.CreatePerpetualTaskRequest parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseDelimitedWithIOException(PARSER, input);
  }
  public static io.harness.delegate.CreatePerpetualTaskRequest parseDelimitedFrom(java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static io.harness.delegate.CreatePerpetualTaskRequest parseFrom(com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input);
  }
  public static io.harness.delegate.CreatePerpetualTaskRequest parseFrom(com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input, extensionRegistry);
  }

  @java.lang.Override
  public Builder newBuilderForType() {
    return newBuilder();
  }
  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }
  public static Builder newBuilder(io.harness.delegate.CreatePerpetualTaskRequest prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }
  @java.lang.Override
  public Builder toBuilder() {
    return this == DEFAULT_INSTANCE ? new Builder() : new Builder().mergeFrom(this);
  }

  @java.lang.Override
  protected Builder newBuilderForType(com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
    Builder builder = new Builder(parent);
    return builder;
  }
  /**
   * Protobuf type {@code io.harness.delegate.CreatePerpetualTaskRequest}
   */
  public static final class Builder extends com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:io.harness.delegate.CreatePerpetualTaskRequest)
      io.harness.delegate.CreatePerpetualTaskRequestOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
      return io.harness.delegate.DelegateServiceOuterClass
          .internal_static_io_harness_delegate_CreatePerpetualTaskRequest_descriptor;
    }

    @java.
    lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
      return io.harness.delegate.DelegateServiceOuterClass
          .internal_static_io_harness_delegate_CreatePerpetualTaskRequest_fieldAccessorTable
          .ensureFieldAccessorsInitialized(io.harness.delegate.CreatePerpetualTaskRequest.class,
              io.harness.delegate.CreatePerpetualTaskRequest.Builder.class);
    }

    // Construct using io.harness.delegate.CreatePerpetualTaskRequest.newBuilder()
    private Builder() {
      maybeForceBuilderInitialization();
    }

    private Builder(com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      super(parent);
      maybeForceBuilderInitialization();
    }
    private void maybeForceBuilderInitialization() {
      if (com.google.protobuf.GeneratedMessageV3.alwaysUseFieldBuilders) {
      }
    }
    @java.lang.Override
    public Builder clear() {
      super.clear();
      if (accountIdBuilder_ == null) {
        accountId_ = null;
      } else {
        accountId_ = null;
        accountIdBuilder_ = null;
      }
      type_ = "";

      if (scheduleBuilder_ == null) {
        schedule_ = null;
      } else {
        schedule_ = null;
        scheduleBuilder_ = null;
      }
      if (contextBuilder_ == null) {
        context_ = null;
      } else {
        context_ = null;
        contextBuilder_ = null;
      }
      allowDuplicate_ = false;

      return this;
    }

    @java.
    lang.Override
    public com.google.protobuf.Descriptors.Descriptor getDescriptorForType() {
      return io.harness.delegate.DelegateServiceOuterClass
          .internal_static_io_harness_delegate_CreatePerpetualTaskRequest_descriptor;
    }

    @java.
    lang.Override
    public io.harness.delegate.CreatePerpetualTaskRequest getDefaultInstanceForType() {
      return io.harness.delegate.CreatePerpetualTaskRequest.getDefaultInstance();
    }

    @java.
    lang.Override
    public io.harness.delegate.CreatePerpetualTaskRequest build() {
      io.harness.delegate.CreatePerpetualTaskRequest result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.
    lang.Override
    public io.harness.delegate.CreatePerpetualTaskRequest buildPartial() {
      io.harness.delegate.CreatePerpetualTaskRequest result = new io.harness.delegate.CreatePerpetualTaskRequest(this);
      if (accountIdBuilder_ == null) {
        result.accountId_ = accountId_;
      } else {
        result.accountId_ = accountIdBuilder_.build();
      }
      result.type_ = type_;
      if (scheduleBuilder_ == null) {
        result.schedule_ = schedule_;
      } else {
        result.schedule_ = scheduleBuilder_.build();
      }
      if (contextBuilder_ == null) {
        result.context_ = context_;
      } else {
        result.context_ = contextBuilder_.build();
      }
      result.allowDuplicate_ = allowDuplicate_;
      onBuilt();
      return result;
    }

    @java.lang.Override
    public Builder clone() {
      return super.clone();
    }
    @java.lang.Override
    public Builder setField(com.google.protobuf.Descriptors.FieldDescriptor field, java.lang.Object value) {
      return super.setField(field, value);
    }
    @java.lang.Override
    public Builder clearField(com.google.protobuf.Descriptors.FieldDescriptor field) {
      return super.clearField(field);
    }
    @java.lang.Override
    public Builder clearOneof(com.google.protobuf.Descriptors.OneofDescriptor oneof) {
      return super.clearOneof(oneof);
    }
    @java.lang.Override
    public Builder setRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field, int index, java.lang.Object value) {
      return super.setRepeatedField(field, index, value);
    }
    @java.lang.Override
    public Builder addRepeatedField(com.google.protobuf.Descriptors.FieldDescriptor field, java.lang.Object value) {
      return super.addRepeatedField(field, value);
    }
    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof io.harness.delegate.CreatePerpetualTaskRequest) {
        return mergeFrom((io.harness.delegate.CreatePerpetualTaskRequest) other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(io.harness.delegate.CreatePerpetualTaskRequest other) {
      if (other == io.harness.delegate.CreatePerpetualTaskRequest.getDefaultInstance())
        return this;
      if (other.hasAccountId()) {
        mergeAccountId(other.getAccountId());
      }
      if (!other.getType().isEmpty()) {
        type_ = other.type_;
        onChanged();
      }
      if (other.hasSchedule()) {
        mergeSchedule(other.getSchedule());
      }
      if (other.hasContext()) {
        mergeContext(other.getContext());
      }
      if (other.getAllowDuplicate() != false) {
        setAllowDuplicate(other.getAllowDuplicate());
      }
      this.mergeUnknownFields(other.unknownFields);
      onChanged();
      return this;
    }

    @java.lang.Override
    public final boolean isInitialized() {
      return true;
    }

    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
      io.harness.delegate.CreatePerpetualTaskRequest parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (io.harness.delegate.CreatePerpetualTaskRequest) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }

    private io.harness.delegate.AccountId accountId_;
    private com.google.protobuf.SingleFieldBuilderV3<io.harness.delegate.AccountId,
        io.harness.delegate.AccountId.Builder, io.harness.delegate.AccountIdOrBuilder> accountIdBuilder_;
    /**
     * <code>.io.harness.delegate.AccountId account_id = 1;</code>
     */
    public boolean hasAccountId() {
      return accountIdBuilder_ != null || accountId_ != null;
    }
    /**
     * <code>.io.harness.delegate.AccountId account_id = 1;</code>
     */
    public io.harness.delegate.AccountId getAccountId() {
      if (accountIdBuilder_ == null) {
        return accountId_ == null ? io.harness.delegate.AccountId.getDefaultInstance() : accountId_;
      } else {
        return accountIdBuilder_.getMessage();
      }
    }
    /**
     * <code>.io.harness.delegate.AccountId account_id = 1;</code>
     */
    public Builder setAccountId(io.harness.delegate.AccountId value) {
      if (accountIdBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        accountId_ = value;
        onChanged();
      } else {
        accountIdBuilder_.setMessage(value);
      }

      return this;
    }
    /**
     * <code>.io.harness.delegate.AccountId account_id = 1;</code>
     */
    public Builder setAccountId(io.harness.delegate.AccountId.Builder builderForValue) {
      if (accountIdBuilder_ == null) {
        accountId_ = builderForValue.build();
        onChanged();
      } else {
        accountIdBuilder_.setMessage(builderForValue.build());
      }

      return this;
    }
    /**
     * <code>.io.harness.delegate.AccountId account_id = 1;</code>
     */
    public Builder mergeAccountId(io.harness.delegate.AccountId value) {
      if (accountIdBuilder_ == null) {
        if (accountId_ != null) {
          accountId_ = io.harness.delegate.AccountId.newBuilder(accountId_).mergeFrom(value).buildPartial();
        } else {
          accountId_ = value;
        }
        onChanged();
      } else {
        accountIdBuilder_.mergeFrom(value);
      }

      return this;
    }
    /**
     * <code>.io.harness.delegate.AccountId account_id = 1;</code>
     */
    public Builder clearAccountId() {
      if (accountIdBuilder_ == null) {
        accountId_ = null;
        onChanged();
      } else {
        accountId_ = null;
        accountIdBuilder_ = null;
      }

      return this;
    }
    /**
     * <code>.io.harness.delegate.AccountId account_id = 1;</code>
     */
    public io.harness.delegate.AccountId.Builder getAccountIdBuilder() {
      onChanged();
      return getAccountIdFieldBuilder().getBuilder();
    }
    /**
     * <code>.io.harness.delegate.AccountId account_id = 1;</code>
     */
    public io.harness.delegate.AccountIdOrBuilder getAccountIdOrBuilder() {
      if (accountIdBuilder_ != null) {
        return accountIdBuilder_.getMessageOrBuilder();
      } else {
        return accountId_ == null ? io.harness.delegate.AccountId.getDefaultInstance() : accountId_;
      }
    }
    /**
     * <code>.io.harness.delegate.AccountId account_id = 1;</code>
     */
    private com.google.protobuf.SingleFieldBuilderV3<io.harness.delegate.AccountId,
        io.harness.delegate.AccountId.Builder, io.harness.delegate.AccountIdOrBuilder>
    getAccountIdFieldBuilder() {
      if (accountIdBuilder_ == null) {
        accountIdBuilder_ = new com.google.protobuf.SingleFieldBuilderV3<io.harness.delegate.AccountId,
            io.harness.delegate.AccountId.Builder, io.harness.delegate.AccountIdOrBuilder>(
            getAccountId(), getParentForChildren(), isClean());
        accountId_ = null;
      }
      return accountIdBuilder_;
    }

    private java.lang.Object type_ = "";
    /**
     * <code>string type = 2;</code>
     */
    public java.lang.String getType() {
      java.lang.Object ref = type_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        type_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <code>string type = 2;</code>
     */
    public com.google.protobuf.ByteString getTypeBytes() {
      java.lang.Object ref = type_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = com.google.protobuf.ByteString.copyFromUtf8((java.lang.String) ref);
        type_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <code>string type = 2;</code>
     */
    public Builder setType(java.lang.String value) {
      if (value == null) {
        throw new NullPointerException();
      }

      type_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>string type = 2;</code>
     */
    public Builder clearType() {
      type_ = getDefaultInstance().getType();
      onChanged();
      return this;
    }
    /**
     * <code>string type = 2;</code>
     */
    public Builder setTypeBytes(com.google.protobuf.ByteString value) {
      if (value == null) {
        throw new NullPointerException();
      }
      checkByteStringIsUtf8(value);

      type_ = value;
      onChanged();
      return this;
    }

    private io.harness.perpetualtask.PerpetualTaskSchedule schedule_;
    private com.google.protobuf.SingleFieldBuilderV3<io.harness.perpetualtask.PerpetualTaskSchedule,
        io.harness.perpetualtask.PerpetualTaskSchedule.Builder, io.harness.perpetualtask.PerpetualTaskScheduleOrBuilder>
        scheduleBuilder_;
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskSchedule schedule = 3;</code>
     */
    public boolean hasSchedule() {
      return scheduleBuilder_ != null || schedule_ != null;
    }
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskSchedule schedule = 3;</code>
     */
    public io.harness.perpetualtask.PerpetualTaskSchedule getSchedule() {
      if (scheduleBuilder_ == null) {
        return schedule_ == null ? io.harness.perpetualtask.PerpetualTaskSchedule.getDefaultInstance() : schedule_;
      } else {
        return scheduleBuilder_.getMessage();
      }
    }
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskSchedule schedule = 3;</code>
     */
    public Builder setSchedule(io.harness.perpetualtask.PerpetualTaskSchedule value) {
      if (scheduleBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        schedule_ = value;
        onChanged();
      } else {
        scheduleBuilder_.setMessage(value);
      }

      return this;
    }
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskSchedule schedule = 3;</code>
     */
    public Builder setSchedule(io.harness.perpetualtask.PerpetualTaskSchedule.Builder builderForValue) {
      if (scheduleBuilder_ == null) {
        schedule_ = builderForValue.build();
        onChanged();
      } else {
        scheduleBuilder_.setMessage(builderForValue.build());
      }

      return this;
    }
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskSchedule schedule = 3;</code>
     */
    public Builder mergeSchedule(io.harness.perpetualtask.PerpetualTaskSchedule value) {
      if (scheduleBuilder_ == null) {
        if (schedule_ != null) {
          schedule_ =
              io.harness.perpetualtask.PerpetualTaskSchedule.newBuilder(schedule_).mergeFrom(value).buildPartial();
        } else {
          schedule_ = value;
        }
        onChanged();
      } else {
        scheduleBuilder_.mergeFrom(value);
      }

      return this;
    }
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskSchedule schedule = 3;</code>
     */
    public Builder clearSchedule() {
      if (scheduleBuilder_ == null) {
        schedule_ = null;
        onChanged();
      } else {
        schedule_ = null;
        scheduleBuilder_ = null;
      }

      return this;
    }
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskSchedule schedule = 3;</code>
     */
    public io.harness.perpetualtask.PerpetualTaskSchedule.Builder getScheduleBuilder() {
      onChanged();
      return getScheduleFieldBuilder().getBuilder();
    }
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskSchedule schedule = 3;</code>
     */
    public io.harness.perpetualtask.PerpetualTaskScheduleOrBuilder getScheduleOrBuilder() {
      if (scheduleBuilder_ != null) {
        return scheduleBuilder_.getMessageOrBuilder();
      } else {
        return schedule_ == null ? io.harness.perpetualtask.PerpetualTaskSchedule.getDefaultInstance() : schedule_;
      }
    }
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskSchedule schedule = 3;</code>
     */
    private com.google.protobuf.SingleFieldBuilderV3<io.harness.perpetualtask.PerpetualTaskSchedule,
        io.harness.perpetualtask.PerpetualTaskSchedule.Builder, io.harness.perpetualtask.PerpetualTaskScheduleOrBuilder>
    getScheduleFieldBuilder() {
      if (scheduleBuilder_ == null) {
        scheduleBuilder_ = new com.google.protobuf.SingleFieldBuilderV3<io.harness.perpetualtask.PerpetualTaskSchedule,
            io.harness.perpetualtask.PerpetualTaskSchedule.Builder,
            io.harness.perpetualtask.PerpetualTaskScheduleOrBuilder>(getSchedule(), getParentForChildren(), isClean());
        schedule_ = null;
      }
      return scheduleBuilder_;
    }

    private io.harness.perpetualtask.PerpetualTaskContext context_;
    private com.google.protobuf.SingleFieldBuilderV3<io.harness.perpetualtask.PerpetualTaskContext,
        io.harness.perpetualtask.PerpetualTaskContext.Builder, io.harness.perpetualtask.PerpetualTaskContextOrBuilder>
        contextBuilder_;
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskContext context = 4;</code>
     */
    public boolean hasContext() {
      return contextBuilder_ != null || context_ != null;
    }
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskContext context = 4;</code>
     */
    public io.harness.perpetualtask.PerpetualTaskContext getContext() {
      if (contextBuilder_ == null) {
        return context_ == null ? io.harness.perpetualtask.PerpetualTaskContext.getDefaultInstance() : context_;
      } else {
        return contextBuilder_.getMessage();
      }
    }
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskContext context = 4;</code>
     */
    public Builder setContext(io.harness.perpetualtask.PerpetualTaskContext value) {
      if (contextBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        context_ = value;
        onChanged();
      } else {
        contextBuilder_.setMessage(value);
      }

      return this;
    }
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskContext context = 4;</code>
     */
    public Builder setContext(io.harness.perpetualtask.PerpetualTaskContext.Builder builderForValue) {
      if (contextBuilder_ == null) {
        context_ = builderForValue.build();
        onChanged();
      } else {
        contextBuilder_.setMessage(builderForValue.build());
      }

      return this;
    }
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskContext context = 4;</code>
     */
    public Builder mergeContext(io.harness.perpetualtask.PerpetualTaskContext value) {
      if (contextBuilder_ == null) {
        if (context_ != null) {
          context_ = io.harness.perpetualtask.PerpetualTaskContext.newBuilder(context_).mergeFrom(value).buildPartial();
        } else {
          context_ = value;
        }
        onChanged();
      } else {
        contextBuilder_.mergeFrom(value);
      }

      return this;
    }
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskContext context = 4;</code>
     */
    public Builder clearContext() {
      if (contextBuilder_ == null) {
        context_ = null;
        onChanged();
      } else {
        context_ = null;
        contextBuilder_ = null;
      }

      return this;
    }
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskContext context = 4;</code>
     */
    public io.harness.perpetualtask.PerpetualTaskContext.Builder getContextBuilder() {
      onChanged();
      return getContextFieldBuilder().getBuilder();
    }
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskContext context = 4;</code>
     */
    public io.harness.perpetualtask.PerpetualTaskContextOrBuilder getContextOrBuilder() {
      if (contextBuilder_ != null) {
        return contextBuilder_.getMessageOrBuilder();
      } else {
        return context_ == null ? io.harness.perpetualtask.PerpetualTaskContext.getDefaultInstance() : context_;
      }
    }
    /**
     * <code>.io.harness.perpetualtask.PerpetualTaskContext context = 4;</code>
     */
    private com.google.protobuf.SingleFieldBuilderV3<io.harness.perpetualtask.PerpetualTaskContext,
        io.harness.perpetualtask.PerpetualTaskContext.Builder, io.harness.perpetualtask.PerpetualTaskContextOrBuilder>
    getContextFieldBuilder() {
      if (contextBuilder_ == null) {
        contextBuilder_ = new com.google.protobuf.SingleFieldBuilderV3<io.harness.perpetualtask.PerpetualTaskContext,
            io.harness.perpetualtask.PerpetualTaskContext.Builder,
            io.harness.perpetualtask.PerpetualTaskContextOrBuilder>(getContext(), getParentForChildren(), isClean());
        context_ = null;
      }
      return contextBuilder_;
    }

    private boolean allowDuplicate_;
    /**
     * <code>bool allow_duplicate = 5;</code>
     */
    public boolean getAllowDuplicate() {
      return allowDuplicate_;
    }
    /**
     * <code>bool allow_duplicate = 5;</code>
     */
    public Builder setAllowDuplicate(boolean value) {
      allowDuplicate_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>bool allow_duplicate = 5;</code>
     */
    public Builder clearAllowDuplicate() {
      allowDuplicate_ = false;
      onChanged();
      return this;
    }
    @java.lang.Override
    public final Builder setUnknownFields(final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.setUnknownFields(unknownFields);
    }

    @java.lang.Override
    public final Builder mergeUnknownFields(final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.mergeUnknownFields(unknownFields);
    }

    // @@protoc_insertion_point(builder_scope:io.harness.delegate.CreatePerpetualTaskRequest)
  }

  // @@protoc_insertion_point(class_scope:io.harness.delegate.CreatePerpetualTaskRequest)
  private static final io.harness.delegate.CreatePerpetualTaskRequest DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new io.harness.delegate.CreatePerpetualTaskRequest();
  }

  public static io.harness.delegate.CreatePerpetualTaskRequest getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<CreatePerpetualTaskRequest> PARSER =
      new com.google.protobuf.AbstractParser<CreatePerpetualTaskRequest>() {
        @java.lang.Override
        public CreatePerpetualTaskRequest parsePartialFrom(
            com.google.protobuf.CodedInputStream input, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws com.google.protobuf.InvalidProtocolBufferException {
          return new CreatePerpetualTaskRequest(input, extensionRegistry);
        }
      };

  public static com.google.protobuf.Parser<CreatePerpetualTaskRequest> parser() {
    return PARSER;
  }

  @java.
  lang.Override
  public com.google.protobuf.Parser<CreatePerpetualTaskRequest> getParserForType() {
    return PARSER;
  }

  @java.
  lang.Override
  public io.harness.delegate.CreatePerpetualTaskRequest getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }
}
