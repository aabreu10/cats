package com.endava.cats.factory;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.generator.format.api.ValidDataFormat;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsField;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.NoMediaType;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonParser;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.parser.core.models.ParseOptions;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@QuarkusTest
class FuzzingDataFactoryTest {
    @Inject
    CatsGlobalContext catsGlobalContext;
    @Inject
    ValidDataFormat validDataFormat;
    private FilesArguments filesArguments;
    private ProcessingArguments processingArguments;
    private FilterArguments filterArguments;
    private FuzzingDataFactory fuzzingDataFactory;

    @BeforeEach
    void setup() {
        filesArguments = Mockito.mock(FilesArguments.class);
        processingArguments = Mockito.mock(ProcessingArguments.class);
        filterArguments = Mockito.mock(FilterArguments.class);
        Mockito.when(processingArguments.isUseExamples()).thenReturn(true);
        Mockito.when(processingArguments.getLimitXxxOfCombinations()).thenReturn(10);
        Mockito.when(processingArguments.isGenerateAllXxxCombinationsForResponses()).thenReturn(true);
        Mockito.when(processingArguments.isFilterXxxFromRequestPayloads()).thenReturn(false);
        Mockito.when(processingArguments.getContentType()).thenReturn(List.of(JsonUtils.JSON_WILDCARD, "application/x-www-form-urlencoded"));
        Mockito.when(processingArguments.examplesFlags()).thenReturn(new ProcessingArguments.ExamplesFlags(true, true, true, true));
        fuzzingDataFactory = new FuzzingDataFactory(filesArguments, processingArguments, catsGlobalContext, validDataFormat, filterArguments);
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(4);
        catsGlobalContext.getRecordedErrors().clear();
    }

    @Test
    void shouldFilterOutXxxExamples() throws Exception {
        Mockito.when(processingArguments.getLimitXxxOfCombinations()).thenReturn(2);
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(5);
        Mockito.when(processingArguments.examplesFlags()).thenReturn(new ProcessingArguments.ExamplesFlags(false, false, false, false));

        List<FuzzingData> dataList = setupFuzzingData("/api/v2/meta/tables/{tableId}/columns", "src/test/resources/nocodb.yaml");

        Assertions.assertThat(dataList).hasSize(2);
        FuzzingData firstData = dataList.get(1);
        Assertions.assertThat(firstData.getPayload()).doesNotContain("ANY_OF", "ONE_OF");
        Assertions.assertThat(firstData.getAllFieldsAsCatsFields()).hasSizeLessThanOrEqualTo(firstData.getRequestPropertyTypes().size());
    }

    @Test
    void shouldGenerateMultiplePayloadsWhenRootOneOfAndDiscriminatorAndAllOfAndMappings() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/variant", "src/test/resources/issue_69.yml");
        Assertions.assertThat(data).hasSize(1);

