package software.wings.yaml.handler.workflow;

public class WorkflowYamlConstant {
  // START STRING CONSTANTS FOR BASIC WORKFLOW YAML TEST
  public static final String BASIC_VALID_YAML_CONTENT = "harnessApiVersion: '1.0'\n"
      + "type: BASIC\n"
      + "envName: ENV_NAME\n"
      + "failureStrategies:\n"
      + "- executionScope: WORKFLOW\n"
      + "  failureTypes:\n"
      + "  - APPLICATION_ERROR\n"
      + "  repairActionCode: ROLLBACK_WORKFLOW\n"
      + "  retryCount: 0\n"
      + "notificationRules:\n"
      + "- conditions:\n"
      + "  - FAILED\n"
      + "  executionScope: WORKFLOW\n"
      + "  notificationGroupAsExpression: false\n"
      + "  notificationGroups:\n"
      + "  - Account Administrator\n"
      + "  userGroupAsExpression: false\n"
      + "phases:\n"
      + "- type: KUBERNETES\n"
      + "  computeProviderName: exploration\n"
      + "  daemonSet: false\n"
      + "  infraMappingName: direct_Kubernetes\n"
      + "  name: Phase 1\n"
      + "  phaseSteps:\n"
      + "  - type: CONTAINER_SETUP\n"
      + "    name: Setup Container\n"
      + "    steps:\n"
      + "    - type: KUBERNETES_SETUP\n"
      + "      name: Kubernetes Service Setup\n"
      + "    stepsInParallel: false\n"
      + "  - type: CONTAINER_DEPLOY\n"
      + "    name: Deploy Containers\n"
      + "    steps:\n"
      + "    - type: KUBERNETES_DEPLOY\n"
      + "      name: Upgrade Containers\n"
      + "      properties:\n"
      + "        commandName: Resize Replication Controller\n"
      + "        instanceCount: 1\n"
      + "        instanceUnitType: COUNT\n"
      + "    stepsInParallel: false\n"
      + "  - type: VERIFY_SERVICE\n"
      + "    name: Verify Service\n"
      + "    stepsInParallel: false\n"
      + "  - type: WRAP_UP\n"
      + "    name: Wrap Up\n"
      + "    stepsInParallel: false\n"
      + "  provisionNodes: false\n"
      + "  serviceName: SERVICE_NAME\n"
      + "  statefulSet: false\n"
      + "rollbackPhases:\n"
      + "- type: KUBERNETES\n"
      + "  computeProviderName: exploration\n"
      + "  daemonSet: false\n"
      + "  infraMappingName: direct_Kubernetes\n"
      + "  name: Rollback Phase 1\n"
      + "  phaseNameForRollback: Phase 1\n"
      + "  phaseSteps:\n"
      + "  - type: CONTAINER_DEPLOY\n"
      + "    name: Deploy Containers\n"
      + "    phaseStepNameForRollback: Deploy Containers\n"
      + "    statusForRollback: SUCCESS\n"
      + "    steps:\n"
      + "    - type: KUBERNETES_DEPLOY_ROLLBACK\n"
      + "      name: Rollback Containers\n"
      + "    stepsInParallel: false\n"
      + "  - type: WRAP_UP\n"
      + "    name: Wrap Up\n"
      + "    stepsInParallel: false\n"
      + "  provisionNodes: false\n"
      + "  serviceName: SERVICE_NAME\n"
      + "  statefulSet: false\n"
      + "templatized: false";

