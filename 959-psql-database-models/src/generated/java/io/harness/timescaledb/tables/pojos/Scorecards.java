/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/*
 * This file is generated by jOOQ.
 */
package io.harness.timescaledb.tables.pojos;

import java.io.Serializable;

/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({"all", "unchecked", "rawtypes"})
public class Scorecards implements Serializable {
  private static final long serialVersionUID = 1L;

  private String id;
  private String accountIdentifier;
  private String identifier;
  private String name;
  private String description;
  private String filter;
  private String weightageStrategy;
  private Short totalNumberOfChecks;
  private Short numberOfCustomChecks;
  private Boolean published;
  private Boolean deleted;
  private Long createdAt;
  private String createdBy;
  private Long lastUpdatedAt;
  private String lastUpdatedBy;

  public Scorecards() {}

  public Scorecards(Scorecards value) {
    this.id = value.id;
    this.accountIdentifier = value.accountIdentifier;
    this.identifier = value.identifier;
    this.name = value.name;
    this.description = value.description;
    this.filter = value.filter;
    this.weightageStrategy = value.weightageStrategy;
    this.totalNumberOfChecks = value.totalNumberOfChecks;
    this.numberOfCustomChecks = value.numberOfCustomChecks;
    this.published = value.published;
    this.deleted = value.deleted;
    this.createdAt = value.createdAt;
    this.createdBy = value.createdBy;
    this.lastUpdatedAt = value.lastUpdatedAt;
    this.lastUpdatedBy = value.lastUpdatedBy;
  }

  public Scorecards(String id, String accountIdentifier, String identifier, String name, String description,
      String filter, String weightageStrategy, Short totalNumberOfChecks, Short numberOfCustomChecks, Boolean published,
      Boolean deleted, Long createdAt, String createdBy, Long lastUpdatedAt, String lastUpdatedBy) {
    this.id = id;
    this.accountIdentifier = accountIdentifier;
    this.identifier = identifier;
    this.name = name;
    this.description = description;
    this.filter = filter;
    this.weightageStrategy = weightageStrategy;
    this.totalNumberOfChecks = totalNumberOfChecks;
    this.numberOfCustomChecks = numberOfCustomChecks;
    this.published = published;
    this.deleted = deleted;
    this.createdAt = createdAt;
    this.createdBy = createdBy;
    this.lastUpdatedAt = lastUpdatedAt;
    this.lastUpdatedBy = lastUpdatedBy;
  }

  /**
   * Getter for <code>public.scorecards.id</code>.
   */
  public String getId() {
    return this.id;
  }

  /**
   * Setter for <code>public.scorecards.id</code>.
   */
  public Scorecards setId(String id) {
    this.id = id;
    return this;
  }

  /**
   * Getter for <code>public.scorecards.account_identifier</code>.
   */
  public String getAccountIdentifier() {
    return this.accountIdentifier;
  }

  /**
   * Setter for <code>public.scorecards.account_identifier</code>.
   */
  public Scorecards setAccountIdentifier(String accountIdentifier) {
    this.accountIdentifier = accountIdentifier;
    return this;
  }

  /**
   * Getter for <code>public.scorecards.identifier</code>.
   */
  public String getIdentifier() {
    return this.identifier;
  }

  /**
   * Setter for <code>public.scorecards.identifier</code>.
   */
  public Scorecards setIdentifier(String identifier) {
    this.identifier = identifier;
    return this;
  }

  /**
   * Getter for <code>public.scorecards.name</code>.
   */
  public String getName() {
    return this.name;
  }

  /**
   * Setter for <code>public.scorecards.name</code>.
   */
  public Scorecards setName(String name) {
    this.name = name;
    return this;
  }

  /**
   * Getter for <code>public.scorecards.description</code>.
   */
  public String getDescription() {
    return this.description;
  }

  /**
   * Setter for <code>public.scorecards.description</code>.
   */
  public Scorecards setDescription(String description) {
    this.description = description;
    return this;
  }

  /**
   * Getter for <code>public.scorecards.filter</code>.
   */
  public String getFilter() {
    return this.filter;
  }