        FuzzingData firstData = data.getFirst();
        List<String> responses = firstData.getResponses().get("200");
        Assertions.assertThat(responses).hasSize(2);
        Assertions.assertThat(responses.getFirst()).doesNotContain("Variant").contains("option1");
        Assertions.assertThat(responses.get(1)).doesNotContain("Variant").contains("option2");
        Assertions.assertThat(firstData.getAllFieldsAsCatsFields()).hasSizeLessThanOrEqualTo(firstData.getRequestPropertyTypes().size());
    }

    @Test
    void shouldAddDefaultContentTypeForResponses() throws Exception {
        Mockito.doCallRealMethod().when(processingArguments).getDefaultContentType();
        List<FuzzingData> data = setupFuzzingData("/parents", "src/test/resources/issue127.json");

        Assertions.assertThat(data).hasSizeGreaterThanOrEqualTo(2);
        Optional<FuzzingData> getRequest = data.stream().filter(fuzzingData -> fuzzingData.getMethod() == HttpMethod.GET).findFirst();
        Assertions.assertThat(getRequest).isPresent();
        Assertions.assertThat(getRequest.get().getResponseContentTypes().values()).containsOnly(List.of("application/json"));
    }

    @Test
    void shouldResolveResponseBodyWhenRefInRef() throws Exception {
        Mockito.doCallRealMethod().when(processingArguments).getDefaultContentType();
        List<FuzzingData> data = setupFuzzingData("/api/v1/auditevents", "src/test/resources/1password.yaml");

        Assertions.assertThat(data).hasSizeGreaterThanOrEqualTo(2);
        FuzzingData firstData = data.getFirst();

        Assertions.assertThat(firstData.getResponseContentTypes().values()).containsOnly(List.of("application/json"));
    }

    @Test
    void givenAContract_whenParsingThePathItemDetailsForPost_thenCorrectFuzzingDataAreBeingReturned() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pets", "src/test/resources/petstore.yml");

        Assertions.assertThat(data).hasSizeGreaterThanOrEqualTo(2);

        Assertions.assertThat(data.getFirst().getPayload()).doesNotContain("ONE_OF", "ANY_OF");
        Assertions.assertThat(data.get(1).getPayload()).doesNotContain("ONE_OF", "ANY_OF");
        Assertions.assertThat(data.getFirst().getAllFieldsAsCatsFields()).hasSizeLessThanOrEqualTo(data.getFirst().getRequestPropertyTypes().size());
    }

    @Test
    void shouldIgnoreOneOfAnyOfWhenAdditionalSchemaIsNull() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/path1", "src/test/resources/oneOf_with_null_additional.yml");
        Assertions.assertThat(data).hasSize(1);

        Assertions.assertThat(data.getFirst().getPayload()).contains("dateFrom");
        Assertions.assertThat(data.getFirst().getPayload()).doesNotContain("ONE_OF", "ANY_OF");
        Assertions.assertThat(data.getFirst().getAllFieldsAsCatsFields()).hasSizeLessThanOrEqualTo(data.getFirst().getRequestPropertyTypes().size());
    }

    @Test
    void shouldNotGenerateRequestBodyWhenPostButSchemaEmpty() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pets", "src/test/resources/petstore_empty_body.json");
        Assertions.assertThat(data).hasSize(1);
        Assertions.assertThat(data.getFirst().getMethod()).isEqualTo(HttpMethod.GET);
        Assertions.assertThat(data.getFirst().getAllFieldsAsCatsFields()).hasSizeLessThanOrEqualTo(data.getFirst().getRequestPropertyTypes().size());
    }

    @Test
    void shouldResolveCrossPathExamples() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/v2/apps/{app_id}/deployments/{deployment_id}", "src/test/resources/digitalocean.yaml");

        Object example = catsGlobalContext.getObjectFromPathsReference("#/paths/~1v2~1apps~1%7Bapp_id%7D~1deployments/post/responses/200/content/application~1json/examples/deployment");
        Assertions.assertThat(example).isInstanceOf(Example.class);
        Assertions.assertThat(data).hasSize(1);
        Assertions.assertThat(example).asString().contains("deployment");
    }

    @Test
    void shouldLoadExamples() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pets", "src/test/resources/petstore.yml");
        Assertions.assertThat(data.getFirst().getExamples()).hasSize(2);
        Assertions.assertThat(data.getFirst().getExamples())
                .anyMatch(example -> Objects.toString(example).contains("dog-example"))
                .anyMatch(example -> Objects.toString(example).contains("dog-no-ref"));
    }

    @Test
    void shouldResolveExampleCrossSchemas() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pet-types-rec", "src/test/resources/petstore-examples.yml");
        Assertions.assertThat(data).hasSize(2);

        FuzzingData firstData = data.getFirst();
        Assertions.assertThat(firstData.getPayload()).contains("9999CATS");
    }

    @Test
    void shouldUseExamplesWhenResponseBodyFlagEnabled() throws Exception {
        Mockito.when(processingArguments.isUseExamples()).thenReturn(false);
        Mockito.when(processingArguments.isUseResponseBodyExamples()).thenReturn(true);
        Mockito.when(processingArguments.examplesFlags()).thenReturn(new ProcessingArguments.ExamplesFlags(true, false, false, false));
        Mockito.when(processingArguments.getDefaultContentType()).thenReturn("application/json");

        List<FuzzingData> data = setupFuzzingData("/pets-batch", "src/test/resources/petstore-examples.yml");
        Assertions.assertThat(data).hasSize(2);

        FuzzingData firstData = data.getFirst();
        Assertions.assertThat(firstData.getResponses().get("200")).hasSize(1);
        String firstResponse = firstData.getResponses().get("200").getFirst();
        Assertions.assertThat(firstResponse).contains("oneExampleField", "secondExampleField", "exampleValue", "anotherValue");
    }

    @Test
    void shouldUseExamplesWhenSchemaFlagEnabled() throws Exception {
        Mockito.when(processingArguments.isUseExamples()).thenReturn(false);
        Mockito.when(processingArguments.isUseSchemaExamples()).thenReturn(true);
        Mockito.when(processingArguments.isUseRequestBodyExamples()).thenReturn(false);
        Mockito.when(processingArguments.examplesFlags()).thenReturn(new ProcessingArguments.ExamplesFlags(false, false, true, true));

        List<FuzzingData> data = setupFuzzingData("/pets-small", "src/test/resources/petstore-examples.yml");
        Assertions.assertThat(data).hasSize(1);

        FuzzingData firstData = data.getFirst();
        //just first example is taken into consideration
        Assertions.assertThat(firstData.getPayload()).contains("dog-example-1", "myId");
    }

    @Test
    void shouldNotUseExamplesWhenSchemaFlagDisabled() throws Exception {
        Mockito.when(processingArguments.isUseExamples()).thenReturn(false);
        Mockito.when(processingArguments.isUseSchemaExamples()).thenReturn(false);
        Mockito.when(processingArguments.isUseRequestBodyExamples()).thenReturn(false);
        Mockito.when(processingArguments.examplesFlags()).thenReturn(new ProcessingArguments.ExamplesFlags(false, false, false, true));

        List<FuzzingData> data = setupFuzzingData("/pets-small", "src/test/resources/petstore-examples.yml");
        Assertions.assertThat(data).hasSize(1);

        FuzzingData firstData = data.getFirst();
        Assertions.assertThat(firstData.getPayload()).doesNotContain("dog-example-1", "myId");
    }

    @Test
    void shouldUseExamplesWhenRequestBodiesFlagEnabled() throws Exception {
        Mockito.when(processingArguments.isUseExamples()).thenReturn(false);
        Mockito.when(processingArguments.isUseRequestBodyExamples()).thenReturn(true);
        Mockito.when(processingArguments.examplesFlags()).thenReturn(new ProcessingArguments.ExamplesFlags(false, true, false, true));
        List<FuzzingData> data = setupFuzzingData("/pets-batch", "src/test/resources/petstore-examples.yml");
        Assertions.assertThat(data).hasSize(1);

        FuzzingData firstData = data.getFirst();
        Assertions.assertThat(firstData.getPayload()).contains("pet1", "pet2", "pet3");
    }

    @Test
    void shouldNotUseExamplesWhenRequestBodyFlagDisabled() throws Exception {
        Mockito.when(processingArguments.isUseExamples()).thenReturn(false);
        Mockito.when(processingArguments.isUseRequestBodyExamples()).thenReturn(false);
        Mockito.when(processingArguments.examplesFlags()).thenReturn(new ProcessingArguments.ExamplesFlags(false, false, false, false));

        List<FuzzingData> data = setupFuzzingData("/pets-batch", "src/test/resources/petstore-examples.yml");
        Assertions.assertThat(data).hasSize(2);

        FuzzingData firstData = data.getFirst();
        Assertions.assertThat(firstData.getPayload()).doesNotContain("pet1", "pet2", "pet3");

        FuzzingData secondData = data.get(1);
        Assertions.assertThat(secondData.getPayload()).doesNotContain("pet1", "pet2", "pet3");
    }


    @Test
    void shouldGenerateValidAnyOfCombinationWhenForLevel3Nesting() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/api/some-endpoint", "src/test/resources/issue66.yml");
        Assertions.assertThat(data).hasSize(2);
        Assertions.assertThat(data.get(1).getPayload()).contains("someSubObjectKey3");
        Assertions.assertThat(data.getFirst().getPayload()).doesNotContain("someSubObjectKey3");
        Assertions.assertThat(data.getFirst().getAllFieldsAsCatsFields()).hasSizeLessThanOrEqualTo(data.getFirst().getRequestPropertyTypes().size());
    }

    @Test
    void shouldGenerateArraySchemaBasedOnMinItemsAndMaxItems() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/api/some-endpoint", "src/test/resources/issue66_2.yml");
        Assertions.assertThat(data).hasSize(2);
        String firstPayload = data.get(1).getPayload();
        int firstArraySize = Integer.parseInt(JsonUtils.getVariableFromJson(firstPayload, "$.someRequestBodyKey1.someObjectKey1.someSubObjectKey1.length()").toString());
        int secondArraySize = Integer.parseInt(JsonUtils.getVariableFromJson(firstPayload, "$.someRequestBodyKey1.someObjectKey1.someSubObjectKey1[0].length()").toString());
        int thirdArraySize = Integer.parseInt(JsonUtils.getVariableFromJson(firstPayload, "$.someRequestBodyKey1.someObjectKey1.someSubObjectKey1[0][0].length()").toString());

        Assertions.assertThat(firstArraySize).isEqualTo(2);
        Assertions.assertThat(secondArraySize).isEqualTo(4);
        Assertions.assertThat(thirdArraySize).isEqualTo(2);
    }


    @Test
    void shouldProperlyParseOneOfWithBaseClass() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/path1", "src/test/resources/oneOf_with_base_class.yml");
        Assertions.assertThat(data).hasSize(2);

        String firstPayload = data.getFirst().getPayload();
        Object type = JsonUtils.getVariableFromJson(firstPayload, "$.payload.type");
        Object innerPayload = JsonUtils.getVariableFromJson(firstPayload, "$.payload.payload");

        Assertions.assertThat(type).asString().isEqualTo("Address");
        Assertions.assertThat(innerPayload).asString().isEqualTo("NOT_SET");
    }

    @Test
    void shouldReturnRequiredFieldsWhenAllOfSchemaAndRequiredInRoot() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pets", "src/test/resources/allof-with-required-in-root.yml");

        Assertions.assertThat(data).hasSize(1);
        FuzzingData fuzzingData = data.getFirst();
        Assertions.assertThat(fuzzingData.getAllRequiredFields()).containsExactly("breed", "id").doesNotContain("color");
    }

    @Test
    void shouldReturnRequiredFieldsWhenAllOfSchemaAndRequiredPropertiesInBothSchemas() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pets", "src/test/resources/allof-with-required-for-both-schemas.yml");

        Assertions.assertThat(data).hasSize(1);
        FuzzingData fuzzingData = data.getFirst();
        Assertions.assertThat(fuzzingData.getAllRequiredFields()).containsExactly("legs", "breed", "id").doesNotContain("color");
        Assertions.assertThat(fuzzingData.getAllFieldsAsCatsFields()).hasSizeLessThanOrEqualTo(fuzzingData.getRequestPropertyTypes().size());
    }

    @Test
    void shouldLoadExample() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pet-types-rec", "src/test/resources/petstore.yml");
        Assertions.assertThat(data.getFirst().getExamples()).hasSize(1);
        Assertions.assertThat(data.getFirst().getExamples()).anyMatch(example -> Objects.toString(example).contains("dog-simple-example"));
    }

    @Test
    void shouldCreateFuzzingDataForEmptyPut() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pets/{id}", "src/test/resources/petstore-empty.yml");
        Assertions.assertThat(data).hasSize(1);
        Assertions.assertThat(data.getFirst().getMethod()).isEqualByComparingTo(HttpMethod.PUT);
    }

    @Test
    void shouldUseExamplesForPathParams() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pets/{id}", "src/test/resources/petstore.yml");
        Assertions.assertThat(data).hasSize(1);
        Assertions.assertThat(data.getFirst().getPayload()).contains("78").contains("test");
    }

    @Test
    void shouldNotIncludeReadOnlyFields() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/pets", "src/test/resources/petstore-readonly.yml");
        Assertions.assertThat(data).hasSize(2);
        FuzzingData postData = data.getFirst();
        Assertions.assertThat(postData.getPayload()).doesNotContain("id", "details").contains("age", "data", "name");

        Set<String> allFields = postData.getAllFieldsByHttpMethod();
        Assertions.assertThat(allFields).containsOnly("data#name", "data", "age");
    }

    private List<FuzzingData> setupFuzzingData(String path, String contract) throws IOException {
        return this.setupFuzzingData(path, contract, true);
    }

    private List<FuzzingData> setupFuzzingData(String path, String contract, boolean resolve) throws IOException {
        OpenAPIParser openAPIV3Parser = new OpenAPIParser();
        ParseOptions options = new ParseOptions();
        options.setResolve(resolve);
        options.setFlatten(resolve);

        OpenAPI openAPI = openAPIV3Parser.readContents(Files.readString(Paths.get(contract)), null, options).getOpenAPI();
        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, List.of("application\\/.*\\+?json"));
        catsGlobalContext.getSchemaMap().clear();
        catsGlobalContext.getSchemaMap().putAll(schemas);
        catsGlobalContext.getExampleMap().putAll(OpenApiUtils.getExamples(openAPI));
        catsGlobalContext.getSchemaMap().put(NoMediaType.EMPTY_BODY, NoMediaType.EMPTY_BODY_SCHEMA);
        catsGlobalContext.getSchemaMap().remove("");
        PathItem pathItem = openAPI.getPaths().get(path);
        catsGlobalContext.setOpenAPI(openAPI);

        Mockito.when(filesArguments.isNotUrlParam(Mockito.anyString())).thenReturn(true);
        return fuzzingDataFactory.fromPathItem(path, pathItem, openAPI);
    }

    @Test
    void shouldCorrectlyParseRefOneOf() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/pet-types", "src/test/resources/petstore.yml");
        Assertions.assertThat(dataList).hasSize(2);
        Assertions.assertThat(dataList.getFirst().getPayload()).contains("\"petType\":{\"breedType\":\"Husky");
        Assertions.assertThat(dataList.get(1).getPayload()).contains("\"petType\":{\"breedType\":\"Labrador");
    }

    @Test
    void shouldGenerateMultiplePayloadsWhenContractGeneratedFromNSwagAndMultipleOneOf() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/api/groopits/create", "src/test/resources/nswag_gen_oneof.json");

        Assertions.assertThat(dataList).hasSize(9);
        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .noneMatch(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"));
        FuzzingData firstData = dataList.get(7);
        Assertions.assertThat(firstData.getPayload()).containsAnyOf("\"discriminator\":\"ResponseData\"", "\"discriminator\":\"PictureData\"");
        Assertions.assertThat(firstData.getPayload()).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF");
        Assertions.assertThat(JsonParser.parseString(firstData.getPayload()).getAsJsonObject().get("Components").isJsonArray()).isTrue();
    }

    @Test
    void shouldGenerateWhenAllOfHaveOnlyASingleSubject() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/datasets/{datasetId}", "src/test/resources/keatext.yaml");

        Assertions.assertThat(dataList).hasSize(3);
        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .noneMatch(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"));
        FuzzingData firstData = dataList.getFirst();
        Assertions.assertThat(firstData.getPayload()).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF");
        String keyWithSquares = String.valueOf(JsonUtils.getVariableFromJson(firstData.getPayload(), "$.primaryDate"));

        Assertions.assertThat(keyWithSquares).isNotEqualTo("NOT_SET");
    }

    @Test
    void shouldProperlyParseKeysWithSquareBrackets() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/tags", "src/test/resources/getresp.yaml");

        Assertions.assertThat(dataList).hasSize(5);
        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .noneMatch(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"));
        FuzzingData firstData = dataList.get(1);
        Assertions.assertThat(firstData.getPayload()).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF");
        String keyWithSquares = String.valueOf(JsonUtils.getVariableFromJson(firstData.getPayload(), "$.query[createdAt][to]"));

        Assertions.assertThat(keyWithSquares).isNotEqualTo("NOT_SET");
    }

    @Test
    void shouldProperlyCreateAllXxxCombinations() throws Exception {
        Mockito.when(processingArguments.getLimitXxxOfCombinations()).thenReturn(100);
        List<FuzzingData> dataList = setupFuzzingData("/shipments", "src/test/resources/shippo.yaml");
        Assertions.assertThat(dataList).hasSize(77);
        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .filteredOn(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"))
                .hasSize(4);
    }

    @Test
    void shouldProperlyGenerateFromArrayWithAnyOfElements() throws Exception {
        Mockito.when(processingArguments.isUseExamples()).thenReturn(false);
        Mockito.when(processingArguments.getLimitXxxOfCombinations()).thenReturn(50);
        List<FuzzingData> dataList = setupFuzzingData("/api/v1/studies", "src/test/resources/prolific.yaml");

        Assertions.assertThat(dataList).hasSize(31);
        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .filteredOn(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"))
                .hasSize(1);

        FuzzingData firstData = dataList.getFirst();
        boolean isActionsArray = JsonUtils.isArray(firstData.getPayload(), "$.CreateStudy.completion_codes[0].actions");
        Assertions.assertThat(isActionsArray).isTrue();
    }

    @Test
    void shouldLimitXxxCombinationsWhenCombinationsExceedArgument() throws Exception {
        Mockito.when(processingArguments.getLimitXxxOfCombinations()).thenReturn(100);
        List<FuzzingData> dataList = setupFuzzingData("/v0/organizations/{organization-id}/onboarding/applications", "src/test/resources/griffin.yaml");

        Assertions.assertThat(dataList).hasSize(100);
        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .filteredOn(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"))
                .isEmpty();

        FuzzingData firstData = dataList.getFirst();
        boolean isActionsArray = JsonUtils.isArray(firstData.getPayload(), "$.subject-profile.claims");
        Assertions.assertThat(isActionsArray).isTrue();
    }

    @Test
    void shouldGenerateCombinationWhenXxxArraysAndSimpleTypes() throws IOException {
        List<FuzzingData> dataList = setupFuzzingData("/api/database/tokens/{token_id}", "src/test/resources/baserow.yaml");

        Assertions.assertThat(dataList).hasSize(12);

        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .noneMatch(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"));

        FuzzingData data = dataList.get(4);
        Object createPermission = JsonUtils.getVariableFromJson(data.getPayload(), "$.permissions.create[0]");
        Object updatePermission = JsonUtils.getVariableFromJson(data.getPayload(), "$.permissions.update[0]");

        Assertions.assertThat(createPermission).asString().isEqualTo("1");
        Assertions.assertThat(updatePermission).asString().isEqualTo("database");
    }

    @Test
    void shouldGenerateCombinationsWhenXxxAsInlineSchemas() throws IOException {
        Mockito.when(processingArguments.isUseExamples()).thenReturn(false);
        List<FuzzingData> dataList = setupFuzzingData("/v1/employee_benefits/{employee_benefit_id}", "src/test/resources/gusto.yaml");

        Assertions.assertThat(dataList).hasSize(4);

        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .noneMatch(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"));

        FuzzingData firstData = dataList.get(1);
        boolean firstDataIsArray = JsonUtils.isArray(firstData.getPayload(), "$.contribution.value");

        FuzzingData zeroData = dataList.getFirst();
        boolean zeroDataIsNotArray = JsonUtils.isArray(zeroData.getPayload(), "$.contribution.value");

        Assertions.assertThat(firstDataIsArray).isTrue();
        Assertions.assertThat(zeroDataIsNotArray).isFalse();
    }

    @Test
    void shouldProperlyParseRootAllOfAndOneOfElements() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/payouts", "src/test/resources/token.yml");

        Assertions.assertThat(dataList).hasSize(11);
        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .noneMatch(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"));
        FuzzingData firstData = dataList.getFirst();
        Assertions.assertThat(firstData.getPayload()).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF");
        Object creditorNotExistent = JsonUtils.getVariableFromJson(firstData.getPayload(), "$.initiation.creditor.creditor");
        String bicValue = String.valueOf(JsonUtils.getVariableFromJson(firstData.getPayload(), "$.initiation.creditor.sortCode"));

        Assertions.assertThat(creditorNotExistent).hasToString("NOT_SET");
        Assertions.assertThat(bicValue).isNotEqualTo("NOT_SET");
    }

    @Test
    void shouldProperlyGenerateOneOfAnyOfPayloads() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/pets-batch", "src/test/resources/petstore.yml");

        Assertions.assertThat(dataList).hasSize(2);
        FuzzingData firstData = dataList.getFirst();
        Assertions.assertThat(firstData.getPayload()).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF");
        Assertions.assertThat(JsonParser.parseString(firstData.getPayload()).isJsonArray()).isTrue();
    }

    @Test
    void shouldGenerateRequestWhenContentTypeFormUrlEncoded() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/pet/url-encoded/{hook_id}", "src/test/resources/petstore.yml");

        Assertions.assertThat(dataList).hasSize(1);
        FuzzingData firstData = dataList.getFirst();
        Assertions.assertThat(firstData.getPayload()).contains("code", "message");
    }

    @Test
    void shouldReturnMediaTypeExample() throws Exception {
        Mockito.when(processingArguments.isUseRequestBodyExamples()).thenReturn(true);
        List<FuzzingData> dataList = setupFuzzingData("/sum", "src/test/resources/issue144.yml");

        Assertions.assertThat(dataList).hasSize(1);
        FuzzingData firstData = dataList.getFirst();
        Assertions.assertThat(JsonUtils.isValidJson(firstData.getPayload())).isTrue();
        Assertions.assertThat(firstData.getPayload()).contains("[23,27,2]");
    }

    @Test
    void shouldGenerateInlineArrayWithOpenApi31() throws Exception {
        Mockito.when(processingArguments.isUseExamples()).thenReturn(false);
        List<FuzzingData> dataList = setupFuzzingData("/sum", "src/test/resources/issue144.yml");

        Assertions.assertThat(dataList).hasSize(1);

        boolean isArray = JsonUtils.isJsonArray(dataList.getFirst().getPayload());
        Assertions.assertThat(isArray).isTrue();

        Object firstElement = JsonUtils.getVariableFromJson(dataList.getFirst().getPayload(), "$[0]");
        Assertions.assertThat(firstElement).isInstanceOf(Double.class);
    }

    @Test
    void shouldGeneratePayloadsWithCrossPathsReferences() throws Exception {
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(5);

        List<FuzzingData> dataList = setupFuzzingData("/v2/account/keys", "src/test/resources/digitalocean.yaml");

        Assertions.assertThat(dataList).hasSize(2);
        FuzzingData firstData = dataList.getFirst();
        Assertions.assertThat(firstData.getPayload()).contains("public_key", "name").doesNotContain("fingerprint", "id");
        Assertions.assertThat(firstData.getReqSchemaName()).isEqualTo("#/paths/~1v2~1account~1keys/get/responses/200/content/application~1json/schema/allOf/0/properties/ssh_keys/items");
        Assertions.assertThat(firstData.getResponseContentTypes().values()).allMatch(contentTypes -> contentTypes.contains("application/json")).allMatch(contentTypes -> contentTypes.size() == 1);
        Assertions.assertThat(firstData.getResponseHeaders().values()).allMatch(headers -> headers.containsAll(List.of("ratelimit-limit", "ratelimit-remaining", "ratelimit-reset"))).allMatch(headers -> headers.size() == 3);
    }

    @Test
    void shouldGenerateWhenCrossPathBodyReference() throws Exception {
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(5);

        List<FuzzingData> dataList = setupFuzzingData("/v2/account/keys/{ssh_key_identifier}", "src/test/resources/digitalocean.yaml");

        Assertions.assertThat(dataList).hasSize(5);
        FuzzingData firstData = dataList.getFirst();
        Assertions.assertThat(firstData.getPayload()).contains("name");
        Assertions.assertThat(firstData.getMethod()).isEqualByComparingTo(HttpMethod.PUT);
    }

    @Test
    void shouldResolveElementsWithEmptyTitle() throws Exception {
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(5);

        List<FuzzingData> dataList = setupFuzzingData("/pet-types", "src/test/resources/petstore.yml");

        Assertions.assertThat(dataList).hasSize(2);
        Schema<?> creatorSchema = catsGlobalContext.getOpenAPI().getComponents().getSchemas().get("MegaPet_creator");
        Assertions.assertThat(creatorSchema).isNotNull();
        Assertions.assertThat(creatorSchema.getProperties()).hasSize(3);
    }


    @Test
    void shouldGenerateCrossPathReferenceParameters() throws Exception {
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(5);

        List<FuzzingData> dataList = setupFuzzingData("/v2/actions", "src/test/resources/digitalocean.yaml");

        Assertions.assertThat(dataList).hasSize(1);
        FuzzingData firstData = dataList.getFirst();
        Assertions.assertThat(firstData.getResponses()).hasSize(5);
        String response200 = firstData.getResponses().get("200").getFirst();
        Assertions.assertThat(response200).contains("region_slug", "resource_id", "resource_type");
        Assertions.assertThat(firstData.getMethod()).isEqualByComparingTo(HttpMethod.GET);

        Object parameter = catsGlobalContext.getObjectFromPathsReference("#/paths/~1v2~1account~1keys~1%7Bssh_key_identifier%7D/get/parameters/0");
        Assertions.assertThat(parameter).isNotNull().isInstanceOf(Parameter.class);
    }

    @Test
    void shouldGenerateCrossPathReferenceHeaders() throws Exception {
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(5);

        List<FuzzingData> dataList = setupFuzzingData("/v2/1-clicks", "src/test/resources/digitalocean.yaml", false);

        Assertions.assertThat(dataList).hasSize(1);
        FuzzingData firstData = dataList.getFirst();
        Assertions.assertThat(firstData.getResponses()).hasSize(5);
        Assertions.assertThat(firstData.getMethod()).isEqualByComparingTo(HttpMethod.GET);

        Set<String> response200 = firstData.getResponseHeaders().get("200");
        Assertions.assertThat(response200).containsOnly("ratelimit-limit", "ratelimit-remaining", "ratelimit-reset");

        Set<String> response401 = firstData.getResponseHeaders().get("401");
        Assertions.assertThat(response401).containsOnly("ratelimit-limit", "ratelimit-remaining", "ratelimit-reset");

        Object header = catsGlobalContext.getObjectFromPathsReference("#/paths/~1v2~11-clicks/get/responses/200/headers/ratelimit-limit");
        Assertions.assertThat(header).isNotNull().isInstanceOf(Header.class);
    }

    @Test
    void shouldGenerateApiResponsesWithSimpleSchemas() throws Exception {
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(5);
        Mockito.when(processingArguments.getDefaultContentType()).thenReturn("application/json");

        List<FuzzingData> dataList = setupFuzzingData("/comments", "src/test/resources/sellsy.yaml");

        Assertions.assertThat(dataList).hasSize(3);
        FuzzingData firstData = dataList.getFirst();
        Assertions.assertThat(firstData.getResponses()).hasSize(2);
        String response201 = firstData.getResponses().get("201").getFirst();
        Assertions.assertThat(response201).contains("facebook", "twitter", "business_segment");
        Assertions.assertThat(firstData.getMethod()).isEqualByComparingTo(HttpMethod.POST);
        String response204 = firstData.getResponses().get("204").getFirst();
        Assertions.assertThat(response204).isEqualTo("null");
    }

    @Test
    void shouldGenerateRequestWhenArrayOfArrayOfString() throws Exception {
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(5);
        Mockito.when(processingArguments.getDefaultContentType()).thenReturn("application/json");

        List<FuzzingData> dataList = setupFuzzingData("/mimic/agent/{agentNum}/value/mset", "src/test/resources/petstore.yml");

        Assertions.assertThat(dataList).hasSize(1);
        FuzzingData firstData = dataList.getFirst();
        boolean isArray = JsonUtils.isJsonArray(firstData.getPayload());
        Assertions.assertThat(isArray).isTrue();
        int firstArraySize = Integer.parseInt(JsonUtils.getVariableFromJson(firstData.getPayload(), "$.length()").toString());
        int innerArraySize = Integer.parseInt(JsonUtils.getVariableFromJson(firstData.getPayload(), "$[0].length()").toString());
        Object thirdLevelArraySize = JsonUtils.getVariableFromJson(firstData.getPayload(), "$[0][0]");
        Object doesNotHaveLength = JsonUtils.getVariableFromJson(firstData.getPayload(), "$[0][0].length()");

        Assertions.assertThat(firstArraySize).isEqualTo(2);
        Assertions.assertThat(innerArraySize).isEqualTo(2);
        Assertions.assertThat(thirdLevelArraySize).isNotNull();
        Assertions.assertThat(doesNotHaveLength).isNull();
    }

    @Test
    void shouldGenerateCrossPathParametersReferences() throws Exception {
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(5);
        Mockito.when(processingArguments.getDefaultContentType()).thenReturn("application/json");

        List<FuzzingData> dataList = setupFuzzingData("/v2/account/keys/{ssh_key_identifier}", "src/test/resources/digitalocean.yaml");

        Assertions.assertThat(dataList).hasSize(5);

        List<FuzzingData> getData = dataList.stream().filter(data -> data.getMethod() == HttpMethod.GET).toList();
        Assertions.assertThat(getData.stream().map(FuzzingData::getPayload)).hasSize(2).allMatch(data -> data.contains("ssh_key_identifier"));

        List<FuzzingData> deleteData = dataList.stream().filter(data -> data.getMethod() == HttpMethod.DELETE).toList();
        Assertions.assertThat(deleteData.stream().map(FuzzingData::getPayload)).hasSize(2).allMatch(data -> data.contains("ssh_key_identifier"));
    }

    @Test
    void shouldGenerateQueryParametersThatAreReferingCrossPathInlineSchema() throws Exception {
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(5);
        Mockito.when(processingArguments.getDefaultContentType()).thenReturn("application/json");

        List<FuzzingData> dataList = setupFuzzingData("/vehicles", "src/test/resources/enode.yaml");
        Assertions.assertThat(dataList).hasSize(1);

        FuzzingData getData = dataList.getFirst();
        Assertions.assertThat(getData.getMethod()).isEqualByComparingTo(HttpMethod.GET);
        Assertions.assertThat(getData.getPayload()).contains("field[]");

        boolean isArray = JsonUtils.isArray(getData.getPayload(), "$.field[]");
        Assertions.assertThat(isArray).isTrue();
    }

    @Test
    void shouldFilterDeprecatedOperations() throws Exception {
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(5);
        Mockito.when(processingArguments.getDefaultContentType()).thenReturn("application/json");

        List<FuzzingData> dataList = setupFuzzingData("/pets", "src/test/resources/petstore-deprecated-tags.yml");

        Assertions.assertThat(dataList).hasSize(4);
        Assertions.assertThat(dataList.getFirst().getMethod()).isEqualTo(HttpMethod.POST);
        Assertions.assertThat(dataList.get(1).getMethod()).isEqualTo(HttpMethod.POST);
        Assertions.assertThat(dataList.get(2).getMethod()).isEqualTo(HttpMethod.GET);
        Assertions.assertThat(dataList.get(3).getMethod()).isEqualTo(HttpMethod.DELETE);

        Mockito.when(filterArguments.isSkipDeprecated()).thenReturn(true);
        List<FuzzingData> secondTimeFuzzDataList = setupFuzzingData("/pets", "src/test/resources/petstore-deprecated-tags.yml");
        Assertions.assertThat(secondTimeFuzzDataList).hasSize(1);
        Assertions.assertThat(secondTimeFuzzDataList.getFirst().getMethod()).isEqualTo(HttpMethod.GET);
    }

    @Test
    void shouldAvoidCyclicDependenciesOnAdditionalProperties() throws Exception {
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(6);
        Mockito.when(processingArguments.getDefaultContentType()).thenReturn("application/json");

        List<FuzzingData> dataList = setupFuzzingData("/containers", "src/test/resources/petstore.yml");
        Assertions.assertThat(dataList).hasSize(1);
        Assertions.assertThat(dataList.getFirst().getMethod()).isEqualTo(HttpMethod.POST);
        String payload = dataList.getFirst().getPayload();
        Object existing = JsonUtils.getVariableFromJson(payload, "$.containers#key#containers#key#containers#key#containers#key#containers#key#containers#key");
        Object nonExisting = JsonUtils.getVariableFromJson(payload, "$.containers#key#containers#key#containers#key#containers#key#containers#key#containers#key#containers");

        Assertions.assertThat(existing).hasToString("{}");
        Assertions.assertThat(nonExisting).hasToString("NOT_SET");
    }

    @Test
    void shouldParseParametersFromPathRoot() throws Exception {
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(5);
        Mockito.when(processingArguments.getDefaultContentType()).thenReturn("application/json");

        List<FuzzingData> dataList = setupFuzzingData("/payments", "src/test/resources/sellsy.yaml");
        Assertions.assertThat(dataList).hasSize(2);
        Assertions.assertThat(dataList.getFirst().getMethod()).isEqualTo(HttpMethod.GET);
        String payload = dataList.getFirst().getPayload();
        boolean isArray = JsonUtils.isArray(payload, "$.embed");
        Assertions.assertThat(isArray).isTrue();
    }

    @Test
    void shouldAvoidCyclicDependenciesOnAdditionalPropertiesSecondCase() throws Exception {
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(5);
        Mockito.when(processingArguments.getDefaultContentType()).thenReturn("application/json");

        List<FuzzingData> dataList = setupFuzzingData("/apis/{api}/{version}/rest", "src/test/resources/discovery.yaml");
        Assertions.assertThat(dataList).hasSize(1);
        Assertions.assertThat(dataList.getFirst().getMethod()).isEqualTo(HttpMethod.GET);
        String payload = dataList.getFirst().getPayload();

        Assertions.assertThat(payload).contains("oauth_token");
        String firstResponse = dataList.getFirst().getResponses().get("200").getFirst();
        Object additionalProps = JsonUtils.getVariableFromJson(firstResponse, "$#methods#key#parameters#key");

        Assertions.assertThat(additionalProps).doesNotHaveToString("NOT_SET");
    }


    @Test
    void shouldOnlyIncludeProvidedTags() throws Exception {
        Mockito.when(filterArguments.getTags()).thenReturn(List.of("pets"));
        List<FuzzingData> dataList = setupFuzzingData("/pets", "src/test/resources/petstore-deprecated-tags.yml");

        Assertions.assertThat(dataList).hasSize(2);
        Assertions.assertThat(dataList.getFirst().getMethod()).isEqualTo(HttpMethod.GET);
        Assertions.assertThat(dataList.get(1).getMethod()).isEqualTo(HttpMethod.DELETE);
    }

    @Test
    void shouldSkipProvidedTags() throws Exception {
        Mockito.when(filterArguments.getSkippedTags()).thenReturn(List.of("pets"));
        List<FuzzingData> dataList = setupFuzzingData("/pets", "src/test/resources/petstore-deprecated-tags.yml");

        Assertions.assertThat(dataList).hasSize(2);
        Assertions.assertThat(dataList.getFirst().getMethod()).isEqualTo(HttpMethod.POST);
        Assertions.assertThat(dataList.get(1).getMethod()).isEqualTo(HttpMethod.POST);
    }

    @Test
    void testAnyOfPrimitiveTypes() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/mfm/v1/services/", "src/test/resources/issue86.json");

        Assertions.assertThat(dataList).hasSize(5);
        Assertions.assertThat(dataList.stream().map(FuzzingData::getPayload).toList())
                .noneMatch(payload -> payload.contains("ANY_OF") || payload.contains("ONE_OF") || payload.contains("ALL_OF"));
        FuzzingData firstData = dataList.getFirst();
        Assertions.assertThat(firstData.getPayload()).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF");
        List<String> responses = dataList.getFirst().getResponses().get("422");
        Assertions.assertThat(responses).hasSize(2);
        String responsePayload = responses.getFirst();
        Assertions.assertThat(responsePayload).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF").contains("loc");
    }

    @Test
    void shouldLimitOneOfAnyOfCombinations() throws Exception {
        Mockito.when(processingArguments.getLimitXxxOfCombinations()).thenReturn(1);
        List<FuzzingData> dataList = setupFuzzingData("/mfm/v1/services/", "src/test/resources/issue86.json");
        Assertions.assertThat(dataList).hasSize(2);
    }

    @Test
    void shouldGenerateValidResponseForOneOfNestedCombinations() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/api/groops/{groopId}/StartGroopitPaging", "src/test/resources/nswag_gen_oneof.json");
        Assertions.assertThat(dataList).hasSize(1);
        FuzzingData firstData = dataList.getFirst();
        Assertions.assertThat(firstData.getPayload()).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF");
        Assertions.assertThat(firstData.getResponses().get("200")).hasSize(9);
        String firstResponse = firstData.getResponses().get("200").getFirst();
        Assertions.assertThat(firstResponse).doesNotContain("ANY_OF", "ONE_OF", "ALL_OF");
    }

    @Test
    void shouldGenerateValidDataWhenPrimitiveArrays() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/pets", "src/test/resources/issue94.json");
        Assertions.assertThat(dataList).hasSize(2);
        FuzzingData firstData = dataList.getFirst();
        Map<String, List<String>> responses = firstData.getResponses();
        Assertions.assertThat(responses).hasSize(2);
        String defaultResponses = responses.get("default").getFirst();
        boolean isArray = JsonUtils.isArray(defaultResponses, "$.code");
        Assertions.assertThat(isArray).isTrue();
    }

    @Test
    void shouldProperlyGenerateArraysWhenElementsUsingXXXOf() throws Exception {
        List<FuzzingData> data = setupFuzzingData("/path2", "src/test/resources/oneOf_with_base_class.yml");
        Assertions.assertThat(data).hasSize(2);
        String payload = data.get(1).getPayload();

        Object variable = JsonUtils.getVariableFromJson(payload, "$.payloads[0].payloads");
        Assertions.assertThat(variable).hasToString("NOT_SET");

        Object firstElementType = JsonUtils.getVariableFromJson(payload, "$.payloads[0].type");
        Assertions.assertThat(firstElementType).hasToString("Address");
    }

    @Test
    void shouldGetPathItemFromReference() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/reference-pets", "src/test/resources/petstore.yml", false);
        Assertions.assertThat(dataList).hasSize(1);
        FuzzingData data = dataList.getFirst();
        Assertions.assertThat(data.getMethod()).isEqualByComparingTo(HttpMethod.GET);
        Assertions.assertThat(data.getPayload()).contains("id", "page");
    }

    @Test
    void shouldThrowExceptionWhenSchemeDoesNotExist() {
        Assertions.assertThatThrownBy(() -> setupFuzzingData("/pet-types", "src/test/resources/petstore-no-schema.yml")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldGetAllFieldsWhenSchemaDoesNotExist() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/rest/1/pdf/", "src/test/resources/issue98.json");
        Assertions.assertThat(dataList).hasSize(1);
        FuzzingData data = dataList.getFirst();
        Set<CatsField> fields = data.getAllFieldsAsCatsFields();
        Assertions.assertThat(fields).hasSize(4);
    }

    @Test
    void shouldResolveWhenOpenApiHasMalformedAllOfSchemas() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/arns", "src/test/resources/petstore.yml");
        Assertions.assertThat(dataList).hasSize(1);
        String payload = dataList.getFirst().getPayload();
        int parametersArraySize = Integer.parseInt(JsonUtils.getVariableFromJson(payload, "$.Parameters.StringParameters.length()").toString());
        Object arn = JsonUtils.getVariableFromJson(payload, "$.SourceEntity.Arn");
        Object name = JsonUtils.getVariableFromJson(payload, "$.Name");

        Assertions.assertThat(parametersArraySize).isEqualTo(2);
        Assertions.assertThat(arn).isNotEqualTo("NOT_SET");
        Assertions.assertThat(name).isNotEqualTo("NOT_SET");
    }


    @Test
    void shouldDetectCyclicDependenciesWhenPropertiesNamesDontMatch() throws Exception {
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(1);

        List<FuzzingData> dataList = setupFuzzingData("/pets", "src/test/resources/issue117.json");
        Assertions.assertThat(dataList).hasSize(2);
        FuzzingData data = dataList.getFirst();
        String var1 = String.valueOf(JsonUtils.getVariableFromJson(data.getPayload(), "$.credentialSource.updatedBy.credentialSource"));
        Object var2 = JsonUtils.getVariableFromJson(data.getPayload(), "$.credentialSource.updatedBy.credentialSource.updatedBy");
        Assertions.assertThat(var1).isNotEqualTo("NOT_SET");
        Assertions.assertThat(var2).hasToString("NOT_SET");
    }

    @Test
    void shouldConsiderSelfReferenceDepthWhenDetectingCyclicDependencies() throws Exception {
        Mockito.when(processingArguments.getSelfReferenceDepth()).thenReturn(6);
        List<FuzzingData> dataList = setupFuzzingData("/pets", "src/test/resources/issue117.json");
        Assertions.assertThat(dataList).hasSize(2);
        FuzzingData data = dataList.getFirst();
        String var1 = String.valueOf(JsonUtils.getVariableFromJson(data.getPayload(), "$.credentialSource.updatedBy.credentialSource.updatedBy.credentialSource.updatedBy.credentialSource"));
        Object var2 = JsonUtils.getVariableFromJson(data.getPayload(), "$.credentialSource.updatedBy.credentialSource.updatedBy.credentialSource.updatedBy.credentialSource.updatedBy.credentialSource");
        Assertions.assertThat(var1).isNotEqualTo("NOT_SET");
        Assertions.assertThat(var2).hasToString("NOT_SET");
    }

    @Test
    void shouldGenerateOpenApi31Specs() throws Exception {
        System.getProperties().setProperty("bind-type", "true");
        List<FuzzingData> dataList = setupFuzzingData("/pet", "src/test/resources/petstore31.yaml");
        Assertions.assertThat(dataList).hasSize(2);
        FuzzingData data = dataList.getFirst();
        List<String> fields = data.getAllFieldsAsCatsFields().stream().map(CatsField::getName).toList();
        Assertions.assertThat(fields).containsExactly("status",
                "legs",
                "tags",
                "photoUrls",
                "name",
                "id",
                "category#name",
                "category",
                "age",
                "category#id",
                "residency");
    }

    @Test
    void shouldRecordErrorWhenParamRefDoesNotExist() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/pet-types", "src/test/resources/petstore.yml");
        Assertions.assertThat(dataList).hasSize(2);

        Parameter param1 = new Parameter();
        param1.set$ref("#/components/parameters/param1");
        Assertions.assertThat(catsGlobalContext.getRecordedErrors()).isEmpty();

        List<Parameter> resolvedParameters = fuzzingDataFactory.getResolvedParameters(List.of(param1));
        Assertions.assertThat(resolvedParameters).isEmpty();
        Assertions.assertThat(catsGlobalContext.getRecordedErrors()).hasSize(1);
    }

    @Test
    void shouldGenerateHeadersWhenParamsHaveContentType() throws Exception {
        List<FuzzingData> dataList = setupFuzzingData("/pets", "src/test/resources/petstore.yml");
        Assertions.assertThat(dataList).hasSize(3);

        FuzzingData getData = dataList.stream().filter(data -> data.getMethod() == HttpMethod.GET).findFirst().orElseThrow();
        Assertions.assertThat(getData.getHeaders()).hasSize(2);

        List<CatsHeader> metadata = getData.getHeaders().stream().filter(header -> header.getName().equals("x-metadata")).toList();
        Assertions.assertThat(metadata).hasSize(1);
        Assertions.assertThat(metadata.getFirst().getName()).isEqualTo("x-metadata");
        Assertions.assertThat(metadata.getFirst().getValue()).contains("red", "green", "blue");

        List<CatsHeader> badConstraints = getData.getHeaders().stream().filter(header -> header.getName().equals("Bad-Constraints")).toList();
        Assertions.assertThat(badConstraints).hasSize(1);
        Assertions.assertThat(badConstraints.getFirst().getName()).isEqualTo("Bad-Constraints");
        Assertions.assertThat(badConstraints.getFirst().getValue()).isEqualTo("changeOrSimplifyThePattern");
    }

    @Test
    void shouldExtractMultiLevelExamples() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode exampleMapObject = objectMapper.createObjectNode();
        exampleMapObject.set("WORKER_COMPENSATION_PAYRATE_POST_PATCH", objectMapper.createObjectNode().put("value", "catsIsCool"));
        catsGlobalContext.getExampleMap().put("JSON_WORKER_EXAMPLES", new Example().value(exampleMapObject));
        MediaType mediaType = new MediaType();
        mediaType.setExamples(Map.of("example1", new Example().$ref("#/components/examples/JSON_WORKER_EXAMPLES/value/WORKER_COMPENSATION_PAYRATE_POST_PATCH"), "example2", new Example().value("example2")));

        Set<Object> examples = fuzzingDataFactory.extractExamples(mediaType);

        Assertions.assertThat(examples).hasSize(2).containsExactly("example2", "\"catsIsCool\"");
    }

    @Test
    void shouldHaveContentWhenContentNotNull() {
        Operation operation = new Operation();
        RequestBody requestBody = new RequestBody();
        requestBody.setContent(new Content());
        operation.setRequestBody(requestBody);

        Assertions.assertThat(FuzzingDataFactory.hasContent(operation)).isTrue();
    }

    @Test
    void shouldNotHaveContentWhenContentIsNull() {
        Operation operation = new Operation();
        RequestBody requestBody = new RequestBody();
        operation.setRequestBody(requestBody);

        Assertions.assertThat(FuzzingDataFactory.hasContent(operation)).isFalse();
    }

    @Test
    void shouldNotHaveContentWhenRequestBodyIsNull() {
        Operation operation = new Operation();

        Assertions.assertThat(FuzzingDataFactory.hasContent(operation)).isFalse();
    }
}
