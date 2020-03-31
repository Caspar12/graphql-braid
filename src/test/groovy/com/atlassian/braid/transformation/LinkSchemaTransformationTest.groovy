package com.atlassian.braid.transformation


import com.atlassian.braid.Link
import com.atlassian.braid.SchemaNamespace
import com.atlassian.braid.SchemaSource
import com.atlassian.braid.TestUtil
import graphql.language.AstPrinter
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeDefinition
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeDefinitionRegistry
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

class LinkSchemaTransformationTest {


    @Test
    void "don't change schema if Link says so"() {

        SchemaNamespace schemaNamespace = SchemaNamespace.of("Foo")

        def schemaSource = mock(SchemaSource)
        TypeDefinitionRegistry publicSchema = TestUtil.typeRegistry("""

            type Query {
                fooById(id: String): [Foo!]!
            }
            
            type Foo {
                subFoo: [Foo!]
            }
        """)
        TypeDefinitionRegistry privateSchema = TestUtil.typeRegistry("""

            type Query {
                fooById(id: String): [Foo!]
            }
            
            type Foo {
                subFoo: String
            }
        """)
        when(schemaSource.getPrivateSchema()).thenReturn(privateSchema)
        when(schemaSource.getSchema()).thenReturn(publicSchema)
        Link link = Link.newLink()
                .newFieldName("subFoo")
                .sourceType("Foo")
                .targetType("Foo")
                .sourceInputFieldName("subFoo")
                .sourceNamespace(schemaNamespace)
                .targetNamespace(schemaNamespace)
                .topLevelQueryField("fooById")
                .queryArgumentName("id")
                .noSchemaChangeNeeded(true)
                .build()

        when(schemaSource.getLinks()).thenReturn(Arrays.asList(link))

        RuntimeWiring.Builder runtimeWiringBuilder = mock(RuntimeWiring.Builder)
        ObjectTypeDefinition queryObjectTypeDefinition = publicSchema.getType("Query").get()

        def braidSchemaSource = new BraidSchemaSource(schemaSource)
        Map<SchemaNamespace, BraidSchemaSource> dataSources = [(schemaNamespace): braidSchemaSource]
        BraidingContext braidingContext = new BraidingContext(
                dataSources,
                schemaSource.getSchema(),
                runtimeWiringBuilder,
                queryObjectTypeDefinition,
                null,
                null
        )
        def linkSchemaTransformation = new LinkSchemaTransformation()
        linkSchemaTransformation.transform(braidingContext)

        def fooType = (ObjectTypeDefinition) publicSchema.getType("Foo").get()
        def subFooFieldDefinition = (FieldDefinition) fooType.getFieldDefinitions()[0]
        String subFooType = AstPrinter.printAst(subFooFieldDefinition.getType())
        assertThat(subFooType).isEqualTo("[Foo!]")

    }

    @Test
    void "remove input field and add newField"() {

        SchemaNamespace schemaNamespace = SchemaNamespace.of("Foo")

        def schemaSource = mock(SchemaSource)
        TypeDefinitionRegistry schema = TestUtil.typeRegistry("""

            type Query {
                fooById(id: String): Foo
            }
            
            type Foo {
                subFooId: String
            }
        """)
        when(schemaSource.getPrivateSchema()).thenReturn(schema)
        when(schemaSource.getSchema()).thenReturn(schema)
        Link link = Link.newLink()
                .newFieldName("subFoo")
                .sourceType("Foo")
                .targetType("Foo")
                .sourceInputFieldName("subFooId")
                .sourceNamespace(schemaNamespace)
                .targetNamespace(schemaNamespace)
                .topLevelQueryField("fooById")
                .queryArgumentName("id")
                .removeInputField(true)
                .build()

        when(schemaSource.getLinks()).thenReturn(Arrays.asList(link))

        RuntimeWiring.Builder runtimeWiringBuilder = mock(RuntimeWiring.Builder)
        ObjectTypeDefinition queryObjectTypeDefinition = schema.getType("Query").get()

        def braidSchemaSource = new BraidSchemaSource(schemaSource)
        Map<SchemaNamespace, BraidSchemaSource> dataSources = [(schemaNamespace): braidSchemaSource]
        BraidingContext braidingContext = new BraidingContext(
                dataSources,
                schemaSource.getSchema(),
                runtimeWiringBuilder,
                queryObjectTypeDefinition,
                null,
                null
        )
        def linkSchemaTransformation = new LinkSchemaTransformation()
        linkSchemaTransformation.transform(braidingContext)

        def fooType = (ObjectTypeDefinition) schema.getType("Foo").get()
        assertThat(fooType.getFieldDefinitions().size()).is(1)

        def subFooFieldDefinition = (FieldDefinition) fooType.getFieldDefinitions()[0]
        assertThat(subFooFieldDefinition.getName()).isEqualTo("subFoo")

        String subFooType = AstPrinter.printAst(subFooFieldDefinition.getType())
        assertThat(subFooType).isEqualTo("Foo")

    }

    @Test
    void "add newField"() {

        SchemaNamespace schemaNamespace = SchemaNamespace.of("Foo")

        def schemaSource = mock(SchemaSource)
        TypeDefinitionRegistry schema = TestUtil.typeRegistry("""

            type Query {
                fooById(id: String): Foo
            }
            
            type Foo {
                subFooId: String
            }
        """)
        when(schemaSource.getPrivateSchema()).thenReturn(schema)
        when(schemaSource.getSchema()).thenReturn(schema)
        Link link = Link.newLink()
                .newFieldName("subFoo")
                .sourceType("Foo")
                .targetType("Foo")
                .sourceInputFieldName("subFooId")
                .sourceNamespace(schemaNamespace)
                .targetNamespace(schemaNamespace)
                .topLevelQueryField("fooById")
                .queryArgumentName("id")
                .build()

        when(schemaSource.getLinks()).thenReturn(Arrays.asList(link))

        RuntimeWiring.Builder runtimeWiringBuilder = mock(RuntimeWiring.Builder)
        ObjectTypeDefinition queryObjectTypeDefinition = schema.getType("Query").get()

        def braidSchemaSource = new BraidSchemaSource(schemaSource)
        Map<SchemaNamespace, BraidSchemaSource> dataSources = [(schemaNamespace): braidSchemaSource]
        BraidingContext braidingContext = new BraidingContext(
                dataSources,
                schemaSource.getSchema(),
                runtimeWiringBuilder,
                queryObjectTypeDefinition,
                null,
                null
        )
        def linkSchemaTransformation = new LinkSchemaTransformation()
        linkSchemaTransformation.transform(braidingContext)

        def fooType = (ObjectTypeDefinition) schema.getType("Foo").get()
        assertThat(fooType.getFieldDefinitions().size()).is(2)

        def subFooIdFieldDefinition = (FieldDefinition) fooType.getFieldDefinitions()[0]
        assertThat(subFooIdFieldDefinition.getName()).isEqualTo("subFooId")

        def subFooFieldDefinition = (FieldDefinition) fooType.getFieldDefinitions()[1]
        assertThat(subFooFieldDefinition.getName()).isEqualTo("subFoo")

        String subFooType = AstPrinter.printAst(subFooFieldDefinition.getType())
        assertThat(subFooType).isEqualTo("Foo")

    }
}
