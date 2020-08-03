package io.harness.eraro;

import static java.util.stream.Collectors.joining;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.EXPECTATION_FAILED;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;
import static javax.ws.rs.core.Response.Status.GATEWAY_TIMEOUT;
import static javax.ws.rs.core.Response.Status.MOVED_PERMANENTLY;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;
import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;

import com.google.common.base.Splitter;

import javax.ws.rs.core.Response.Status;

/**
 * The enum Error codes.
 */
public enum ErrorCode {
  DEFAULT_ERROR_CODE,

  INVALID_ARGUMENT,

  INVALID_EMAIL,

  DOMAIN_NOT_ALLOWED_TO_REGISTER,

  USER_ALREADY_REGISTERED(CONFLICT),

  USER_INVITATION_DOES_NOT_EXIST(UNAUTHORIZED, "User was not invited to access account or the invitation is obsolete"),

  USER_DOES_NOT_EXIST(UNAUTHORIZED),

  USER_INVITE_OPERATION_FAILED,

  USER_DISABLED(UNAUTHORIZED),

  ACCOUNT_DOES_NOT_EXIST(UNAUTHORIZED),

  INACTIVE_ACCOUNT(UNAUTHORIZED),

  ACCOUNT_MIGRATED(MOVED_PERMANENTLY),

  USER_DOMAIN_NOT_ALLOWED(UNAUTHORIZED),

  MAX_FAILED_ATTEMPT_COUNT_EXCEEDED(UNAUTHORIZED),

  RESOURCE_NOT_FOUND(NOT_FOUND),

  ROLE_DOES_NOT_EXIST(UNAUTHORIZED),

  EMAIL_NOT_VERIFIED(UNAUTHORIZED),

  EMAIL_VERIFICATION_TOKEN_NOT_FOUND(NOT_FOUND),

  INVALID_TOKEN(UNAUTHORIZED),

  INVALID_CAPTCHA_TOKEN(UNAUTHORIZED),

  NOT_ACCOUNT_MGR_NOR_HAS_ALL_APP_ACCESS,

  EXPIRED_TOKEN(UNAUTHORIZED),

  TOKEN_ALREADY_REFRESHED_ONCE(UNAUTHORIZED),

  ACCESS_DENIED(FORBIDDEN),

  INVALID_CREDENTIAL(UNAUTHORIZED),

  INVALID_KEY("Invalid key"),

  INVALID_KEYPATH,

  INVALID_VARIABLE,

  UNKNOWN_HOST("Invalid hostname"),

  UNREACHABLE_HOST("Unreachable hostname"),

  INVALID_PORT,

  SSH_SESSION_TIMEOUT,

  SOCKET_CONNECTION_ERROR("Connection error"),

  SOCKET_CONNECTION_TIMEOUT("Socket Connection timeout"),

  CONNECTION_TIMEOUT("Connection timeout"),

  SSH_CONNECTION_ERROR("Ssh Connection error"),

  USER_GROUP_ERROR("User Group Invalid"),

  INVALID_EXECUTION_ID,

  ERROR_IN_GETTING_CHANNEL_STREAMS,

  UNEXPECTED,

  UNKNOWN_ERROR,

  UNKNOWN_EXECUTOR_TYPE_ERROR,

  DUPLICATE_STATE_NAMES,

  TRANSITION_NOT_LINKED,

  TRANSITION_TO_INCORRECT_STATE,

  TRANSITION_TYPE_NULL,

  STATES_WITH_DUP_TRANSITIONS,

  BARRIERS_NOT_RUNNING_CONCURRENTLY,

  NON_FORK_STATES,

  NON_REPEAT_STATES,

  INITIAL_STATE_NOT_DEFINED,

  FILE_INTEGRITY_CHECK_FAILED,

  INVALID_URL,

  FILE_DOWNLOAD_FAILED,

  PLATFORM_SOFTWARE_DELETE_ERROR,

  INVALID_CSV_FILE,

  INVALID_REQUEST,

  INVALID_INFRA_STATE,

  PIPELINE_ALREADY_TRIGGERED,

  NON_EXISTING_PIPELINE,

  DUPLICATE_COMMAND_NAMES,

  INVALID_PIPELINE,

  COMMAND_DOES_NOT_EXIST,

  DUPLICATE_ARTIFACTSTREAM_NAMES,

  DUPLICATE_HOST_NAMES,

  STATE_NOT_FOR_TYPE,

  STATE_MACHINE_ISSUE,

  STATE_DISCONTINUE_FAILED,

  STATE_PAUSE_FAILED,

