package com.atlassian.braid.source


import com.fasterxml.jackson.databind.ObjectMapper
import graphql.introspection.IntrospectionQuery
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import org.junit.Test

import static graphql.GraphQL.newGraphQL
import static junit.framework.Assert.assertNotNull

class SchemaUtilsTest {

    def schemaIdl = """
        schema {
          query: Query
        }
        type Query {
          foo(id: String) : Foo
        }
        type Foo {
          id: String
          name: String
          bar: String
        } """

    @Test
    void schemaIdl() {
        def source = SchemaUtils.loadSchema(SchemaLoader.Type.IDL, new StringReader(schemaIdl))

        assertNotNull(source.getType("Foo"))
    }

    @Test
    void introspectionDoc() {
        def source = SchemaUtils.loadSchema(SchemaLoader.Type.IDL, new StringReader(schemaIdl))
        def schema = new SchemaGenerator().makeExecutableSchema(source, RuntimeWiring.newRuntimeWiring().build())
        def graphql = newGraphQL(schema).build()
        def result = graphql.execute(IntrospectionQuery.INTROSPECTION_QUERY)
        def introspectionAsString = new ObjectMapper().writeValueAsString(result.data)

        source = SchemaUtils.loadSchema(SchemaLoader.Type.INTROSPECTION, new StringReader(introspectionAsString))
        assertNotNull(source.getType("Foo"))
    }
}