  public static final String BASIC_VALID_YAML_CONTENT_WITH_MULTILINE_USER_INPUT = "harnessApiVersion: '1.0'\n"
      + "type: BASIC\n"
      + "envName: ENV_NAME\n"
      + "failureStrategies:\n"
      + "- executionScope: WORKFLOW\n"
      + "  failureTypes:\n"
      + "  - APPLICATION_ERROR\n"
      + "  repairActionCode: ROLLBACK_WORKFLOW\n"
      + "  retryCount: 0\n"
      + "notificationRules:\n"
      + "- conditions:\n"
      + "  - FAILED\n"
      + "  executionScope: WORKFLOW\n"
      + "  notificationGroupAsExpression: false\n"
      + "  notificationGroups:\n"
      + "  - Account Administrator\n"
      + "  userGroupAsExpression: false\n"
      + "phases:\n"
      + "- type: KUBERNETES\n"
      + "  computeProviderName: Aws test\n"
      + "  daemonSet: false\n"
      + "  infraMappingName: direct_Kubernetes\n"
      + "  name: Phase 1\n"
      + "  phaseSteps:\n"
      + "  - type: INFRASTRUCTURE_NODE\n"
      + "    name: Prepare Infra\n"
      + "    steps:\n"
      + "    - type: AWS_NODE_SELECT\n"
      + "      name: Select Nodes\n"
      + "      properties:\n"
      + "        excludeSelectedHostsFromFuturePhases: true\n"
      + "        instanceCount: 1\n"
      + "        specificHosts: false\n"
      + "    stepsInParallel: false\n"
      + "  - type: DISABLE_SERVICE\n"
      + "    name: Disable Service\n"
      + "    stepsInParallel: false\n"
      + "  - type: DEPLOY_SERVICE\n"
      + "    name: Deploy Service\n"
      + "    steps:\n"
      + "    - type: COMMAND\n"
      + "      name: Install\n"
      + "      properties:\n"
      + "        commandName: Install\n"
      + "    - type: HTTP\n"
      + "      name: HTTP\n"
      + "      properties:\n"
      + "        body: |-\n"
      + "          {\n"
      + "            \"deployment\": {\n"
      + "            \"revision\": \"${artifact.buildNo}\",\n"
      + "              \"description\": \"Harness Deployment via workflow ${workflow.name}\",\n"
      + "              \"user\": \"${workflow.name}\"\n"
      + "            }\n"
      + "            }\n"
      + "        method: POST\n"
      + "        url: rama:8080\n"
      + "    stepsInParallel: false\n"
      + "  - type: ENABLE_SERVICE\n"
      + "    name: Enable Service\n"
      + "    stepsInParallel: false\n"
      + "  - type: VERIFY_SERVICE\n"
      + "    name: Verify Service\n"
      + "    stepsInParallel: false\n"
      + "  - type: WRAP_UP\n"
      + "    name: Wrap Up\n"
      + "    stepsInParallel: false\n"
      + "  provisionNodes: false\n"
      + "  serviceName: SERVICE_NAME\n"
      + "  statefulSet: false\n"
      + "preDeploymentSteps:\n"
      + "- type: ARTIFACT_CHECK\n"
      + "  name: Artifact Check\n"
      + "rollbackPhases:\n"
      + "- type: KUBERNETES\n"
      + "  computeProviderName: Aws test\n"
      + "  daemonSet: false\n"
      + "  infraMappingName: direct_Kubernetes\n"
      + "  name: Rollback Phase 1\n"
      + "  phaseNameForRollback: Phase 1\n"
      + "  phaseSteps:\n"
      + "  - type: DISABLE_SERVICE\n"
      + "    name: Disable Service\n"
      + "    phaseStepNameForRollback: Enable Service\n"
      + "    statusForRollback: SUCCESS\n"
      + "    stepsInParallel: false\n"
      + "  - type: STOP_SERVICE\n"
      + "    name: Stop Service\n"
      + "    phaseStepNameForRollback: Deploy Service\n"
      + "    statusForRollback: SUCCESS\n"
      + "    stepsInParallel: false\n"
      + "  - type: DEPLOY_SERVICE\n"
      + "    name: Deploy Service\n"
      + "    phaseStepNameForRollback: Deploy Service\n"
      + "    statusForRollback: SUCCESS\n"
      + "    steps:\n"
      + "    - type: COMMAND\n"
      + "      name: Install\n"
      + "      properties:\n"
      + "        commandName: Install\n"
      + "    stepsInParallel: false\n"
      + "  - type: ENABLE_SERVICE\n"
      + "    name: Enable Service\n"
      + "    phaseStepNameForRollback: Disable Service\n"
      + "    statusForRollback: SUCCESS\n"
      + "    stepsInParallel: false\n"
      + "  - type: VERIFY_SERVICE\n"
      + "    name: Verify Service\n"
      + "    phaseStepNameForRollback: Deploy Service\n"
      + "    statusForRollback: SUCCESS\n"
      + "    stepsInParallel: false\n"
      + "  - type: WRAP_UP\n"
      + "    name: Wrap Up\n"
      + "    stepsInParallel: false\n"
      + "  provisionNodes: false\n"
      + "  serviceName: SERVICE_NAME\n"
      + "  statefulSet: false\n"
      + "templatized: false";