  PAUSE_ALL_ALREADY,

  RESUME_ALL_ALREADY,

  ROLLBACK_ALREADY,

  ABORT_ALL_ALREADY,

  RETRY_FAILED,

  UNKNOWN_ARTIFACT_TYPE,

  INIT_TIMEOUT,

  LICENSE_EXPIRED,

  NOT_LICENSED,

  REQUEST_TIMEOUT(GATEWAY_TIMEOUT, GATEWAY_TIMEOUT.getReasonPhrase()),

  WORKFLOW_ALREADY_TRIGGERED,

  JENKINS_ERROR,

  INVALID_ARTIFACT_SOURCE,

  INVALID_ARTIFACT_SERVER(BAD_REQUEST),

  INVALID_CLOUD_PROVIDER(BAD_REQUEST),

  UPDATE_NOT_ALLOWED,

  DELETE_NOT_ALLOWED,

  APPDYNAMICS_CONFIGURATION_ERROR,

  APM_CONFIGURATION_ERROR,

  SPLUNK_CONFIGURATION_ERROR,

  ELK_CONFIGURATION_ERROR,

  LOGZ_CONFIGURATION_ERROR,

  SUMO_CONFIGURATION_ERROR,

  INSTANA_CONFIGURATION_ERROR,

  APPDYNAMICS_ERROR,

  STACKDRIVER_ERROR,

  STACKDRIVER_CONFIGURATION_ERROR,

  NEWRELIC_CONFIGURATION_ERROR,

  NEWRELIC_ERROR,

  DYNA_TRACE_CONFIGURATION_ERROR,

  DYNA_TRACE_ERROR,

  CLOUDWATCH_ERROR,

  CLOUDWATCH_CONFIGURATION_ERROR,

  PROMETHEUS_CONFIGURATION_ERROR,

  DATA_DOG_CONFIGURATION_ERROR,

  SERVICE_GUARD_CONFIGURATION_ERROR,

  // Problem with encryption, most likely that the JCE Unlimited Strength jars aren't installed.
  ENCRYPTION_NOT_CONFIGURED,

  UNAVAILABLE_DELEGATES(SERVICE_UNAVAILABLE),

  WORKFLOW_EXECUTION_IN_PROGRESS,

  PIPELINE_EXECUTION_IN_PROGRESS,

  AWS_ACCESS_DENIED,

  AWS_CLUSTER_NOT_FOUND,

  AWS_SERVICE_NOT_FOUND,

  INVALID_YAML_PAYLOAD,

  UNRECOGNIZED_YAML_FIELDS,

  COULD_NOT_MAP_BEFORE_YAML,

  MISSING_BEFORE_YAML,

  MISSING_YAML,

  NON_EMPTY_DELETIONS,

  GENERAL_YAML_ERROR,

  GENERAL_YAML_INFO,

  YAML_GIT_SYNC_ERROR,

  GIT_CONNECTION_ERROR,

  GIT_ERROR,

  ARTIFACT_SERVER_ERROR,

  ENCRYPT_DECRYPT_ERROR,

  SECRET_MANAGEMENT_ERROR,

  KMS_OPERATION_ERROR,

  GCP_KMS_OPERATION_ERROR,

  VAULT_OPERATION_ERROR,

  AWS_SECRETS_MANAGER_OPERATION_ERROR,

  AZURE_KEY_VAULT_OPERATION_ERROR,

  CYBERARK_OPERATION_ERROR,

  UNSUPPORTED_OPERATION_EXCEPTION,

  FEATURE_UNAVAILABLE(EXPECTATION_FAILED),

  GENERAL_ERROR,

  BASELINE_CONFIGURATION_ERROR,

  SAML_IDP_CONFIGURATION_NOT_AVAILABLE,

  INVALID_AUTHENTICATION_MECHANISM,

  INVALID_SAML_CONFIGURATION,

  INVALID_OAUTH_CONFIGURATION,

  INVALID_LDAP_CONFIGURATION,

  USER_GROUP_SYNC_FAILURE,

  USER_GROUP_ALREADY_EXIST,

  INVALID_TWO_FACTOR_AUTHENTICATION_CONFIGURATION,

  EXPLANATION,

  HINT,

  NOT_WHITELISTED_IP,

  INVALID_TOTP_TOKEN(UNAUTHORIZED),

  EMAIL_FAILED,

  SSL_HANDSHAKE_FAILED,

  NO_APPS_ASSIGNED("No apps are assigned to the user or no apps exist in the account"),

  INVALID_INFRA_CONFIGURATION,

