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
public class Plugins implements Serializable {
  private static final long serialVersionUID = 1L;

  private String id;
  private String accountIdentifier;
  private String identifier;
  private String name;
  private Boolean enabled;
  private Long createdAt;
  private Long lastUpdatedAt;

  public Plugins() {}

  public Plugins(Plugins value) {
    this.id = value.id;
    this.accountIdentifier = value.accountIdentifier;
    this.identifier = value.identifier;
    this.name = value.name;
    this.enabled = value.enabled;
    this.createdAt = value.createdAt;
    this.lastUpdatedAt = value.lastUpdatedAt;
  }

  public Plugins(String id, String accountIdentifier, String identifier, String name, Boolean enabled, Long createdAt,
      Long lastUpdatedAt) {
    this.id = id;
    this.accountIdentifier = accountIdentifier;
    this.identifier = identifier;
    this.name = name;
    this.enabled = enabled;
    this.createdAt = createdAt;
    this.lastUpdatedAt = lastUpdatedAt;
  }

  /**
   * Getter for <code>public.plugins.id</code>.
   */
  public String getId() {
    return this.id;
  }

  /**
   * Setter for <code>public.plugins.id</code>.
   */
  public Plugins setId(String id) {
    this.id = id;
    return this;
  }

  /**
   * Getter for <code>public.plugins.account_identifier</code>.
   */
  public String getAccountIdentifier() {
    return this.accountIdentifier;
  }

  /**
   * Setter for <code>public.plugins.account_identifier</code>.
   */
  public Plugins setAccountIdentifier(String accountIdentifier) {
    this.accountIdentifier = accountIdentifier;
    return this;
  }

  /**
   * Getter for <code>public.plugins.identifier</code>.
   */
  public String getIdentifier() {
    return this.identifier;
  }

  /**
   * Setter for <code>public.plugins.identifier</code>.
   */
  public Plugins setIdentifier(String identifier) {
    this.identifier = identifier;
    return this;
  }

  /**
   * Getter for <code>public.plugins.name</code>.
   */
  public String getName() {
    return this.name;
  }

  /**
   * Setter for <code>public.plugins.name</code>.
   */
  public Plugins setName(String name) {
    this.name = name;
    return this;
  }

  /**
   * Getter for <code>public.plugins.enabled</code>.
   */
  public Boolean getEnabled() {
    return this.enabled;
  }

  /**
   * Setter for <code>public.plugins.enabled</code>.
   */
  public Plugins setEnabled(Boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  /**
   * Getter for <code>public.plugins.created_at</code>.
   */
  public Long getCreatedAt() {
    return this.createdAt;
  }

  /**
   * Setter for <code>public.plugins.created_at</code>.
   */
  public Plugins setCreatedAt(Long createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /**
   * Getter for <code>public.plugins.last_updated_at</code>.
   */
  public Long getLastUpdatedAt() {
    return this.lastUpdatedAt;
  }

  /**
   * Setter for <code>public.plugins.last_updated_at</code>.
   */
  public Plugins setLastUpdatedAt(Long lastUpdatedAt) {
    this.lastUpdatedAt = lastUpdatedAt;
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
    final Plugins other = (Plugins) obj;
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
    if (enabled == null) {
      if (other.enabled != null)
        return false;
    } else if (!enabled.equals(other.enabled))
      return false;
    if (createdAt == null) {
      if (other.createdAt != null)
        return false;
    } else if (!createdAt.equals(other.createdAt))
      return false;
    if (lastUpdatedAt == null) {
      if (other.lastUpdatedAt != null)
        return false;
    } else if (!lastUpdatedAt.equals(other.lastUpdatedAt))
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
    result = prime * result + ((this.enabled == null) ? 0 : this.enabled.hashCode());
    result = prime * result + ((this.createdAt == null) ? 0 : this.createdAt.hashCode());
    result = prime * result + ((this.lastUpdatedAt == null) ? 0 : this.lastUpdatedAt.hashCode());
    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("Plugins (");

    sb.append(id);
    sb.append(", ").append(accountIdentifier);
    sb.append(", ").append(identifier);
    sb.append(", ").append(name);
    sb.append(", ").append(enabled);
    sb.append(", ").append(createdAt);
    sb.append(", ").append(lastUpdatedAt);

    sb.append(")");
    return sb.toString();
  }
}
