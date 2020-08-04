package com.endava.cats.fuzzer.fields;

import com.endava.cats.CatsMain;
import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.google.gson.JsonElement;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class CustomFuzzer implements Fuzzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomFuzzer.class);
    private static final String EXPECTED_RESPONSE_CODE = "expectedResponseCode";
    private static final String OUTPUT = "output";
    private static final String DESCRIPTION = "description";
    private static final String NOT_SET = "NOT_SET";

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final CatsUtil catsUtil;
    private final Map<String, String> variables = new HashMap<>();

    @Value("${customFuzzerFile:empty}")
    private String customFuzzerFile;

    private Map<String, Map<String, Object>> customFuzzerDetails = new HashMap<>();

    @Autowired
    public CustomFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
        this.catsUtil = cu;
    }

    @PostConstruct
    public void loadCustomFuzzerFile() {
        try {
            if (CatsMain.EMPTY.equalsIgnoreCase(customFuzzerFile)) {
                LOGGER.info("No custom Fuzzer file. CustomFuzzer will be skipped!");
            } else {
                customFuzzerDetails = catsUtil.parseYaml(customFuzzerFile);
            }
        } catch (Exception e) {
            LOGGER.error("Error processing customFuzzerFile!", e);
        }
    }

    public void fuzz(FuzzingData data) {
        if (!customFuzzerDetails.isEmpty()) {
            this.processCustomFuzzerFile(data);
        }
    }

    protected void processCustomFuzzerFile(FuzzingData data) {
        try {
            Map<String, Object> currentPathValues = customFuzzerDetails.get(data.getPath());
            if (currentPathValues != null) {
                currentPathValues.forEach((key, value) -> this.executeTestCases(data, key, value));
            } else {
                LOGGER.info("Skipping path [{}] as it was not configured in customFuzzerFile", data.getPath());
            }
        } catch (Exception e) {
            LOGGER.error("Error processing customFuzzerFile!", e);
        }
    }

    private void executeTestCases(FuzzingData data, String key, Object value) {
        LOGGER.info("Path [{}] has the following custom data [{}]", data.getPath(), value);

        if (this.entryIsValid((Map<String, Object>) value)) {
            List<Map<String, String>> individualTestCases = this.createIndividualRequest((Map<String, Object>) value);
            for (Map<String, String> individualTestCase : individualTestCases) {
                testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data, key, individualTestCase));
            }
        } else {
            LOGGER.warn("Skipping path [{}] as not valid. It either doesn't contain a valid expectedResponseCode or there is more than one list of values for a specific field", data.getPath());
        }
    }

    private void process(FuzzingData data, String testName, Map<String, String> currentPathValues) {
        String testScenario = this.getTestScenario(testName, currentPathValues);
        testCaseListener.addScenario(LOGGER, "Scenario: {}", testScenario);
        String expectedResponseCode = String.valueOf(currentPathValues.get(EXPECTED_RESPONSE_CODE));
        testCaseListener.addExpectedResult(LOGGER, "Expected result: should return [{}]", expectedResponseCode);

        String payloadWithCustomValuesReplaced = this.getStringWithCustomValuesFromFile(data, currentPathValues);
        CatsResponse response = serviceCaller.call(data.getMethod(), ServiceData.builder().relativePath(data.getPath()).replaceRefData(false)
                .headers(data.getHeaders()).payload(payloadWithCustomValuesReplaced).queryParams(data.getQueryParams()).build());

        this.setOutputVariables(currentPathValues, response);

        testCaseListener.reportResult(LOGGER, data, response, ResponseCodeFamily.from(expectedResponseCode));
    }

    private String getTestScenario(String testName, Map<String, String> currentPathValues) {
        String description = currentPathValues.get(DESCRIPTION);
        if (StringUtils.isNotBlank(description)) {
            return description;
        }

        return "send request with custom values supplied. Test key [" + testName + "]";
    }

    private void setOutputVariables(Map<String, String> currentPathValues, CatsResponse response) {
        String output = currentPathValues.get(OUTPUT);

        if (output != null) {
            if (StringUtils.isNotBlank(output)) {
                output = output.replace("{", "").replace("}", "");
                variables.putAll(Arrays.stream(output.split(","))
                        .map(variable -> variable.trim().split("=")).collect(Collectors.toMap(
                                variableArray -> variableArray[0],
                                variableArray -> variableArray[1]
                        )));
            }

            JsonElement body = response.getJsonBody();
            if (body.isJsonArray()) {
                LOGGER.error("Arrays are not supported for Output variables!");
            } else {
                variables.putAll(variables.entrySet().stream().collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> this.getOutputVariable(body, entry.getValue()))
                ));
            }
            LOGGER.info("The following OUTPUT variables were identified {}", variables);
        }
    }

    private String getOutputVariable(JsonElement body, String value) {
        JsonElement outputVariable = catsUtil.getJsonElementBasedOnFullyQualifiedName(body, value);

        if (outputVariable == null || outputVariable.isJsonNull()) {
            LOGGER.error("Expected variable {} was not found on response. Setting to NOT_SET", value);
            return NOT_SET;
        }
        if (outputVariable.isJsonArray()) {
            LOGGER.error("Arrays are not supported. Variable {} will be set to NOT_SET", value);
            return NOT_SET;
        }
        String[] depth = value.split("#");
        return outputVariable.getAsJsonObject().get(depth[depth.length - 1]).getAsString();
    }

    private String getStringWithCustomValuesFromFile(FuzzingData data, Map<String, String> currentPathValues) {
        JsonElement jsonElement = catsUtil.parseAsJsonElement(data.getPayload());

        if (jsonElement.isJsonObject()) {
            this.replaceFieldsWithCustomValue(currentPathValues, jsonElement);
        } else if (jsonElement.isJsonArray()) {
            for (JsonElement element : jsonElement.getAsJsonArray()) {
                replaceFieldsWithCustomValue(currentPathValues, element);
            }
        }
        LOGGER.info("Final payload after reference data replacement [{}]", jsonElement);

        return jsonElement.toString();
    }

    private void replaceFieldsWithCustomValue(Map<String, String> currentPathValues, JsonElement jsonElement) {
        for (Map.Entry<String, String> entry : currentPathValues.entrySet()) {
            if (this.isNotAReservedWord(entry.getKey())) {
                this.replaceElementWithCustomValue(entry, jsonElement);
            }
        }
    }

    private boolean isNotAReservedWord(String key) {
        return !key.equalsIgnoreCase(OUTPUT) && !key.equalsIgnoreCase(DESCRIPTION) && !key.equalsIgnoreCase(EXPECTED_RESPONSE_CODE);
    }


    private void replaceElementWithCustomValue(Map.Entry<String, String> entry, JsonElement jsonElement) {
        JsonElement element = catsUtil.getJsonElementBasedOnFullyQualifiedName(jsonElement, entry.getKey());
        String[] depth = entry.getKey().split("#");

        if (element != null) {
            String key = depth[depth.length - 1];
            String propertyValue = this.getPropertyValueToReplaceInBody(entry);

            if (element.getAsJsonObject().remove(key) != null) {
                element.getAsJsonObject().addProperty(key, propertyValue);
                LOGGER.info("Replacing property [{}] with value [{}]", entry.getKey(), propertyValue);
            } else {
                LOGGER.error("Property [{}] does not exist", entry.getKey());
            }
        }
    }

    private String getPropertyValueToReplaceInBody(Map.Entry<String, String> entry) {
        String propertyValue = String.valueOf(entry.getValue());

        if (propertyValue.startsWith("${") && propertyValue.endsWith("}")) {
            String variableValue = variables.get(propertyValue.replace("${", "").replace("}", ""));

            if (variableValue == null) {
                LOGGER.error("Supplied variable was not found [{}]", propertyValue);
            } else {
                LOGGER.info("Variable [{}] found. Will be replaced with [{}]", propertyValue, variableValue);
                propertyValue = variableValue;
            }
        }
        return propertyValue;
    }

    /**
     * Custom tests can contain multiple values for a specific field. We iterate through those values and create a list of individual requests
     *
     * @param testCase object from the custom fuzzer file
     * @return individual requests
     */
    private List<Map<String, String>> createIndividualRequest(Map<String, Object> testCase) {
        Optional<Map.Entry<String, Object>> listOfValuesOptional = testCase.entrySet().stream().filter(entry -> entry.getValue() instanceof List).findFirst();
        List<Map<String, String>> allValues = new ArrayList<>();

        if (listOfValuesOptional.isPresent()) {
            Map.Entry<String, Object> listOfValues = listOfValuesOptional.get();
            for (Object value : (List) listOfValues.getValue()) {
                testCase.put(listOfValues.getKey(), value);
                allValues.add(testCase.entrySet()
                        .stream().collect(Collectors.toMap(Map.Entry::getKey, en -> String.valueOf(en.getValue()))));
            }
            return allValues;
        }

        return Collections.singletonList(testCase.entrySet()
                .stream().collect(Collectors.toMap(Map.Entry::getKey, en -> String.valueOf(en.getValue()))));
    }

    private boolean entryIsValid(Map<String, Object> currentPathValues) {
        boolean responseCodeValid = ResponseCodeFamily.isValidCode(String.valueOf(currentPathValues.get(EXPECTED_RESPONSE_CODE)));
        boolean hasAtMostOneArrayOfData = currentPathValues.entrySet().stream().filter(entry -> entry.getValue() instanceof ArrayList).count() <= 1;

        return responseCodeValid && hasAtMostOneArrayOfData;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String description() {
        return "allows to configure user supplied values for specific fields withing payloads; this is useful when testing scenarios where the the user want to test a predefined list of blacklisted strings";
    }
}
