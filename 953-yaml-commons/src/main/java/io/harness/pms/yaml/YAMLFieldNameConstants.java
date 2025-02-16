/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@UtilityClass
@OwnedBy(CDC)
public class YAMLFieldNameConstants {
  public final String EXECUTION = "execution";
  public final String PIPELINE = "pipeline";
  public final String TRIGGER = "trigger";
  public final String POLICY_OUTPUT = "policyOutput";
  public final String CI_CODE_BASE = "codebase";
  public final String PROPERTIES = "properties";
  public final String CI = "ci";
  public final String SECURITY = "security";
  public final String PARALLEL = "parallel";
  public final String SPEC = "spec";
  public final String STAGE = "stage";
  public final String STAGES = "stages";
  public final String STRATEGY = "strategy";
  public final String CLONE = "clone";
  public final String CLONE_CODEBASE = "cloneCodebase";
  public final String DISABLED = "disabled";
  public final String WHEN = "when";

  public final String STEP = "step";
  public final String STEPS = "steps";
  public final String STEP_GROUP = "stepGroup";
  public final String ROLLBACK_STEPS = "rollbackSteps";
  public final String FAILURE_STRATEGIES = "failureStrategies";
  public final String NAME = "name";
  public final String IDENTIFIER = "identifier";
  public final String ID = "id";
  public final String DESCRIPTION = "description";
  public final String TAGS = "tags";
  public final String VARIABLES = "variables";
  public final String SERVICE_VARIABLES = "serviceVariables";
  public final String TYPE = "type";
  public final String KEY = "key";
  public final String VALUE = "value";
  public final String REQUIRED = "required";
  public final String UUID = YamlNode.UUID_FIELD_NAME;
  public final String TIMEOUT = "timeout";
  public final String OUTPUT_VARIABLES = "outputVariables";
  public final String HEADERS = "headers";
  public final String OUTPUT = "output";
  public final String INPUT = "input";
  public final String INPUTS = "inputs";
  public final String ENVIRONMENT = "environment";
  public final String PROVISIONER = "provisioner";
  public final String CONNECTOR_REF = "connectorRef";
  public final String CREDENTIALS_REF = "credentialsRef";
  public final String CONNECTOR_REFS = "connectorRefs";
  public final String FILES = "files";
  public final String SECRET_FILES = "secretFiles";
  public final String SECRET = "secret";
  public final String CODEBASE_CONNECTOR_REF = "ciCodebase.connectorRef";
  public final String USE_ROLLBACK_STRATEGY = "useRollbackStrategy";
  public final String USE_PIPELINE_ROLLBACK_STRATEGY = "usePipelineRollbackStrategy";
  public final String PIPELINE_ROLLBACK_FAILURE_INFO = "pipelineRollbackFailureInfo";
  public final String FAILED_CHILDREN_OUTPUT = "failedChildrenOutput";
  public final String COMMAND = "Command";

  public final String STORE = "store";
  public final String PIPELINE_INFRASTRUCTURE = "infrastructure";
  public final String COMMAND_TYPE = "commandType";
  public final String STOP_STEPS_SEQUENCE = "stopStepsSequence";
  public final String DELEGATE_SELECTORS = "delegateSelectors";

  public final String CONFIGURATION = "configuration";
  public final String TEMPLATE = "template";
  public final String TEMPLATE_INPUTS = "templateInputs";
  public final String TEMPLATE_VERSION = "versionLabel";

  public final String BASE_IMAGE_CONNECTOR_REFS = "baseImageConnectorRefs";
  public final String HARNESS_IMAGE_CONNECTOR_REF = "harnessImageConnectorRef";

  public final String CHILD_NODE_OF_SPEC = "childNodeOfSpec";
  public final String GITOPS_ENABLED = "gitOpsEnabled";
  public final String SKIP_INSTANCES = "skipInstances";

  public final String ORG_IDENTIFIER = "orgIdentifier";
  public final String PROJECT_IDENTIFIER = "projectIdentifier";
  public final String PIPELINE_IDENTIFIER = "pipelineIdentifier";
  public final String USE_FROM_STAGE = "useFromStage";
  public final String SERVICE = "service";

  public final String INPUT_SET_REFERENCES = "inputSetReferences";
  public final String INPUT_SETS = "input_sets";
  public final String REPEAT = "repeat";
  public final String SERVICE_REF = "serviceRef";
  public final String ENVIRONMENT_REF = "environmentRef";
  public final String GROUP = "group";
  public static final String OUTPUTS = "outputs";
  public static final String EXPORTS = "exports";

  public final String OPTIONS = "options";
  public final String REPOSITORY = "repository";
  public final String DEFAULT = "default";
  public final String REGISTRY = "registry";

  public final String VALUES_PATHS = "valuesPaths";
  public final String PARAMS_PATHS = "paramsPaths";
  public final String PATCHES_PATHS = "patchesPaths";

  public final String SERVICE_CONFIG = "serviceConfig";
  // todo(abhinav): make this step group once battle tested
  public final String STEP_GROUP_V2 = "stepGroup";
  public final String STEP_GROUP_CHILD_NODE_ID = "stepsChildNodeId";

  public final String COMBINED_ROLLBACK_STATUS = "combinedRollbackStatus";
  public final String RETRY_STEP_GROUP = "retryStepGroup";
  public final String CUSTOM = "Custom";
  public final String CUSTOM_V1 = "custom";
  public final String APPROVAL_V1 = "approval";
  public final String REF = "ref";
  public final String DOLLAR_REF = "$ref";
  public final String VERSION = "version";
  public final String KIND = "kind";
  public final String VALIDATOR = "validator";
  public final String ALLOWED = "allowed";
  public final String REGEX = "regex";
  public final String DESC = "desc";

  public final String DEPLOYMENT_STAGE_V1 = "deployment";
  public final String ROLLBACK_STEPS_V1 = "rollback_steps";
  public final String DELEGATES = "delegates";
}
