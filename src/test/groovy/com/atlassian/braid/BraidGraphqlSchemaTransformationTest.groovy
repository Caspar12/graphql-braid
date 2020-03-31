package com.atlassian.braid

import graphql.ExecutionInput
import graphql.schema.GraphQLSchema
import graphql.schema.idl.TypeDefinitionRegistry
import org.dataloader.BatchLoader
import org.junit.Test
import org.mockito.Mockito

import java.util.function.Function

import static org.hamcrest.CoreMatchers.equalTo
import static org.junit.Assert.assertThat
import static org.mockito.ArgumentMatchers.isNull
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

class BraidGraphqlSchemaTransformationTest {

    @Test
    void "can transform the schema before execution"() throws Exception {

        def registry = TestUtil.typeRegistry("""
              type Query {
                foo: String
            } 
            schema {
                query: Query 
            }
        """)

        SchemaSource schemaSource = mockSchemaSource(registry)

        def count = 0
        Function<GraphQLSchema, GraphQLSchema> countingTransformer = { gqlSchema -> count++
            gqlSchema
        }
        def braid = Braid.builder().schemaSource(schemaSource).build()

        def query = ExecutionInput.newExecutionInput().query("{ foo }").build()
        braid.newGraphQL(countingTransformer).execute(query)
        assertThat(count, equalTo(1))

        braid.newGraphQL(countingTransformer).execute(query)
        assertThat(count, equalTo(2))

        braid.newGraphQL(countingTransformer).execute(query)
        assertThat(count, equalTo(3))

    }

    private SchemaSource mockSchemaSource(TypeDefinitionRegistry registry) {
        SchemaSource schemaSource = mock(SchemaSource.class)
        when(schemaSource.getNamespace()).thenReturn(SchemaNamespace.of("foo"))
        when(schemaSource.getSchema()).thenReturn(registry)
        when(schemaSource.newBatchLoader(Mockito.any(), Mockito.any(), isNull())).thenReturn(mock(BatchLoader.class))
        schemaSource
    }
}