  public static final String BASIC_VALID_YAML_CONTENT_TEMPLATIZED = "harnessApiVersion: '1.0'\n"
      + "type: BASIC\n"
      + "envName: ENV_NAME\n"
      + "failureStrategies:\n"
      + "- executionScope: WORKFLOW\n"
      + "  failureTypes:\n"
      + "  - APPLICATION_ERROR\n"
      + "  repairActionCode: ROLLBACK_WORKFLOW\n"
      + "  retryCount: 0\n"
      + "notificationRules:\n"
      + "- conditions:\n"
      + "  - FAILED\n"
      + "  executionScope: WORKFLOW\n"
      + "  notificationGroupAsExpression: false\n"
      + "  notificationGroups:\n"
      + "  - Account Administrator\n"
      + "  userGroupAsExpression: false\n"
      + "phases:\n"
      + "- type: KUBERNETES\n"
      + "  computeProviderName: Aws non-prod\n"
      + "  daemonSet: false\n"
      + "  name: Phase 1\n"
      + "  phaseSteps:\n"
      + "  - type: CONTAINER_SETUP\n"
      + "    name: Setup Container\n"
      + "    steps:\n"
      + "    - type: ECS_SERVICE_SETUP\n"
      + "      name: ECS Service Setup\n"
      + "    stepsInParallel: false\n"
      + "  - type: CONTAINER_DEPLOY\n"
      + "    name: Deploy Containers\n"
      + "    steps:\n"
      + "    - type: ECS_SERVICE_DEPLOY\n"
      + "      name: Upgrade Containers\n"
      + "    stepsInParallel: false\n"
      + "  - type: VERIFY_SERVICE\n"
      + "    name: Verify Service\n"
      + "    stepsInParallel: false\n"
      + "  - type: WRAP_UP\n"
      + "    name: Wrap Up\n"
      + "    stepsInParallel: false\n"
      + "  provisionNodes: false\n"
      + "  statefulSet: false\n"
      + "  templateExpressions:\n"
      + "  - expression: ${Service}\n"
      + "    fieldName: serviceId\n"
      + "    metadata:\n"
      + "    - name: artifactType\n"
      + "      value: DOCKER\n"
      + "    - name: relatedField\n"
      + "      value: ${ServiceInfra_ECS}\n"
      + "    - name: entityType\n"
      + "      value: SERVICE\n"
      + "  - expression: ${ServiceInfra_ECS}\n"
      + "    fieldName: infraMappingId\n"
      + "    metadata:\n"
      + "    - name: artifactType\n"
      + "      value: DOCKER\n"
      + "    - name: relatedField\n"
      + "    - name: entityType\n"
      + "      value: INFRASTRUCTURE_MAPPING\n"
      + "rollbackPhases:\n"
      + "- type: KUBERNETES\n"
      + "  computeProviderName: Aws non-prod\n"
      + "  daemonSet: false\n"
      + "  name: Rollback Phase 1\n"
      + "  phaseNameForRollback: Phase 1\n"
      + "  phaseSteps:\n"
      + "  - type: CONTAINER_DEPLOY\n"
      + "    name: Deploy Containers\n"
      + "    phaseStepNameForRollback: Deploy Containers\n"
      + "    statusForRollback: SUCCESS\n"
      + "    steps:\n"
      + "    - type: ECS_SERVICE_ROLLBACK\n"
      + "      name: Rollback Containers\n"
      + "      properties:\n"
      + "        rollback: true\n"
      + "    stepsInParallel: false\n"
      + "  - type: VERIFY_SERVICE\n"
      + "    name: Verify Service\n"
      + "    phaseStepNameForRollback: Deploy Containers\n"
      + "    statusForRollback: SUCCESS\n"
      + "    stepsInParallel: false\n"
      + "  - type: WRAP_UP\n"
      + "    name: Wrap Up\n"
      + "    stepsInParallel: false\n"
      + "  provisionNodes: false\n"
      + "  statefulSet: false\n"
      + "  templateExpressions:\n"
      + "  - expression: ${Service}\n"
      + "    fieldName: serviceId\n"
      + "    metadata:\n"
      + "    - name: artifactType\n"
      + "      value: DOCKER\n"
      + "    - name: relatedField\n"
      + "      value: ${ServiceInfra_ECS}\n"
      + "    - name: entityType\n"
      + "      value: SERVICE\n"
      + "  - expression: ${ServiceInfra_ECS}\n"
      + "    fieldName: infraMappingId\n"
      + "    metadata:\n"
      + "    - name: artifactType\n"
      + "      value: DOCKER\n"
      + "    - name: relatedField\n"
      + "    - name: entityType\n"
      + "      value: INFRASTRUCTURE_MAPPING\n"
      + "templateExpressions:\n"
      + "- expression: ${Service}\n"
      + "  fieldName: serviceId\n"
      + "  metadata:\n"
      + "  - name: artifactType\n"
      + "    value: DOCKER\n"
      + "  - name: relatedField\n"
      + "    value: ${ServiceInfra_ECS}\n"
      + "  - name: entityType\n"
      + "    value: SERVICE\n"
      + "- expression: ${ServiceInfra_ECS}\n"
      + "  fieldName: infraMappingId\n"
      + "  metadata:\n"
      + "  - name: artifactType\n"
      + "    value: DOCKER\n"
      + "  - name: relatedField\n"
      + "  - name: entityType\n"
      + "    value: INFRASTRUCTURE_MAPPING\n"
      + "templatized: true\n"
      + "userVariables:\n"
      + "- type: ENTITY\n"
      + "  description: Variable for Service entity\n"
      + "  fixed: false\n"
      + "  mandatory: true\n"
      + "  name: Service\n"
      + "- type: ENTITY\n"
      + "  description: Variable for Service Infra-structure entity\n"
      + "  fixed: false\n"
      + "  mandatory: true\n"
      + "  name: ServiceInfra_ECS";

