package com.atlassian.braid

import graphql.language.ObjectTypeDefinition
import graphql.schema.idl.TypeDefinitionRegistry
import org.dataloader.BatchLoader
import org.hamcrest.core.IsEqual
import org.hamcrest.core.IsNull
import org.junit.Test
import org.mockito.Mockito

import static com.atlassian.braid.TestUtil.mockRuntimeWiringBuilder
import static java.util.Arrays.asList
import static org.hamcrest.CoreMatchers.containsString
import static org.junit.Assert.assertThat
import static org.junit.Assert.fail
import static org.mockito.ArgumentMatchers.any
import static org.mockito.ArgumentMatchers.isNotNull
import static org.mockito.ArgumentMatchers.isNull
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

class BraidSchemaTest {
    private static final SchemaNamespace FOO = SchemaNamespace.of("foo")

    @Test
    void "schema with empty mutation doesn't create a mutation"() {

        def registry = TestUtil.typeRegistry("""
              type Query {
                foo: String
            } 
            schema {
                query: Query 
            }
        """)

        def schemaSourceRegistry = TestUtil.typeRegistry("""
        type Query {
            bar: String
        }
        """)


        SchemaSource schemaSource = mock(SchemaSource.class)
        when(schemaSource.getNamespace()).thenReturn(FOO);
        when(schemaSource.getSchema()).thenReturn(schemaSourceRegistry);
        when(schemaSource.newBatchLoader(Mockito.any(), Mockito.any(), isNull())).thenReturn(mock(BatchLoader.class))

        def schema = BraidSchema.from(registry, mockRuntimeWiringBuilder, asList(schemaSource)).schema

        assertThat(schema.getMutationType(), IsNull.nullValue())
    }

    @Test
    void "schema with mutation created"() {

        def registry = TestUtil.typeRegistry("""
              type Query {
                foo: String
            } 
            schema {
                query: Query 
            }
        """)

        def schemaSourceRegistry = TestUtil.typeRegistry("""
            type Query {
                bar: String
            }
            type Mutation{
                bar: String
            }
            schema {
                query: Query
                mutation: Mutation
            }
        """)


        SchemaSource schemaSource = mock(SchemaSource.class)
        when(schemaSource.getNamespace()).thenReturn(FOO)
        when(schemaSource.getSchema()).thenReturn(schemaSourceRegistry)
        when(schemaSource.newBatchLoader(Mockito.any(), Mockito.any(), isNull())).thenReturn(mock(BatchLoader.class))

        def schema = BraidSchema.from(registry, mockRuntimeWiringBuilder, asList(schemaSource)).schema

        assertThat(schema.getMutationType(), IsNull.notNullValue())
    }

    @Test
    void "implicit schema definition"() {
        def registry = TestUtil.typeRegistry("""
              type Query {
                foo: String
            } 
        """)

        def schemaSourceRegistry = TestUtil.typeRegistry("""
        type Query {
            bar: String
        }
        """)


        SchemaSource schemaSource = mock(SchemaSource.class)
        when(schemaSource.getNamespace()).thenReturn(FOO);
        when(schemaSource.getSchema()).thenReturn(schemaSourceRegistry);
        when(schemaSource.newBatchLoader(Mockito.any(), Mockito.any(), isNull())).thenReturn(mock(BatchLoader.class))

        def schema = BraidSchema.from(registry, mockRuntimeWiringBuilder, asList(schemaSource)).schema

        assertThat(schema.getQueryType(), IsNull.notNullValue())
        assertThat(schema.getMutationType(), IsNull.nullValue())

    }


    @Test
    void "empty base registry"() {
        def emptyRegistry = new TypeDefinitionRegistry();

        def schemaSourceRegistry = TestUtil.typeRegistry("""
        type Query {
            bar: String
        }
        """)


        SchemaSource schemaSource = mock(SchemaSource.class)
        when(schemaSource.getNamespace()).thenReturn(FOO);
        when(schemaSource.getSchema()).thenReturn(schemaSourceRegistry);
        when(schemaSource.newBatchLoader(Mockito.any(), Mockito.any(), isNull())).thenReturn(mock(BatchLoader.class))

        def schema = BraidSchema.from(emptyRegistry, mockRuntimeWiringBuilder, asList(schemaSource)).schema

        assertThat(schema.getQueryType(), IsNull.notNullValue())
        assertThat(schema.getMutationType(), IsNull.nullValue())

    }

