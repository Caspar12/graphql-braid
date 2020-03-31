package com.atlassian.braid;

import com.atlassian.braid.java.util.BraidMaps;
import com.atlassian.braid.java.util.BraidObjects;
import com.atlassian.braid.source.MapGraphQLError;
import com.atlassian.braid.source.Query;
import com.atlassian.braid.source.QueryExecutorSchemaSource;
import com.google.common.base.Supplier;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.execution.DataFetcherResult;
import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.idl.SchemaPrinter;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.atlassian.braid.Util.read;
import static com.atlassian.braid.graphql.language.GraphQLNodes.printNode;
import static com.atlassian.braid.java.util.BraidObjects.cast;
import static com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceBuilder.buildDocumentMapperFactory;
import static com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceBuilder.buildExtensions;
import static com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceBuilder.buildLinks;
import static com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceBuilder.buildMutationAliases;
import static com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceBuilder.buildQueryFieldRenames;
import static com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceBuilder.buildSchemaNamespace;
import static com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceBuilder.buildSchemaLoader;
import static com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceBuilder.buildTypeRenames;
import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Suppliers.memoize;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Executes a test by using the test name to find a yml file containing all the information to execute and test a
 * graphql scenario
 */
public class YamlBraidExecutionRule implements MethodRule {

    @SuppressWarnings("WeakerAccess")
    public ExecutionResult executionResult = null;

    public Braid braid = null;

    @Override
    public Statement apply(Statement base, FrameworkMethod method, Object target) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    TestConfiguration config = loadFromYaml(getYamlPath(method));

                    braid = Braid.builder()
                            .withRuntimeWiring(rwb -> {
                                rwb.type("Fooable", wiring -> wiring.typeResolver(__ -> null));
                                rwb.type("FooNamed", wiring -> wiring.typeResolver(__ -> null));
                                rwb.type("BarNamed", wiring -> wiring.typeResolver(__ -> null));
                            })
                            .schemaSources(config.getSchemaSources())
                            .build();

                    final TestQuery request = config.getRequest();

                    ExecutionInput.Builder executionInputBuilder = ExecutionInput.newExecutionInput()
                            .query(request.getQuery())
                            .variables(request.getVariables());

                    request.getOperation().ifPresent(executionInputBuilder::operationName);

                    System.out.println(new SchemaPrinter().print(braid.getSchema()));

                    executionResult = braid.newGraphQL().execute(executionInputBuilder.build()).join();

                    Map<String, Object> response = config.getResponse();

                    assertEquals(response.get("errors"), toSpecification(executionResult.getErrors()));
                    assertEquals(response.get("data"), executionResult.<Map<String, Object>>getData());

                    base.evaluate();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    private static Function<Query, Object> mapInputToResult(Map<String, Object> m) {
        Supplier<Queue<TestQuery>> expectedSupplier = memoize(() -> parseAsQueue(m, "expected", TestQuery::new));
        Supplier<Queue<TestResponse>> responseSupplier = memoize(() -> parseAsQueue(m, "response", TestResponse::new));
        return input -> {
            try {
                final TestQuery expected = expectedSupplier.get().poll();
                if (expected == null) {
                    throw new IllegalArgumentException(m + " shouldn't have been called");
                }

                assertThat(QueryAssertion.from(input)).isEqualTo(QueryAssertion.from(expected));

                return responseSupplier.get().poll().getResult();
            } catch (Throwable e) {
                // necessary to make sure assertion error show in the JUnit output, otherwise they're kinda swallowed
                // by the futures and GraphQL java (since they're Errors and not Exception
                throw new RuntimeException(e);
            }
        };
    }

    private static String printQuery(String query) {
        return printQuery(new Parser().parseDocument(query));
    }

    private static String printQuery(Document query) {
        try {
            return printNode(query);
        } catch (Exception e) {
            throw new IllegalStateException("Exception while printing query:\n" + query + "\n", e);
        }
    }

    private List<Map<String, Object>> toSpecification(List<GraphQLError> errors) {
        return errors.stream().map(GraphQLError::toSpecification).collect(toList());
    }

    private static TestConfiguration loadFromYaml(String path) throws IOException {
        return new TestConfiguration(loadYamlAsMap(path));
    }

    private static Map<String, Object> loadYamlAsMap(String path) throws IOException {
        return BraidObjects.cast(new Yaml().loadAs(read(path), Map.class));
    }

    private static String getYamlPath(FrameworkMethod method) {
        return method.getName() + ".yml";
    }

    private static class TestConfiguration {

        private final Map<String, Object> configMap;

