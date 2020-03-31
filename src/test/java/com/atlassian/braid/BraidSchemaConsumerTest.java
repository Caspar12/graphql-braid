package com.atlassian.braid;

import com.atlassian.braid.source.Query;
import com.atlassian.braid.source.QueryExecutorSchemaSource;
import com.atlassian.braid.source.SchemaLoader;
import com.atlassian.braid.source.StringSchemaLoader;
import com.google.common.collect.ImmutableMap;
import graphql.ExecutionResult;
import graphql.execution.DataFetcherResult;
import graphql.parser.Parser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.BatchLoader;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.atlassian.braid.Util.getResourceAsReader;
import static com.atlassian.braid.Util.parseRegistry;
import static com.atlassian.braid.Util.read;
import static com.atlassian.braid.source.Query.newQuery;
import static graphql.ExecutionInput.newExecutionInput;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;


@SuppressWarnings("unchecked")
public class BraidSchemaConsumerTest {

    private static final SchemaNamespace FOO = SchemaNamespace.of("foo");

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Function<Query, Object> queryExecutor;

    @Test
    public void testBraidWithExistingTypes() throws IOException {
        final TypeDefinitionRegistry existingRegistry = parseRegistry("/com/atlassian/braid/existing.graphql");

        Braid braid = Braid.builder()
                .typeDefinitionRegistry(existingRegistry)
                .schemaSource(QueryExecutorSchemaSource.builder()
                    .namespace(FOO)
                        .schemaLoader(new StringSchemaLoader(SchemaLoader.Type.IDL, read("/com/atlassian/braid/foo.graphql")))
                        .localRetriever(queryExecutor)
                        .build())
                .build();

        final BraidGraphQL graphql = braid.newGraphQL();

        String query = "{ foo(id: \"fooid\") { id, name } }";

        final Object context = new Object();

        Query fooInput = newQuery()
                .query(new Parser().parseDocument("query Bulk_Foo {\n" +
                        "  foo100: foo(id: \"fooid\") {\n" +
                        "    id\n" +
                        "    name\n" +
                        "  }\n" +
                        "}\n"))
                .operationName("Bulk_Foo")
                .context(context)
                .build();

        when(queryExecutor.apply(argThat(matchesInput(fooInput))))
                .thenReturn(singletonMap("foo100", ImmutableMap.of("id", "fooid", "name", "Foo")));

        final ExecutionResult result = graphql.execute(newExecutionInput().query(query).context(context).build()).join();

        verify(queryExecutor, times(1)).apply(argThat(matchesInput(fooInput)));
        assertEquals(emptyList(), result.getErrors());

        Map<String, Map<String, Object>> data = result.getData();
        assertEquals(data.get("foo").get("name"), "Foo");
    }

    @Test
    public void testBraidWithLocalTypesAsSchemaSource() {
        final TypeDefinitionRegistry existingRegistry = parseRegistry("/com/atlassian/braid/existing.graphql");
        final TypeDefinitionRegistry fooRegistry = parseRegistry("/com/atlassian/braid/foo.graphql");

        SchemaSource localSource = mock(SchemaSource.class, withSettings().extraInterfaces(BatchLoaderFactory.class));
        when(localSource.getNamespace()).thenReturn(FOO);
        when(localSource.getSchema()).thenReturn(fooRegistry);
        BatchLoader loader = mock(BatchLoader.class);
        when(loader.load(any())).thenReturn(CompletableFuture.completedFuture(
                singletonList(new DataFetcherResult(
                        ImmutableMap.of("id", "fooid", "name", "Foo"),
                        emptyList()))));
        when(localSource.newBatchLoader(any(), any(), isNull())).thenReturn(loader);


        Braid braid = Braid.builder()
                .typeDefinitionRegistry(existingRegistry)
                .schemaSource(localSource)
                .build();

        BraidGraphQL graphql = braid.newGraphQL();

        String query = "{ foo(id: \"fooid\") { id, name } }";

        final ExecutionResult result = graphql.execute(newExecutionInput().query(query).build()).join();

        assertEquals(emptyList(), result.getErrors());

        Map<String, Map<String, Object>> data = result.getData();
        assertEquals(data.get("foo").get("name"), "Foo");
    }

    private static QueryMatcher matchesInput(Query input) {
        return new QueryMatcher(input);
    }

    private static class QueryMatcher implements ArgumentMatcher<Query> {

        private final Query input;

        private QueryMatcher(Query input) {
            this.input = input;
        }

        @Override
        public boolean matches(Query arg) {
            return arg.toString().equals(input.toString());
        }

        @Override
        public String toString() {
            return input.toString();
        }
    }
}