  /**
   * Setter for <code>public.scorecards.filter</code>.
   */
  public Scorecards setFilter(String filter) {
    this.filter = filter;
    return this;
  }

  /**
   * Getter for <code>public.scorecards.weightage_strategy</code>.
   */
  public String getWeightageStrategy() {
    return this.weightageStrategy;
  }

  /**
   * Setter for <code>public.scorecards.weightage_strategy</code>.
   */
  public Scorecards setWeightageStrategy(String weightageStrategy) {
    this.weightageStrategy = weightageStrategy;
    return this;
  }

  /**
   * Getter for <code>public.scorecards.total_number_of_checks</code>.
   */
  public Short getTotalNumberOfChecks() {
    return this.totalNumberOfChecks;
  }

  /**
   * Setter for <code>public.scorecards.total_number_of_checks</code>.
   */
  public Scorecards setTotalNumberOfChecks(Short totalNumberOfChecks) {
    this.totalNumberOfChecks = totalNumberOfChecks;
    return this;
  }

  /**
   * Getter for <code>public.scorecards.number_of_custom_checks</code>.
   */
  public Short getNumberOfCustomChecks() {
    return this.numberOfCustomChecks;
  }

  /**
   * Setter for <code>public.scorecards.number_of_custom_checks</code>.
   */
  public Scorecards setNumberOfCustomChecks(Short numberOfCustomChecks) {
    this.numberOfCustomChecks = numberOfCustomChecks;
    return this;
  }

  /**
   * Getter for <code>public.scorecards.published</code>.
   */
  public Boolean getPublished() {
    return this.published;
  }

  /**
   * Setter for <code>public.scorecards.published</code>.
   */
  public Scorecards setPublished(Boolean published) {
    this.published = published;
    return this;
  }

  /**
   * Getter for <code>public.scorecards.deleted</code>.
   */
  public Boolean getDeleted() {
    return this.deleted;
  }

  /**
   * Setter for <code>public.scorecards.deleted</code>.
   */
  public Scorecards setDeleted(Boolean deleted) {
    this.deleted = deleted;
    return this;
  }

  /**
   * Getter for <code>public.scorecards.created_at</code>.
   */
  public Long getCreatedAt() {
    return this.createdAt;
  }

  /**
   * Setter for <code>public.scorecards.created_at</code>.
   */
  public Scorecards setCreatedAt(Long createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /**
   * Getter for <code>public.scorecards.created_by</code>.
   */
  public String getCreatedBy() {
    return this.createdBy;
  }

  /**
   * Setter for <code>public.scorecards.created_by</code>.
   */
  public Scorecards setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
    return this;
  }

  /**
   * Getter for <code>public.scorecards.last_updated_at</code>.
   */
  public Long getLastUpdatedAt() {
    return this.lastUpdatedAt;
  }

  /**
   * Setter for <code>public.scorecards.last_updated_at</code>.
   */
  public Scorecards setLastUpdatedAt(Long lastUpdatedAt) {
    this.lastUpdatedAt = lastUpdatedAt;
    return this;
  }

  /**
   * Getter for <code>public.scorecards.last_updated_by</code>.
   */
  public String getLastUpdatedBy() {
    return this.lastUpdatedBy;
  }

