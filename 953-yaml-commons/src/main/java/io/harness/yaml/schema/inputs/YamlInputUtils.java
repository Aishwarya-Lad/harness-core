/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.schema.inputs;

import static io.harness.yaml.individualschema.InputFieldMetadata.parentTypesOfNodeGroups;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.jackson.JsonNodeUtils;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.fqn.FQNNode;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlNodeUtils;
import io.harness.pms.yaml.YamlSchemaFieldConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.schema.inputs.beans.InputDetails;
import io.harness.yaml.schema.inputs.beans.InputDetails.InputDetailsBuilder;
import io.harness.yaml.schema.inputs.beans.SchemaInputType;
import io.harness.yaml.utils.JsonFieldUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@UtilityClass
@Slf4j
public class YamlInputUtils {
  public List<InputDetails> getYamlInputList(String yaml) {
    List<InputDetails> inputDetails = new ArrayList<>();
    ObjectNode inputsJsonNode;

    JsonNode yamlJsonNode = YamlUtils.readAsJsonNode(yaml);
    // should pick hardcoded values from some constants, need to change it

    JsonNode inputsParentNode = getInputsParentNode(yamlJsonNode);
    inputsJsonNode = (ObjectNode) JsonFieldUtils.get(inputsParentNode, YamlSchemaFieldConstants.INPUTS);
    if (inputsJsonNode == null) {
      return inputDetails;
    }

    Iterator<String> inputNames = inputsJsonNode.fieldNames();
    for (Iterator<String> it = inputNames; it.hasNext();) {
      String inputName = it.next();
      InputDetails inputDetailForGivenName =
          buildInputDetails(JsonFieldUtils.get(inputsJsonNode, inputName), inputName);
      if (inputDetailForGivenName != null) {
        inputDetails.add(inputDetailForGivenName);
      }
    }

    return inputDetails;
  }

  public Map<Set<String>, InputDetails> prepareYamlInputExpressionToYamlInputMap(List<InputDetails> inputDetailsList) {
    Map<Set<String>, InputDetails> yamlInputMap = new LinkedHashMap<>();
    inputDetailsList.forEach(inputDetails -> {
      Set<String> possibleExpressions = new HashSet<>();
      possibleExpressions.add("<+inputs." + inputDetails.getName() + ">");
      possibleExpressions.add("<+inputs.get(\\\"" + inputDetails.getName() + "\\\")>");
      yamlInputMap.put(possibleExpressions, inputDetails);
    });
    return yamlInputMap;
  }

  public Map<String, List<FQN>> parseFQNsForAllInputsInYaml(
      Map<FQN, Object> fqnToValueMap, Set<Set<String>> inputExpressionsList) {
    Set<String> allInputExpressions = new HashSet<>();
    inputExpressionsList.forEach(allInputExpressions::addAll);

    Map<String, List<FQN>> FQNListForEachInput = new HashMap<>();
    fqnToValueMap.forEach((fqn, v) -> {
      String value = v.toString();
      // remove double quoted string if present
      if (value.charAt(0) == '"') {
        value = value.substring(1);
      }
      if (value.charAt(value.length() - 1) == '"') {
        value = value.substring(0, value.length() - 1);
      }
      if (allInputExpressions.contains(value)) {
        if (!FQNListForEachInput.containsKey(value)) {
          FQNListForEachInput.put(value, new ArrayList<>());
        }
        FQNListForEachInput.get(value).add(fqn);
      }
    });
    return FQNListForEachInput;
  }

  private InputDetails buildInputDetails(JsonNode inputNode, String inputName) {
    // If value is present in some input then we that would be considered as variable with fixed value. So we should not
    // return such inputs.
    if (inputNode.has(YAMLFieldNameConstants.VALUE)) {
      return null;
    }
    InputDetailsBuilder builder =
        InputDetails.builder().name(inputName).type(SchemaInputType.getYamlInputType(inputNode.get("type").asText()));
    if (inputNode.get(YAMLFieldNameConstants.DESC) != null) {
      builder.description(inputNode.get(YAMLFieldNameConstants.DESC).asText());
    }
    if (inputNode.get(YAMLFieldNameConstants.REQUIRED) != null) {
      builder.required(inputNode.get(YAMLFieldNameConstants.REQUIRED).asBoolean());
    }
    if (inputNode.has(YAMLFieldNameConstants.DEFAULT)) {
      builder.defaultValue(JsonNodeUtils.getValueFromJsonNode(inputNode.get(YAMLFieldNameConstants.DEFAULT)));
    }
    if (inputNode.has(YAMLFieldNameConstants.EXECUTION)) {
      builder.execution(inputNode.get(YAMLFieldNameConstants.EXECUTION).asBoolean());
    }
    JsonNode validatorNode = inputNode.get(YAMLFieldNameConstants.VALIDATOR);
    if (validatorNode != null) {
      if (validatorNode.has(YAMLFieldNameConstants.ALLOWED)) {
        if (!validatorNode.get(YAMLFieldNameConstants.ALLOWED).isArray()) {
          throw new InvalidRequestException(
              String.format("Provided value for %s field should be of type List", YAMLFieldNameConstants.ALLOWED));
        }
        builder.allowedValues(
            (List<Object>) JsonNodeUtils.getValueFromJsonNode(validatorNode.get(YAMLFieldNameConstants.ALLOWED)));
      } else if (validatorNode.has(YAMLFieldNameConstants.REGEX)) {
        builder.regex(validatorNode.get(YAMLFieldNameConstants.REGEX).asText());
      }
    }
    return builder.build();
  }

  private JsonNode getInputsParentNode(JsonNode yamlJsonNode) {
    if (JsonFieldUtils.get(yamlJsonNode, YamlSchemaFieldConstants.VERSION) != null) {
      return JsonFieldUtils.get(yamlJsonNode, YamlSchemaFieldConstants.SPEC);
    }
    return JsonFieldUtils.get(yamlJsonNode, YamlSchemaFieldConstants.PIPELINE);
  }

  public String getParentNodeTypeForGivenFQNField(JsonNode jsonNode, FQN fqn) {
    YamlNode yamlNode = new YamlNode(jsonNode);
    int parentNodeIndex = -1;
    for (int i = 1; i < fqn.getFqnList().size(); i++) {
      if (fqn.getFqnList().get(i).getNodeType() == FQNNode.NodeType.UUID
          && parentTypesOfNodeGroups.contains(fqn.getFqnList().get(i - 1).getKey())) {
        parentNodeIndex = i;
      }
    }
    if (parentNodeIndex == -1) {
      return "";
    }
    String pathTillParent =
        FQN.builder().fqnList(fqn.getFqnList().subList(0, parentNodeIndex + 1)).build().getExpressionFqn();
    YamlNode parentNode = YamlNodeUtils.goToPathUsingFqn(yamlNode, pathTillParent);
    if (parentNode == null) {
      return "";
    }
    return JsonFieldUtils.getTextOrEmpty(parentNode.getCurrJsonNode(), YamlSchemaFieldConstants.TYPE);
  }
}