        private TestConfiguration(Map<String, Object> configMap) {
            this.configMap = requireNonNull(configMap);
        }

        TestQuery getRequest() {
            return BraidMaps.get(configMap, "request")
                    .map(BraidObjects::<Map<String, Object>>cast)
                    .map(TestQuery::new)
                    .orElse(null);
        }

        List<SchemaSource> getSchemaSources() {
            return BraidMaps.get(configMap, "schemaSources")
                    .map(BraidObjects::<List<Map<String, Object>>>cast)
                    .map(sources -> sources.stream()
                            .map(m -> QueryExecutorSchemaSource.builder()
                                    .namespace(buildSchemaNamespace(m))
                                    .schemaLoader(buildSchemaLoader(m))
                                    .localRetriever(mapInputToResult(m))
                                    .links(buildLinks(m))
                                    .extensions(buildExtensions(m))
                                    .queryFieldRenames(buildQueryFieldRenames(m))
                                    .mutationFieldRenames(buildMutationAliases(m))
                                    .typeRenames(buildTypeRenames(m))
                                    .documentMapperFactory(buildDocumentMapperFactory(m))
                                    .build())
                            .collect(Collectors.<SchemaSource>toList()))
                    .orElse(emptyList());
        }

        Map<String, Object> getResponse() {
            return BraidMaps.get(configMap, "response")
                    .map(BraidObjects::<Map<String, Object>>cast)
                    .orElse(null);
        }
    }

    private static class TestQuery {
        private final Map<String, Object> requestMap;

        private TestQuery(Map<String, Object> requestMap) {
            this.requestMap = requireNonNull(requestMap);
        }

        String getQuery() {
            return (String) requestMap.get("query");
        }

        Map<String, Object> getVariables() {
            return BraidMaps.get(requestMap, "variables").map(BraidObjects::<Map<String, Object>>cast).orElse(null);
        }

        Optional<String> getOperation() {
            return Optional.ofNullable(requestMap.get("operation")).map(String.class::cast);
        }
    }

    private static <T> List<T> asList(Object o) {
        return o instanceof List ? cast(o) : singletonList(cast(o));
    }

    private static <T> Queue<T> parseAsQueue(Map<String, Object> map, String key,
                                             Function<Map<String, Object>, T> transform) {
        return new LinkedList<>(BraidMaps.get(map, key)
                .map(YamlBraidExecutionRule::<Map<String, Object>>asList)
                .map(l -> l.stream().map(transform).collect(toList()))
                .orElse(emptyList()));
    }

    private static class TestResponse {
        private final Map<String, Object> responseMap;

        private TestResponse(Map<String, Object> responseMap) {
            this.responseMap = requireNonNull(responseMap);
        }

        Map<String, Object> getData() {
            return BraidMaps.get(responseMap, "data")
                    .map(BraidObjects::<Map<String, Object>>cast)
                    .orElse(null);
        }

        List<Map<String, Object>> getErrors() {
            return BraidMaps.get(responseMap, "errors")
                    .map(BraidObjects::<List<Map<String, Object>>>cast)
                    .orElse(emptyList());
        }

        private List<GraphQLError> getGraphQLErrors() {
            return getErrors().stream().map(MapGraphQLError::new).collect(toList());
        }

        DataFetcherResult<Map<String, Object>> getResult() {
            return new DataFetcherResult<>(this.getData(), this.getGraphQLErrors());
        }
    }

    private static class QueryAssertion {
        private final String query;
        private final Map<String, Object> variables;
        private final String operationName;

        private QueryAssertion(String query, Map<String, Object> variables, String operationName) {
            this.query = query;
            this.variables = variables == null ? emptyMap() : variables;
            this.operationName = operationName;
        }

        static QueryAssertion from(Query input) {
            return new QueryAssertion(printQuery(input.getQuery()), input.getVariables(), input.getOperationName());
        }

        static QueryAssertion from(TestQuery testQuery) {
            return new QueryAssertion(printQuery(testQuery.getQuery()), testQuery.getVariables(), testQuery.getOperation().orElse(null));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            QueryAssertion that = (QueryAssertion) o;
            return Objects.equals(query, that.query) &&
                    Objects.equals(variables, that.variables) &&
                    // operation name is check only if both are non-null
                    (operationName == null || that.operationName == null
                            || Objects.equals(operationName, that.operationName));
        }

        @Override
        public int hashCode() {
            return Objects.hash(query, variables, operationName);
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                    .add("query", query)
                    .add("variables", variables)
                    .add("operationName", operationName)
                    .toString();
        }
    }
}