  public static final String BASIC_VALID_YAML_FILE_PATH_PREFIX = "Setup/Applications/APP_NAME/Workflows/";
  public static final String BASIC_INVALID_YAML_CONTENT = "envName: env1\nphaseInvalid: phase1\ntype: BASIC";
  public static final String BASIC_INVALID_YAML_FILE_PATH = "Setup/Applications/APP_NAME/WorkflowsInvalid/basic.yaml";

  // END STRING CONSTANTS FOR BASIC WORKFLOW YAML TEST

  // START STRING CONSTANTS FOR BUILD WORKFLOW YAML TEST
  public static final String BUILD_VALID_YAML_CONTENT = "harnessApiVersion: '1.0'\n"
      + "type: BUILD\n"
      + "envName: ENV_NAME\n"
      + "notificationRules:\n"
      + "- conditions:\n"
      + "  - FAILED\n"
      + "  executionScope: WORKFLOW\n"
      + "  notificationGroupAsExpression: false\n"
      + "  notificationGroups:\n"
      + "  - Account Administrator\n"
      + "  userGroupAsExpression: false\n"
      + "phases:\n"
      + "- type: KUBERNETES\n"
      + "  daemonSet: false\n"
      + "  infraMappingName: direct_Kubernetes\n"
      + "  name: Phase 1\n"
      + "  phaseSteps:\n"
      + "  - type: PREPARE_STEPS\n"
      + "    name: Prepare Steps\n"
      + "    stepsInParallel: false\n"
      + "  - type: COLLECT_ARTIFACT\n"
      + "    name: Collect Artifact\n"
      + "    steps:\n"
      + "    - type: ARTIFACT_COLLECTION\n"
      + "      name: Artifact Collection\n"
      + "      properties:\n"
      + "        artifactStreamName: gcr.io_exploration-161417_todolist\n"
      + "        buildNo: latest\n"
      + "        serviceName: SERVICE_NAME\n"
      + "    stepsInParallel: false\n"
      + "  - type: WRAP_UP\n"
      + "    name: Wrap Up\n"
      + "    stepsInParallel: false\n"
      + "  provisionNodes: false\n"
      + "  serviceName: SERVICE_NAME\n"
      + "  statefulSet: false\n"
      + "templatized: false";

