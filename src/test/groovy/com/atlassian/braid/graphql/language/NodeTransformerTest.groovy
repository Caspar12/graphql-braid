package com.atlassian.braid.graphql.language


import graphql.language.Document
import graphql.language.FragmentDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.OperationDefinition
import graphql.language.TypeName
import graphql.parser.Parser
import org.junit.Test

import static com.atlassian.braid.TestUtil.parseQuery
import static com.atlassian.braid.TestUtil.typeRegistry
import static com.atlassian.braid.graphql.language.GraphQLNodes.printNode
import static org.assertj.core.api.Java6Assertions.assertThat

class NodeTransformerTest {

    def overallSchemaRegistry = typeRegistry("""
            type Query {
                foo(id: Bar): Foo
                topbar(topBarId: ID) : Bar!
            }
            type Foo {
              name: String
              bar: Bar
              barId: String
            }
            type Bar {
              myId: ID
              title: String
            }
        """)

    def query = parseQuery("""
            query myQuery(\$name: Bar) {
                foo(id: \$name) {
                    name
                    bar {
                     ...barFields
                    }
                }
            }
            
            fragment barFields on Bar {
              title
            }           
        """)

    @Test
    void "using IdentityNodeTransformer to rename types in schema"() {
        def barType = overallSchemaRegistry.getType("Bar").get()
        def transformer = new SchemaTypeRenamingNodeTransformer()
        def bazType = transformer.objectTypeDefinition(barType)
        assertThat(bazType.getName()).isEqualTo("Baz")

        def queryType = transformer.objectTypeDefinition(overallSchemaRegistry.getType("Query").get())
        assertThat(queryType.fieldDefinitions[1].type.type.name).isEqualTo("Baz")
    }

    @Test
    void "using IdentityNodeTransformer to rename types in query"() {
        def transformer = new QueryTypeRenamingNodeTransformer()
        Document doc = transformer.document(query)
        OperationDefinition queryDef = doc.definitions[0]
        assertThat(queryDef.variableDefinitions[0].type.name).isEqualTo("Baz")

        FragmentDefinition fragDef = doc.definitions[1]
        assertThat(fragDef.typeCondition.name).isEqualTo("Baz")
    }

    @Test
    void "IdentityNodeTransformer performs perfect copy of schema"() {
        String originalSchemaStr = this.getClass().getResource( '/starwars.graphqls' ).text

        def originalDoc = new Parser().parseDocument(originalSchemaStr)
        String generatedSchemaStr = printNode(originalDoc)

        def transformer = new QueryTypeRenamingNodeTransformer()
        def newDoc = transformer.document(originalDoc)
        String newSchemaStr = printNode(newDoc)
        assertThat(generatedSchemaStr).isEqualTo(newSchemaStr)

    }

    static class SchemaTypeRenamingNodeTransformer extends NodeTransformer {

        @Override
        TypeName typeName(TypeName node) {
            TypeName newTypeName = super.typeName(node)
            if (node.getName() == "Bar") {
                return newTypeName.transform({b -> b.name("Baz")})
            } else {
                return newTypeName
            }
        }

        @Override
        ObjectTypeDefinition objectTypeDefinition(ObjectTypeDefinition node) {
            ObjectTypeDefinition newType = super.objectTypeDefinition(node)
            if (node.getName() == "Bar") {
                return newType.transform({b -> b.name("Baz")})
            } else {
                return newType
            }
        }
    }

    static class QueryTypeRenamingNodeTransformer extends NodeTransformer {

        @Override
        TypeName typeName(TypeName node) {
            TypeName newTypeName = super.typeName(node)
            if (node.getName() == "Bar") {
                return newTypeName.transform({b -> b.name("Baz")})
            } else {
                return newTypeName
            }
        }
    }


}


