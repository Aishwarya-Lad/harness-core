/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PL)
public enum ResourceType {
  ORGANIZATION(ResourceTypeConstants.ORGANIZATION),
  PROJECT(ResourceTypeConstants.PROJECT),
  USER_GROUP(ResourceTypeConstants.USER_GROUP),
  SECRET(ResourceTypeConstants.SECRET),
  RESOURCE_GROUP(ResourceTypeConstants.RESOURCE_GROUP),
  USER(ResourceTypeConstants.USER),
  ROLE(ResourceTypeConstants.ROLE),
  PIPELINE(ResourceTypeConstants.PIPELINE),
  TRIGGER(ResourceTypeConstants.TRIGGER),
  TEMPLATE(ResourceTypeConstants.TEMPLATE),
  INPUT_SET(ResourceTypeConstants.INPUT_SET),
  DELEGATE_CONFIGURATION(ResourceTypeConstants.DELEGATE_CONFIGURATION),
  DELEGATE_GROUPS(ResourceTypeConstants.DELEGATE_GROUPS),
  SERVICE(ResourceTypeConstants.SERVICE),
  ENVIRONMENT(ResourceTypeConstants.ENVIRONMENT),
  ENVIRONMENT_GROUP(ResourceTypeConstants.ENVIRONMENT_GROUP),
  DELEGATE(ResourceTypeConstants.DELEGATE),
  SERVICE_ACCOUNT(ResourceTypeConstants.SERVICE_ACCOUNT),
  CONNECTOR(ResourceTypeConstants.CONNECTOR),
  API_KEY(ResourceTypeConstants.API_KEY),
  TOKEN(ResourceTypeConstants.TOKEN),
  DELEGATE_TOKEN(ResourceTypeConstants.DELEGATE_TOKEN),
  DASHBOARD(ResourceTypeConstants.DASHBOARD),
  DASHBOARD_FOLDER(ResourceTypeConstants.DASHBOARD_FOLDER),
  GOVERNANCE_POLICY(ResourceTypeConstants.GOVERNANCE_POLICY),
  GOVERNANCE_POLICY_SET(ResourceTypeConstants.GOVERNANCE_POLICY_SET),
  VARIABLE(ResourceTypeConstants.VARIABLE),
  CHAOS_HUB(ResourceTypeConstants.CHAOS_HUB),
  MONITORED_SERVICE(ResourceTypeConstants.MONITORED_SERVICE),
  CHAOS_INFRASTRUCTURE(ResourceTypeConstants.CHAOS_INFRASTRUCTURE),
  CHAOS_EXPERIMENT(ResourceTypeConstants.CHAOS_EXPERIMENT),
  CHAOS_GAMEDAY(ResourceTypeConstants.CHAOS_GAMEDAY),
  STO_TARGET(ResourceTypeConstants.STO_TARGET),
  STO_EXEMPTION(ResourceTypeConstants.STO_EXEMPTION),
  SERVICE_LEVEL_OBJECTIVE(ResourceTypeConstants.SERVICE_LEVEL_OBJECTIVE),
  PERSPECTIVE(ResourceTypeConstants.PERSPECTIVE),
  PERSPECTIVE_BUDGET(ResourceTypeConstants.PERSPECTIVE_BUDGET),
  PERSPECTIVE_REPORT(ResourceTypeConstants.PERSPECTIVE_REPORT),
  COST_CATEGORY(ResourceTypeConstants.COST_CATEGORY),
  SMTP(ResourceTypeConstants.SMTP),
  PERSPECTIVE_FOLDER(ResourceTypeConstants.PERSPECTIVE_FOLDER),
  AUTOSTOPPING_RULE(ResourceTypeConstants.AUTOSTOPPING_RULE),
  AUTOSTOPPING_LB(ResourceTypeConstants.AUTOSTOPPING_LB),
  AUTOSTOPPING_STARTSTOP(ResourceTypeConstants.AUTOSTOPPING_STARTSTOP),
  SETTING(ResourceTypeConstants.SETTING),
  NG_LOGIN_SETTINGS(ResourceTypeConstants.NG_LOGIN_SETTINGS),
  DEPLOYMENT_FREEZE(ResourceTypeConstants.DEPLOYMENT_FREEZE),
  CLOUD_ASSET_GOVERNANCE_RULE(ResourceTypeConstants.CLOUD_ASSET_GOVERNANCE_RULE),
  CLOUD_ASSET_GOVERNANCE_RULE_SET(ResourceTypeConstants.CLOUD_ASSET_GOVERNANCE_RULE_SET),
  CLOUD_ASSET_GOVERNANCE_RULE_ENFORCEMENT(ResourceTypeConstants.CLOUD_ASSET_GOVERNANCE_RULE_ENFORCEMENT),
  TARGET_GROUP(ResourceTypeConstants.TARGET_GROUP),
  FEATURE_FLAG(ResourceTypeConstants.FEATURE_FLAG),
  NG_ACCOUNT_DETAILS(ResourceTypeConstants.NG_ACCOUNT_DETAILS),
  BUDGET_GROUP(ResourceTypeConstants.BUDGET_GROUP),
  IP_ALLOWLIST_CONFIG(ResourceTypeConstants.IP_ALLOWLIST_CONFIG),
  NETWORK_MAP(ResourceTypeConstants.NETWORK_MAP),
  CET_AGENT_TOKEN(ResourceTypeConstants.CET_AGENT_TOKEN),
  CET_CRITICAL_EVENT(ResourceTypeConstants.CET_CRITICAL_EVENT),
  CHAOS_SECURITY_GOVERNANCE(ResourceTypeConstants.CHAOS_SECURITY_GOVERNANCE),
  END_USER_LICENSE_AGREEMENT(ResourceTypeConstants.EULA),
  WORKSPACE(ResourceTypeConstants.WORKSPACE),
  SEI_CONFIGURATION_SETTINGS(ResourceTypeConstants.SEI_CONFIGURATION_SETTINGS),
  SEI_COLLECTIONS(ResourceTypeConstants.SEI_COLLECTIONS),
  SEI_INSIGHTS(ResourceTypeConstants.SEI_INSIGHTS),
  CET_SAVED_FILTER(ResourceTypeConstants.CET_SAVED_FILTER),
  GITOPS_AGENT(ResourceTypeConstants.GITOPS_AGENT),
  GITOPS_REPOSITORY(ResourceTypeConstants.GITOPS_REPOSITORY),
  GITOPS_CLUSTER(ResourceTypeConstants.GITOPS_CLUSTER),
  GITOPS_CREDENTIAL_TEMPLATE(ResourceTypeConstants.GITOPS_CREDENTIAL_TEMPLATE),
  GITOPS_REPOSITORY_CERTIFICATE(ResourceTypeConstants.GITOPS_REPOSITORY_CERTIFICATE),
  GITOPS_GNUPG_KEY(ResourceTypeConstants.GITOPS_GNUPG_KEY),
  GITOPS_PROJECT_MAPPING(ResourceTypeConstants.GITOPS_PROJECT_MAPPING),
  GITOPS_APPLICATION(ResourceTypeConstants.GITOPS_APPLICATION),
  CODE_REPOSITORY(ResourceTypeConstants.CODE_REPOSITORY),
  MODULE_LICENSE(ResourceTypeConstants.MODULE_LICENSE),
  IDP_BACKSTAGE_CATALOG_ENTITY(ResourceTypeConstants.IDP_BACKSTAGE_CATALOG_ENTITY),
  IDP_APP_CONFIGS(ResourceTypeConstants.IDP_APP_CONFIGS),
  IDP_CONFIG_ENV_VARIABLES(ResourceTypeConstants.IDP_CONFIG_ENV_VARIABLES),
  IDP_PROXY_HOST(ResourceTypeConstants.IDP_PROXY_HOST),
  IDP_SCORECARDS(ResourceTypeConstants.IDP_SCORECARDS),
  IDP_CHECKS(ResourceTypeConstants.IDP_CHECKS),
  IDP_ALLOW_LIST(ResourceTypeConstants.IDP_ALLOW_LIST),
  IDP_OAUTH_CONFIG(ResourceTypeConstants.IDP_OAUTH_CONFIG),
  IDP_CATALOG_CONNECTOR(ResourceTypeConstants.IDP_CATALOG_CONNECTOR),
  IDP_PERMISSIONS(ResourceTypeConstants.IDP_PERMISSIONS);

  ResourceType(String resourceType) {
    if (!this.name().equals(resourceType)) {
      throw new IllegalArgumentException(
          String.format("ResourceType enum: %s doesn't match constant: %s", this.name(), resourceType));
    }
  }
}