  public static final String BUILD_VALID_YAML_FILE_PATH = "Setup/Applications/APP_NAME/Workflows/build.yaml";
  public static final String BUILD_INVALID_YAML_CONTENT = "harnessApiVersion: '1.0'\n"
      + "type: BUILD\n"
      + "envName: ENV_NAME\n"
      + "notificationRules:\n"
      + "- conditions:\n"
      + "  - FAILED\n"
      + "  executionScope: WORKFLOW\n"
      + "  notificationGroupAsExpression: false\n"
      + "  notificationGroups:\n"
      + "  - Account Administrator\n"
      + "phases:\n"
      + "- type: KUBERNETES\n"
      + "  daemonSet: false\n"
      + "  infraMappingName: direct_Kubernetes\n"
      + "  name: Phase 1\n"
      + "  phaseSteps:\n"
      + "  - type: PREPARE_STEPS\n"
      + "    name: Prepare Steps\n"
      + "    stepsInParallel: false\n"
      + "  - type: COLLECT_ARTIFACT\n"
      + "    name: Collect Artifact\n"
      + "    steps:\n"
      + "    - type: ARTIFACT_COLLECTION\n"
      + "      name: Artifact Collection\n"
      + "    stepsInParallel: false\n"
      + "  - type: WRAP_UP\n"
      + "    name: Wrap Up\n"
      + "    stepsInParallel: false\n"
      + "  provisionNodes: false\n"
      + "  serviceName: SERVICE_NAME\n"
      + "templatized: false";

  public static final String BUILD_INVALID_YAML_FILE_PATH = "Setup/Applications/APP_NAME/WorkflowsInvalid/build.yaml";
  // END STRING CONSTANTS FOR BUILD WORKFLOW YAML TEST