    @Test
    void "link's source field existence is validated against private source registry"() {
        def publicRegistry = TestUtil.typeRegistry("""
        type Query {
            foo: Foo
        }
        
        type Foo {
            id: ID!
            # Does not contain barId which is needed for Link
        }
        
        type Bar {
            id: ID!
        }
        """)

        def privateRegistry = TestUtil.typeRegistry("""
        type Query {
            foo: Foo
            barById(id: ID!): Bar
        }
        
        type Foo {
            id: ID!
            barId: ID!
        }
        
        type Bar {
            id: ID!
        }
        """)

        def link = Link.from(FOO, "Foo", "bar", "barId")
                .to(FOO, "Bar", "barById")
                .build()
        SchemaSource schemaSource = mock(SchemaSource.class)
        when(schemaSource.getNamespace()).thenReturn(FOO)
        when(schemaSource.getSchema()).thenReturn(publicRegistry)
        when(schemaSource.getPrivateSchema()).thenReturn(privateRegistry)
        when(schemaSource.getLinks()).thenReturn([link])
        when(schemaSource.newBatchLoader(any(), any(), isNull())).thenReturn(mock(BatchLoader.class))

        def schema = BraidSchema.from(new TypeDefinitionRegistry(), mockRuntimeWiringBuilder, asList(schemaSource)).schema

        def fooType = schema.getObjectType("Foo")
        assertThat(fooType, IsNull.notNullValue())
        assertThat(fooType.getFieldDefinition("barId"), IsNull.nullValue())
        assertThat(fooType.getFieldDefinition("bar")?.type?.name, IsEqual.equalTo("Bar"))

        // Remove barId from type Foo in private schema. This time validation should fail.
        privateRegistry.getType("Foo", ObjectTypeDefinition.class).get().fieldDefinitions
                .removeIf({ it.name == "barId" })
        try {
            BraidSchema.from(new TypeDefinitionRegistry(), mockRuntimeWiringBuilder, asList(schemaSource)).schema
            fail("Expected IllegalArgumentException")
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("Can't find source from field: barId"))
        }
    }

    @Test
    void "link's source type must exist in private schema"() {
        def publicRegistry = TestUtil.typeRegistry("""
        type Query {
            foo: Foo
        }
        
        type Foo {
            id: ID!
        }
        
        type Bar {
            id: ID!
        }
        """)

        def privateRegistry = TestUtil.typeRegistry("""
        type Query {
            barById(id: ID!): Bar
        }
             
        type Bar {
            id: ID!
        }
        """)

        def link = Link.from(FOO, "Foo", "bar", "barId")
                .to(FOO, "Bar", "barById")
                .build()
        SchemaSource schemaSource = mock(SchemaSource.class)
        when(schemaSource.getNamespace()).thenReturn(FOO)
        when(schemaSource.getSchema()).thenReturn(publicRegistry)
        when(schemaSource.getPrivateSchema()).thenReturn(privateRegistry)
        when(schemaSource.getLinks()).thenReturn([link])

        try {
            BraidSchema.from(new TypeDefinitionRegistry(), mockRuntimeWiringBuilder, asList(schemaSource)).schema
            fail("Expected IllegalArgumentException")
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("Can't find source type 'Foo' in private schema for link bar"))
        }
    }

    @Test
    void "directives definitions will be copied over"() {
        def publicRegistry = TestUtil.typeRegistry("""
        directive @arg on ARGUMENT_DEFINITION
        directive @field on FIELD_DEFINITION
        directive @withArg(argument: String) on FIELD | FIELD_DEFINITION
        
        type Query {
            foo(id:ID! @arg): Foo
        }
        
        type Foo {
            id: ID! @field
            name: String @withArg(argument: "test")
        }
       
        """)

        def privateRegistry = TestUtil.typeRegistry("""
        type Query {
             foo(id:ID!): Foo
        }
             
         type Foo {
            id: ID!
            name: String
        }
        
        """)

        SchemaSource schemaSource = mock(SchemaSource.class)
        when(schemaSource.getNamespace()).thenReturn(FOO)
        when(schemaSource.getSchema()).thenReturn(publicRegistry)
        when(schemaSource.getPrivateSchema()).thenReturn(privateRegistry)
        when(schemaSource.newBatchLoader(any(), any(), isNull())).thenReturn(mock(BatchLoader.class))
        def schema =  BraidSchema.from(new TypeDefinitionRegistry(), mockRuntimeWiringBuilder, asList(schemaSource)).schema
        assertThat(schema.getDirective("arg"), IsNull.notNullValue())
        assertThat(schema.getDirective("field"), IsNull.notNullValue())
        assertThat(schema.getDirective("withArg").getArgument("argument"), IsNull.notNullValue())
    }

}