  /**
   * Setter for <code>public.scorecards.last_updated_by</code>.
   */
  public Scorecards setLastUpdatedBy(String lastUpdatedBy) {
    this.lastUpdatedBy = lastUpdatedBy;
    return this;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    final Scorecards other = (Scorecards) obj;
    if (id == null) {
      if (other.id != null)
        return false;
    } else if (!id.equals(other.id))
      return false;
    if (accountIdentifier == null) {
      if (other.accountIdentifier != null)
        return false;
    } else if (!accountIdentifier.equals(other.accountIdentifier))
      return false;
    if (identifier == null) {
      if (other.identifier != null)
        return false;
    } else if (!identifier.equals(other.identifier))
      return false;
    if (name == null) {
      if (other.name != null)
        return false;
    } else if (!name.equals(other.name))
      return false;
    if (description == null) {
      if (other.description != null)
        return false;
    } else if (!description.equals(other.description))
      return false;
    if (filter == null) {
      if (other.filter != null)
        return false;
    } else if (!filter.equals(other.filter))
      return false;
    if (weightageStrategy == null) {
      if (other.weightageStrategy != null)
        return false;
    } else if (!weightageStrategy.equals(other.weightageStrategy))
      return false;
    if (totalNumberOfChecks == null) {
      if (other.totalNumberOfChecks != null)
        return false;
    } else if (!totalNumberOfChecks.equals(other.totalNumberOfChecks))
      return false;
    if (numberOfCustomChecks == null) {
      if (other.numberOfCustomChecks != null)
        return false;
    } else if (!numberOfCustomChecks.equals(other.numberOfCustomChecks))
      return false;
    if (published == null) {
      if (other.published != null)
        return false;
    } else if (!published.equals(other.published))
      return false;
    if (deleted == null) {
      if (other.deleted != null)
        return false;
    } else if (!deleted.equals(other.deleted))
      return false;
    if (createdAt == null) {
      if (other.createdAt != null)
        return false;
    } else if (!createdAt.equals(other.createdAt))
      return false;
    if (createdBy == null) {
      if (other.createdBy != null)
        return false;
    } else if (!createdBy.equals(other.createdBy))
      return false;
    if (lastUpdatedAt == null) {
      if (other.lastUpdatedAt != null)
        return false;
    } else if (!lastUpdatedAt.equals(other.lastUpdatedAt))
      return false;
    if (lastUpdatedBy == null) {
      if (other.lastUpdatedBy != null)
        return false;
    } else if (!lastUpdatedBy.equals(other.lastUpdatedBy))
      return false;
    return true;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((this.id == null) ? 0 : this.id.hashCode());
    result = prime * result + ((this.accountIdentifier == null) ? 0 : this.accountIdentifier.hashCode());
    result = prime * result + ((this.identifier == null) ? 0 : this.identifier.hashCode());
    result = prime * result + ((this.name == null) ? 0 : this.name.hashCode());
    result = prime * result + ((this.description == null) ? 0 : this.description.hashCode());
    result = prime * result + ((this.filter == null) ? 0 : this.filter.hashCode());
    result = prime * result + ((this.weightageStrategy == null) ? 0 : this.weightageStrategy.hashCode());
    result = prime * result + ((this.totalNumberOfChecks == null) ? 0 : this.totalNumberOfChecks.hashCode());
    result = prime * result + ((this.numberOfCustomChecks == null) ? 0 : this.numberOfCustomChecks.hashCode());
    result = prime * result + ((this.published == null) ? 0 : this.published.hashCode());
    result = prime * result + ((this.deleted == null) ? 0 : this.deleted.hashCode());
    result = prime * result + ((this.createdAt == null) ? 0 : this.createdAt.hashCode());
    result = prime * result + ((this.createdBy == null) ? 0 : this.createdBy.hashCode());
    result = prime * result + ((this.lastUpdatedAt == null) ? 0 : this.lastUpdatedAt.hashCode());
    result = prime * result + ((this.lastUpdatedBy == null) ? 0 : this.lastUpdatedBy.hashCode());
    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("Scorecards (");

    sb.append(id);
    sb.append(", ").append(accountIdentifier);
    sb.append(", ").append(identifier);
    sb.append(", ").append(name);
    sb.append(", ").append(description);
    sb.append(", ").append(filter);
    sb.append(", ").append(weightageStrategy);
    sb.append(", ").append(totalNumberOfChecks);
    sb.append(", ").append(numberOfCustomChecks);
    sb.append(", ").append(published);
    sb.append(", ").append(deleted);
    sb.append(", ").append(createdAt);
    sb.append(", ").append(createdBy);
    sb.append(", ").append(lastUpdatedAt);
    sb.append(", ").append(lastUpdatedBy);

    sb.append(")");
    return sb.toString();
  }
}