  // START STRING CONSTANTS FOR CANARY WORKFLOW YAML TEST
  public static final String CANARY_VALID_YAML_CONTENT = "harnessApiVersion: '1.0'\n"
      + "type: CANARY\n"
      + "envName: ENV_NAME\n"
      + "failureStrategies:\n"
      + "- executionScope: WORKFLOW\n"
      + "  failureTypes:\n"
      + "  - APPLICATION_ERROR\n"
      + "  repairActionCode: ROLLBACK_WORKFLOW\n"
      + "  retryCount: 0\n"
      + "notificationRules:\n"
      + "- conditions:\n"
      + "  - FAILED\n"
      + "  executionScope: WORKFLOW\n"
      + "  notificationGroupAsExpression: false\n"
      + "  notificationGroups:\n"
      + "  - Account Administrator\n"
      + "  userGroupAsExpression: false\n"
      + "phases:\n"
      + "- type: KUBERNETES\n"
      + "  computeProviderName: COMPUTE_PROVIDER_ID\n"
      + "  daemonSet: false\n"
      + "  infraMappingName: direct_Kubernetes\n"
      + "  name: Phase 1\n"
      + "  phaseSteps:\n"
      + "  - type: CONTAINER_SETUP\n"
      + "    name: Setup Container\n"
      + "    steps:\n"
      + "    - type: KUBERNETES_SETUP\n"
      + "      name: Kubernetes Service Setup\n"
      + "    stepsInParallel: false\n"
      + "  - type: CONTAINER_DEPLOY\n"
      + "    name: Deploy Containers\n"
      + "    steps:\n"
      + "    - type: KUBERNETES_DEPLOY\n"
      + "      name: Upgrade Containers\n"
      + "      properties:\n"
      + "        commandName: Resize Replication Controller\n"
      + "        instanceCount: 1\n"
      + "        instanceUnitType: COUNT\n"
      + "    stepsInParallel: false\n"
      + "  - type: VERIFY_SERVICE\n"
      + "    name: Verify Service\n"
      + "    stepsInParallel: false\n"
      + "  - type: WRAP_UP\n"
      + "    name: Wrap Up\n"
      + "    stepsInParallel: false\n"
      + "  provisionNodes: false\n"
      + "  serviceName: SERVICE_NAME\n"
      + "  statefulSet: false\n"
      + "- type: KUBERNETES\n"
      + "  computeProviderName: COMPUTE_PROVIDER_ID\n"
      + "  daemonSet: false\n"
      + "  infraMappingName: direct_Kubernetes\n"
      + "  name: Phase 2\n"
      + "  phaseSteps:\n"
      + "  - type: CONTAINER_SETUP\n"
      + "    name: Setup Container\n"
      + "    steps:\n"
      + "    - type: KUBERNETES_SETUP\n"
      + "      name: Kubernetes Service Setup\n"
      + "    stepsInParallel: false\n"
      + "  - type: CONTAINER_DEPLOY\n"
      + "    name: Deploy Containers\n"
      + "    steps:\n"
      + "    - type: KUBERNETES_DEPLOY\n"
      + "      name: Upgrade Containers\n"
      + "      properties:\n"
      + "        commandName: Resize Replication Controller\n"
      + "        instanceCount: 1\n"
      + "        instanceUnitType: COUNT\n"
      + "    stepsInParallel: false\n"
      + "  - type: VERIFY_SERVICE\n"
      + "    name: Verify Service\n"
      + "    stepsInParallel: false\n"
      + "  - type: WRAP_UP\n"
      + "    name: Wrap Up\n"
      + "    stepsInParallel: false\n"
      + "  provisionNodes: false\n"
      + "  serviceName: SERVICE_NAME\n"
      + "  statefulSet: false\n"
      + "rollbackPhases:\n"
      + "- type: KUBERNETES\n"
      + "  computeProviderName: COMPUTE_PROVIDER_ID\n"
      + "  daemonSet: false\n"
      + "  infraMappingName: direct_Kubernetes\n"
      + "  name: Rollback Phase 1\n"
      + "  phaseNameForRollback: Phase 1\n"
      + "  phaseSteps:\n"
      + "  - type: CONTAINER_DEPLOY\n"
      + "    name: Deploy Containers\n"
      + "    phaseStepNameForRollback: Deploy Containers\n"
      + "    statusForRollback: SUCCESS\n"
      + "    steps:\n"
      + "    - type: KUBERNETES_DEPLOY_ROLLBACK\n"
      + "      name: Rollback Containers\n"
      + "    stepsInParallel: false\n"
      + "  - type: WRAP_UP\n"
      + "    name: Wrap Up\n"
      + "    stepsInParallel: false\n"
      + "  provisionNodes: false\n"
      + "  serviceName: SERVICE_NAME\n"
      + "  statefulSet: false\n"
      + "- type: KUBERNETES\n"
      + "  computeProviderName: COMPUTE_PROVIDER_ID\n"
      + "  daemonSet: false\n"
      + "  infraMappingName: direct_Kubernetes\n"
      + "  name: Rollback Phase 2\n"
      + "  phaseNameForRollback: Phase 2\n"
      + "  phaseSteps:\n"
      + "  - type: CONTAINER_DEPLOY\n"
      + "    name: Deploy Containers\n"
      + "    phaseStepNameForRollback: Deploy Containers\n"
      + "    statusForRollback: SUCCESS\n"
      + "    steps:\n"
      + "    - type: KUBERNETES_DEPLOY_ROLLBACK\n"
      + "      name: Rollback Containers\n"
      + "    stepsInParallel: false\n"
      + "  - type: WRAP_UP\n"
      + "    name: Wrap Up\n"
      + "    stepsInParallel: false\n"
      + "  provisionNodes: false\n"
      + "  serviceName: SERVICE_NAME\n"
      + "  statefulSet: false\n"
      + "templatized: false";

