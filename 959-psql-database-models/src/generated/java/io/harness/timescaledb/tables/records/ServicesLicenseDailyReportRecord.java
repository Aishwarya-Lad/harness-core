/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/*
 * This file is generated by jOOQ.
 */
package io.harness.timescaledb.tables.records;

import io.harness.timescaledb.tables.ServicesLicenseDailyReport;

import java.time.LocalDate;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Row3;
import org.jooq.impl.UpdatableRecordImpl;

/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({"all", "unchecked", "rawtypes"})
public class ServicesLicenseDailyReportRecord
    extends UpdatableRecordImpl<ServicesLicenseDailyReportRecord> implements Record3<String, LocalDate, Integer> {
  private static final long serialVersionUID = 1L;

  /**
   * Setter for <code>public.services_license_daily_report.account_id</code>.
   */
  public ServicesLicenseDailyReportRecord setAccountId(String value) {
    set(0, value);
    return this;
  }

  /**
   * Getter for <code>public.services_license_daily_report.account_id</code>.
   */
  public String getAccountId() {
    return (String) get(0);
  }

  /**
   * Setter for <code>public.services_license_daily_report.reported_day</code>.
   */
  public ServicesLicenseDailyReportRecord setReportedDay(LocalDate value) {
    set(1, value);
    return this;
  }

  /**
   * Getter for <code>public.services_license_daily_report.reported_day</code>.
   */
  public LocalDate getReportedDay() {
    return (LocalDate) get(1);
  }

  /**
   * Setter for <code>public.services_license_daily_report.license_count</code>.
   */
  public ServicesLicenseDailyReportRecord setLicenseCount(Integer value) {
    set(2, value);
    return this;
  }

  /**
   * Getter for <code>public.services_license_daily_report.license_count</code>.
   */
  public Integer getLicenseCount() {
    return (Integer) get(2);
  }

  // -------------------------------------------------------------------------
  // Primary key information
  // -------------------------------------------------------------------------

  @Override
  public Record2<String, LocalDate> key() {
    return (Record2) super.key();
  }

  // -------------------------------------------------------------------------
  // Record3 type implementation
  // -------------------------------------------------------------------------

  @Override
  public Row3<String, LocalDate, Integer> fieldsRow() {
    return (Row3) super.fieldsRow();
  }

  @Override
  public Row3<String, LocalDate, Integer> valuesRow() {
    return (Row3) super.valuesRow();
  }

  @Override
  public Field<String> field1() {
    return ServicesLicenseDailyReport.SERVICES_LICENSE_DAILY_REPORT.ACCOUNT_ID;
  }

  @Override
  public Field<LocalDate> field2() {
    return ServicesLicenseDailyReport.SERVICES_LICENSE_DAILY_REPORT.REPORTED_DAY;
  }

  @Override
  public Field<Integer> field3() {
    return ServicesLicenseDailyReport.SERVICES_LICENSE_DAILY_REPORT.LICENSE_COUNT;
  }

  @Override
  public String component1() {
    return getAccountId();
  }

  @Override
  public LocalDate component2() {
    return getReportedDay();
  }

  @Override
  public Integer component3() {
    return getLicenseCount();
  }

  @Override
  public String value1() {
    return getAccountId();
  }

  @Override
  public LocalDate value2() {
    return getReportedDay();
  }

  @Override
  public Integer value3() {
    return getLicenseCount();
  }

  @Override
  public ServicesLicenseDailyReportRecord value1(String value) {
    setAccountId(value);
    return this;
  }

  @Override
  public ServicesLicenseDailyReportRecord value2(LocalDate value) {
    setReportedDay(value);
    return this;
  }

  @Override
  public ServicesLicenseDailyReportRecord value3(Integer value) {
    setLicenseCount(value);
    return this;
  }

  @Override
  public ServicesLicenseDailyReportRecord values(String value1, LocalDate value2, Integer value3) {
    value1(value1);
    value2(value2);
    value3(value3);
    return this;
  }

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  /**
   * Create a detached ServicesLicenseDailyReportRecord
   */
  public ServicesLicenseDailyReportRecord() {
    super(ServicesLicenseDailyReport.SERVICES_LICENSE_DAILY_REPORT);
  }

  /**
   * Create a detached, initialised ServicesLicenseDailyReportRecord
   */
  public ServicesLicenseDailyReportRecord(String accountId, LocalDate reportedDay, Integer licenseCount) {
    super(ServicesLicenseDailyReport.SERVICES_LICENSE_DAILY_REPORT);

    setAccountId(accountId);
    setReportedDay(reportedDay);
    setLicenseCount(licenseCount);
  }
}
