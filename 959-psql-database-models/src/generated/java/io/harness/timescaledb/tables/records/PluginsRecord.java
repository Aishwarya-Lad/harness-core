/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/*
 * This file is generated by jOOQ.
 */
package io.harness.timescaledb.tables.records;

import io.harness.timescaledb.tables.Plugins;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record7;
import org.jooq.Row7;
import org.jooq.impl.UpdatableRecordImpl;

/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({"all", "unchecked", "rawtypes"})
public class PluginsRecord
    extends UpdatableRecordImpl<PluginsRecord> implements Record7<String, String, String, String, Boolean, Long, Long> {
  private static final long serialVersionUID = 1L;

  /**
   * Setter for <code>public.plugins.id</code>.
   */
  public PluginsRecord setId(String value) {
    set(0, value);
    return this;
  }

  /**
   * Getter for <code>public.plugins.id</code>.
   */
  public String getId() {
    return (String) get(0);
  }

  /**
   * Setter for <code>public.plugins.account_identifier</code>.
   */
  public PluginsRecord setAccountIdentifier(String value) {
    set(1, value);
    return this;
  }

  /**
   * Getter for <code>public.plugins.account_identifier</code>.
   */
  public String getAccountIdentifier() {
    return (String) get(1);
  }

  /**
   * Setter for <code>public.plugins.identifier</code>.
   */
  public PluginsRecord setIdentifier(String value) {
    set(2, value);
    return this;
  }

  /**
   * Getter for <code>public.plugins.identifier</code>.
   */
  public String getIdentifier() {
    return (String) get(2);
  }

  /**
   * Setter for <code>public.plugins.name</code>.
   */
  public PluginsRecord setName(String value) {
    set(3, value);
    return this;
  }

  /**
   * Getter for <code>public.plugins.name</code>.
   */
  public String getName() {
    return (String) get(3);
  }

  /**
   * Setter for <code>public.plugins.enabled</code>.
   */
  public PluginsRecord setEnabled(Boolean value) {
    set(4, value);
    return this;
  }

  /**
   * Getter for <code>public.plugins.enabled</code>.
   */
  public Boolean getEnabled() {
    return (Boolean) get(4);
  }

  /**
   * Setter for <code>public.plugins.created_at</code>.
   */
  public PluginsRecord setCreatedAt(Long value) {
    set(5, value);
    return this;
  }

  /**
   * Getter for <code>public.plugins.created_at</code>.
   */
  public Long getCreatedAt() {
    return (Long) get(5);
  }

  /**
   * Setter for <code>public.plugins.last_updated_at</code>.
   */
  public PluginsRecord setLastUpdatedAt(Long value) {
    set(6, value);
    return this;
  }

  /**
   * Getter for <code>public.plugins.last_updated_at</code>.
   */
  public Long getLastUpdatedAt() {
    return (Long) get(6);
  }

  // -------------------------------------------------------------------------
  // Primary key information
  // -------------------------------------------------------------------------

  @Override
  public Record1<String> key() {
    return (Record1) super.key();
  }

  // -------------------------------------------------------------------------
  // Record7 type implementation
  // -------------------------------------------------------------------------

  @Override
  public Row7<String, String, String, String, Boolean, Long, Long> fieldsRow() {
    return (Row7) super.fieldsRow();
  }

  @Override
  public Row7<String, String, String, String, Boolean, Long, Long> valuesRow() {
    return (Row7) super.valuesRow();
  }

  @Override
  public Field<String> field1() {
    return Plugins.PLUGINS.ID;
  }

  @Override
  public Field<String> field2() {
    return Plugins.PLUGINS.ACCOUNT_IDENTIFIER;
  }

  @Override
  public Field<String> field3() {
    return Plugins.PLUGINS.IDENTIFIER;
  }

  @Override
  public Field<String> field4() {
    return Plugins.PLUGINS.NAME;
  }

  @Override
  public Field<Boolean> field5() {
    return Plugins.PLUGINS.ENABLED;
  }

  @Override
  public Field<Long> field6() {
    return Plugins.PLUGINS.CREATED_AT;
  }

  @Override
  public Field<Long> field7() {
    return Plugins.PLUGINS.LAST_UPDATED_AT;
  }

  @Override
  public String component1() {
    return getId();
  }

  @Override
  public String component2() {
    return getAccountIdentifier();
  }

  @Override
  public String component3() {
    return getIdentifier();
  }

  @Override
  public String component4() {
    return getName();
  }

  @Override
  public Boolean component5() {
    return getEnabled();
  }

  @Override
  public Long component6() {
    return getCreatedAt();
  }

  @Override
  public Long component7() {
    return getLastUpdatedAt();
  }

  @Override
  public String value1() {
    return getId();
  }

  @Override
  public String value2() {
    return getAccountIdentifier();
  }

  @Override
  public String value3() {
    return getIdentifier();
  }

  @Override
  public String value4() {
    return getName();
  }

  @Override
  public Boolean value5() {
    return getEnabled();
  }

  @Override
  public Long value6() {
    return getCreatedAt();
  }

  @Override
  public Long value7() {
    return getLastUpdatedAt();
  }

  @Override
  public PluginsRecord value1(String value) {
    setId(value);
    return this;
  }

  @Override
  public PluginsRecord value2(String value) {
    setAccountIdentifier(value);
    return this;
  }

  @Override
  public PluginsRecord value3(String value) {
    setIdentifier(value);
    return this;
  }

  @Override
  public PluginsRecord value4(String value) {
    setName(value);
    return this;
  }

  @Override
  public PluginsRecord value5(Boolean value) {
    setEnabled(value);
    return this;
  }

  @Override
  public PluginsRecord value6(Long value) {
    setCreatedAt(value);
    return this;
  }

  @Override
  public PluginsRecord value7(Long value) {
    setLastUpdatedAt(value);
    return this;
  }

  @Override
  public PluginsRecord values(
      String value1, String value2, String value3, String value4, Boolean value5, Long value6, Long value7) {
    value1(value1);
    value2(value2);
    value3(value3);
    value4(value4);
    value5(value5);
    value6(value6);
    value7(value7);
    return this;
  }

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  /**
   * Create a detached PluginsRecord
   */
  public PluginsRecord() {
    super(Plugins.PLUGINS);
  }

  /**
   * Create a detached, initialised PluginsRecord
   */
  public PluginsRecord(String id, String accountIdentifier, String identifier, String name, Boolean enabled,
      Long createdAt, Long lastUpdatedAt) {
    super(Plugins.PLUGINS);

    setId(id);
    setAccountIdentifier(accountIdentifier);
    setIdentifier(identifier);
    setName(name);
    setEnabled(enabled);
    setCreatedAt(createdAt);
    setLastUpdatedAt(lastUpdatedAt);
  }
}