  public static final String CANARY_VALID_YAML_FILE_PATH = "Setup/Applications/APP_NAME/Workflows/canary.yaml";
  public static final String CANARY_INVALID_YAML_CONTENT = "envName: env1\nphaseInvalid: phase1\ntype: CANARY";
  public static final String CANARY_INVALID_YAML_FILE_PATH = "Setup/Applications/APP_NAME/WorkflowsInvalid/canary.yaml";
  // END STRING CONSTANTS FOR CANARY WORKFLOW YAML TEST

  // START STRING CONSTANTS FOR MULTI_SERVICE WORKFLOW YAML TEST
  public static final String MULTI_SERVICE_VALID_YAML_CONTENT = "harnessApiVersion: '1.0'\n"
      + "type: MULTI_SERVICE\n"
      + "envName: ENV_NAME\n"
      + "failureStrategies:\n"
      + "- executionScope: WORKFLOW\n"
      + "  failureTypes:\n"
      + "  - APPLICATION_ERROR\n"
      + "  repairActionCode: ROLLBACK_WORKFLOW\n"
      + "  retryCount: 0\n"
      + "notificationRules:\n"
      + "- conditions:\n"
      + "  - FAILED\n"
      + "  executionScope: WORKFLOW\n"
      + "  notificationGroupAsExpression: false\n"
      + "  notificationGroups:\n"
      + "  - Account Administrator\n"
      + "  userGroupAsExpression: false\n"
      + "phases:\n"
      + "- type: KUBERNETES\n"
      + "  computeProviderName: COMPUTE_PROVIDER_ID\n"
      + "  daemonSet: false\n"
      + "  infraMappingName: direct_Kubernetes\n"
      + "  name: Phase 1\n"
      + "  phaseSteps:\n"
      + "  - type: CONTAINER_SETUP\n"
      + "    name: Setup Container\n"
      + "    steps:\n"
      + "    - type: KUBERNETES_SETUP\n"
      + "      name: Kubernetes Service Setup\n"
      + "    stepsInParallel: false\n"
      + "  - type: CONTAINER_DEPLOY\n"
      + "    name: Deploy Containers\n"
      + "    steps:\n"
      + "    - type: KUBERNETES_DEPLOY\n"
      + "      name: Upgrade Containers\n"
      + "      properties:\n"
      + "        commandName: Resize Replication Controller\n"
      + "        instanceCount: 1\n"
      + "        instanceUnitType: COUNT\n"
      + "    stepsInParallel: false\n"
      + "  - type: VERIFY_SERVICE\n"
      + "    name: Verify Service\n"
      + "    stepsInParallel: false\n"
      + "  - type: WRAP_UP\n"
      + "    name: Wrap Up\n"
      + "    stepsInParallel: false\n"
      + "  provisionNodes: false\n"
      + "  serviceName: SERVICE_NAME\n"
      + "  statefulSet: false\n"
      + "- type: KUBERNETES\n"
      + "  computeProviderName: COMPUTE_PROVIDER_ID\n"
      + "  daemonSet: false\n"
      + "  infraMappingName: direct_Kubernetes\n"
      + "  name: Phase 2\n"
      + "  phaseSteps:\n"
      + "  - type: CONTAINER_SETUP\n"
      + "    name: Setup Container\n"
      + "    steps:\n"
      + "    - type: KUBERNETES_SETUP\n"
      + "      name: Kubernetes Service Setup\n"
      + "    stepsInParallel: false\n"
      + "  - type: CONTAINER_DEPLOY\n"
      + "    name: Deploy Containers\n"
      + "    steps:\n"
      + "    - type: KUBERNETES_DEPLOY\n"
      + "      name: Upgrade Containers\n"
      + "      properties:\n"
      + "        commandName: Resize Replication Controller\n"
      + "        instanceCount: 1\n"
      + "        instanceUnitType: COUNT\n"
      + "    stepsInParallel: false\n"
      + "  - type: VERIFY_SERVICE\n"
      + "    name: Verify Service\n"
      + "    stepsInParallel: false\n"
      + "  - type: WRAP_UP\n"
      + "    name: Wrap Up\n"
      + "    stepsInParallel: false\n"
      + "  provisionNodes: false\n"
      + "  serviceName: SERVICE_NAME\n"
      + "  statefulSet: false\n"
      + "rollbackPhases:\n"
      + "- type: KUBERNETES\n"
      + "  computeProviderName: COMPUTE_PROVIDER_ID\n"
      + "  daemonSet: false\n"
      + "  infraMappingName: direct_Kubernetes\n"
      + "  name: Rollback Phase 1\n"
      + "  phaseNameForRollback: Phase 1\n"
      + "  phaseSteps:\n"
      + "  - type: CONTAINER_DEPLOY\n"
      + "    name: Deploy Containers\n"
      + "    phaseStepNameForRollback: Deploy Containers\n"
      + "    statusForRollback: SUCCESS\n"
      + "    steps:\n"
      + "    - type: KUBERNETES_DEPLOY_ROLLBACK\n"
      + "      name: Rollback Containers\n"
      + "    stepsInParallel: false\n"
      + "  - type: WRAP_UP\n"
      + "    name: Wrap Up\n"
      + "    stepsInParallel: false\n"
      + "  provisionNodes: false\n"
      + "  serviceName: SERVICE_NAME\n"
      + "  statefulSet: false\n"
      + "- type: KUBERNETES\n"
      + "  computeProviderName: COMPUTE_PROVIDER_ID\n"
      + "  daemonSet: false\n"
      + "  infraMappingName: direct_Kubernetes\n"
      + "  name: Rollback Phase 2\n"
      + "  phaseNameForRollback: Phase 2\n"
      + "  phaseSteps:\n"
      + "  - type: CONTAINER_DEPLOY\n"
      + "    name: Deploy Containers\n"
      + "    phaseStepNameForRollback: Deploy Containers\n"
      + "    statusForRollback: SUCCESS\n"
      + "    steps:\n"
      + "    - type: KUBERNETES_DEPLOY_ROLLBACK\n"
      + "      name: Rollback Containers\n"
      + "    stepsInParallel: false\n"
      + "  - type: WRAP_UP\n"
      + "    name: Wrap Up\n"
      + "    stepsInParallel: false\n"
      + "  provisionNodes: false\n"
      + "  serviceName: SERVICE_NAME\n"
      + "  statefulSet: false\n"
      + "templatized: false";
  public static final String MULTI_SERVICE_VALID_YAML_FILE_PATH =
      "Setup/Applications/APP_NAME/Workflows/multiService.yaml";
  public static final String MULTI_SERVICE_INVALID_YAML_CONTENT =
      "envName: env1\nphaseInvalid: phase1\ntype: MULTI_SERVICE";
  public static final String MULTI_SERVICE_INVALID_YAML_FILE_PATH =
      "Setup/Applications/APP_NAME/WorkflowsInvalid/multiService.yaml";
  // END STRING CONSTANTS FOR MULTI_SERVICE WORKFLOW YAML TEST
}