  TEMPLATES_LINKED,

  USER_HAS_NO_PERMISSIONS("The user doesn't have update access to any Environment"),

  USER_NOT_AUTHORIZED("User not authorized"),

  USER_ALREADY_PRESENT,

  INVALID_USAGE_RESTRICTION("Invalid usage restrictions"),

  USAGE_RESTRICTION_ERROR,

  STATE_EXECUTION_INSTANCE_NOT_FOUND("State Execution Instance not found"),

  DELEGATE_TASK_RETRY,

  KUBERNETES_YAML_ERROR,

  SAVE_FILE_INTO_GCP_STORAGE_FAILED,

  READ_FILE_FROM_GCP_STORAGE_FAILED,

  USAGE_LIMITS_EXCEEDED(Status.FORBIDDEN, "Usage Limit Exceeded"),

  EVENT_PUBLISH_FAILED("Event published failed"),

  JIRA_ERROR,

  EXPRESSION_EVALUATION_FAILED,

  KUBERNETES_VALUES_ERROR,

  KUBERNETES_CLUSTER_ERROR,

  INCORRECT_SIGN_IN_MECHANISM("Using incorrect sign-in mechanism. Please contact Account Admin."),

  OAUTH_LOGIN_FAILED,

  INVALID_TERRAFORM_TARGETS_REQUEST(Status.OK),

  FILE_READ_FAILED,

  FILE_SIZE_EXCEEDS_LIMIT,

  CLUSTER_NOT_FOUND,

  MARKETPLACE_TOKEN_NOT_FOUND,

  INVALID_MARKETPLACE_TOKEN,

  INVALID_TICKETING_SERVER,

  SERVICENOW_ERROR,

  PASSWORD_EXPIRED("Password has expired. Please reset it."),

  USER_LOCKED,

  PASSWORD_STRENGTH_CHECK_FAILED,

  INVALID_ACCOUNT_PERMISSION("Invalid account permission"),

  PAGERDUTY_ERROR,

  HEALTH_ERROR,

  SAML_TEST_SUCCESS_MECHANISM_NOT_ENABLED,

  DOMAIN_WHITELIST_FILTER_CHECK_FAILED(UNAUTHORIZED),

  INVALID_DASHBOARD_UPDATE_REQUEST("Invalid Dashboard update request"),

  DUPLICATE_FIELD,

  INVALID_AZURE_VAULT_CONFIGURATION,

  USER_NOT_AUTHORIZED_DUE_TO_USAGE_RESTRICTIONS("User not authorized due to usage restrictions"),

  INVALID_ROLLBACK,

  SUMO_DATA_COLLECTION_ERROR,

  DEPLOYMENT_GOVERNANCE_ERROR,

  BATCH_PROCESSING_ERROR,

  GRAPHQL_ERROR,

  FILE_CREATE_ERROR,

  ILLEGAL_STATE,

  GIT_DIFF_COMMIT_NOT_IN_ORDER,

  FAILED_TO_ACQUIRE_PERSISTENT_LOCK,

  POD_NOT_FOUND_ERROR,

  COMMAND_EXECUTION_ERROR,

  REGISTRY_EXCEPTION,

  ENGINE_INTERRUPT_PROCESSING_EXCEPTION,

  ENGINE_IO_EXCEPTION,

  ENGINE_OUTCOME_EXCEPTION,

  ENGINE_SWEEPING_OUTPUT_EXCEPTION,

  CACHE_NOT_FOUND_EXCEPTION,

  ENGINE_ENTITY_UPDATE_EXCEPTION,

  SHELL_EXECUTION_EXCEPTION,

  TEMPLATE_NOT_FOUND,

  GIT_UNSEEN_REMOTE_HEAD_COMMIT;

  private Status status = BAD_REQUEST;
  private String description;

  ErrorCode() {}

  ErrorCode(Status status) {
    this.status = status;
  }

  ErrorCode(String description) {
    this.description = description;
  }

  ErrorCode(Status status, String description) {
    this.status = status;
    this.description = description;
  }

  public Status getStatus() {
    return status;
  }

  public String getDescription() {
    return description != null ? description : upperUnderscoreToSpaceSeparatedCamelCase(name());
  }

  public static String upperUnderscoreToSpaceSeparatedCamelCase(String original) {
    return Splitter.on("_").splitToList(original).stream().map(ErrorCode::capitalize).collect(joining(" "));
  }

  public static String capitalize(final String line) {
    return line.length() > 1 ? line.charAt(0) + line.substring(1).toLowerCase() : line;
  }
}
