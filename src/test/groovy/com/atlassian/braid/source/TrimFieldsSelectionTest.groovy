package com.atlassian.braid.source

import com.atlassian.braid.*
import graphql.execution.ExecutionContext
import graphql.language.AstPrinter
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.OperationDefinition
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLType
import org.junit.Test

import static com.atlassian.braid.LinkArgument.ArgumentSource.CONTEXT
import static com.atlassian.braid.LinkArgument.ArgumentSource.OBJECT_FIELD
import static com.atlassian.braid.TestUtil.parseQuery
import static com.atlassian.braid.TestUtil.typeRegistry
import static java.util.Collections.emptyList
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

class TrimFieldsSelectionTest {

    def overallSchemaRegistry = typeRegistry("""
            type Query {
                foo: Foo
                topbar(topBarId: ID) : Bar
            }
            type Foo {
              name: String
              bar: Bar
              barId: String
              bar2: Bar
              complexBar: Bar
            }
            type Bar {
              myId: ID
              title: String
            }
        """)

    def overallSchema = TestUtil.schema(overallSchemaRegistry)

    def innerSchema1 = typeRegistry("""
             type Query {
                  topbar(topBarId: String) : Bar
                  topbarmulti(bar1: String, bar2: String, otherParam: ID!) : Bar
              }
              type Bar {
                  myId: ID
                  title: String
              }
        """)

    // not used at the moment, but provided for claritiy and future tests
    def innerSchema2 = typeRegistry("""
          type Query {
              foo: Foo
          }
          type Foo {
              name: String
              barId: String
              bar2: String
          }
        """)

    def namespace1 = SchemaNamespace.of("namespace1")
    def namespace2 = SchemaNamespace.of("namespace2")

    def complexLink = Link
            .newComplexLink()
            .sourceNamespace(namespace1)
            .sourceType("Foo")
            .newFieldName("complexBar")
            .targetNamespace(namespace2)
            .targetType("Bar")
            .topLevelQueryField("topbarmulti")
            .linkArgument(
            LinkArgument.newLinkArgument()
                    .argumentSource(OBJECT_FIELD)
                    .sourceName("barId")
                    .queryArgumentName("bar1")
                    .build())
            .linkArgument(
            LinkArgument.newLinkArgument()
                    .argumentSource(OBJECT_FIELD)
                    .sourceName("bar2")
                    .queryArgumentName("bar2")
                    .build())
            .linkArgument(
            LinkArgument.newLinkArgument()
                    .argumentSource(CONTEXT)
                    .sourceName("ctxParam")
                    .queryArgumentName("otherParam")
                    .build())
            .build()

    @Test
    void "trimming root query with one link"() {
        def schemaSource = mock(SchemaSource)
        def environment = mock(DataFetchingEnvironment)

        def link = Link
                .from(namespace1, "Foo", "bar", "barId")
                .to(namespace2, "Bar", "topbar", "topBarId")
                .build()

        when(schemaSource.getLinks()).thenReturn(Arrays.asList(link))
        when(schemaSource.getSchema()).thenReturn(innerSchema1)

        when(environment.getGraphQLSchema()).thenReturn(overallSchema)
        when(environment.getParentType()).thenReturn(overallSchema.getType("Query") as GraphQLType)
        def context = mock(ExecutionContext)
        when(environment.getExecutionContext()).thenReturn(context)
        when(context.getVariables()).thenReturn(new LinkedHashMap<String, Object>())


        def query = parseQuery("{foo{bar{myId}}}")
        def rootField = (query.definitions[0] as OperationDefinition).selectionSet.selections[0] as Field

        TrimFieldsSelection.trimFieldSelection(schemaSource, environment, rootField, false)
        String expectedQuery = "foo { barId }"
        assertEquals(expectedQuery, AstPrinter.printAstCompact(rootField))
    }

    @Test
    void "trimming root query with link with multiple arguments"() {
        def schemaSource = mock(SchemaSource)
        def environment = mock(DataFetchingEnvironment)

        when(schemaSource.getLinks()).thenReturn(Arrays.asList(complexLink))
        when(schemaSource.getSchema()).thenReturn(innerSchema1)

        when(environment.getGraphQLSchema()).thenReturn(overallSchema)
        when(environment.getParentType()).thenReturn(overallSchema.getType("Query") as GraphQLType)
        def context = mock(ExecutionContext)
        when(environment.getExecutionContext()).thenReturn(context)
        when(context.getVariables()).thenReturn(new LinkedHashMap<String, Object>())


        def query = parseQuery("{foo{complexBar{myId}}}")
        def rootField = (query.definitions[0] as OperationDefinition).selectionSet.selections[0] as Field

        TrimFieldsSelection.trimFieldSelection(schemaSource, environment, rootField, false)
        String expectedQuery = "foo { barId bar2 }"
        assertEquals(expectedQuery, AstPrinter.printAstCompact(rootField))
    }

    @Test
    void "trim returns cloned fragments bases on the modified query"() {
        def schemaSource = mock(SchemaSource)
        def environment = mock(DataFetchingEnvironment)


        def link = Link
                .from(namespace1, "Foo", "bar", "barId")
                .to(namespace2, "Bar", "topbar", "topBarId")
                .build()

        when(schemaSource.getLinks()).thenReturn(Arrays.asList(link))
        when(schemaSource.getSchema()).thenReturn(innerSchema1)

        when(environment.getGraphQLSchema()).thenReturn(overallSchema)
        when(environment.getParentType()).thenReturn(overallSchema.getType("Query") as GraphQLType)
        def context = mock(ExecutionContext)
        when(environment.getExecutionContext()).thenReturn(context)
        when(context.getVariables()).thenReturn(new LinkedHashMap<String, Object>())


        def query = parseQuery("""
            { foo { ...FooFragment  } }
            
            fragment BarFragment on Bar {
                myId
            }                
            fragment FooFragment on Foo {
                bar {
                ...BarFragment
                }
            }
        """)
        def barFragment = (query.definitions[1] as FragmentDefinition)
        def fooFragment = (query.definitions[2] as FragmentDefinition)
        when(environment.getFragmentsByName()).thenReturn([BarFragment: barFragment, FooFragment: fooFragment])
        def rootField = (query.definitions[0] as OperationDefinition).selectionSet.selections[0] as Field

        def referencedFragments = TrimFieldsSelection.trimFieldSelection(schemaSource, environment, rootField, false)
        assertEquals(1, referencedFragments.size())
        assertEquals(fooFragment.name, referencedFragments.get(0).name)
        assertTrue(fooFragment != referencedFragments.get(0))
    }


    @Test
    void "trim returns nested cloned fragments"() {
        def schemaSource = mock(SchemaSource)
        def environment = mock(DataFetchingEnvironment)

        when(schemaSource.getLinks()).thenReturn(emptyList())
        when(schemaSource.getSchema()).thenReturn(innerSchema1)

        when(environment.getGraphQLSchema()).thenReturn(overallSchema)
        when(environment.getParentType()).thenReturn(overallSchema.getType("Query") as GraphQLType)
        def context = mock(ExecutionContext)
        when(environment.getExecutionContext()).thenReturn(context)
        when(context.getVariables()).thenReturn(new LinkedHashMap<String, Object>())


        def query = parseQuery("""
            { foo { ...FooFragment  } }
            
            fragment BarFragment on Bar {
                myId
            }                
            fragment FooFragment on Foo {
                bar {
                ...BarFragment
                }
            }
        """)
        def barFragment = (query.definitions[1] as FragmentDefinition)
        def fooFragment = (query.definitions[2] as FragmentDefinition)
        when(environment.getFragmentsByName()).thenReturn([BarFragment: barFragment, FooFragment: fooFragment])
        def rootField = (query.definitions[0] as OperationDefinition).selectionSet.selections[0] as Field

        def referencedFragments = TrimFieldsSelection.trimFieldSelection(schemaSource, environment, rootField, true)
        assertEquals(2, referencedFragments.size())
        assertEquals(fooFragment.name, referencedFragments.get(0).name)
        assertTrue(fooFragment != referencedFragments.get(0))
        assertEquals(barFragment.name, referencedFragments.get(1).name)
        assertTrue(barFragment != referencedFragments.get(1))
    }

    @Test
    void "__typename field on union type special handling"() {
        def overallSchemaRegistry = typeRegistry("""
             type Query {
                  unionField: XorY
              }
              union XorY = X | Y
              type X{
                id: ID
              }
              type Y{
                id: ID
              }
        """)

        def overallSchema = TestUtil.schema(overallSchemaRegistry)

        def innerSchema1 = typeRegistry("""
             type Query {
                  unionField: XorY
              }
              union XorY = X | Y
              type X{
                id: ID
              }
              type Y{
                id: ID
              }
        """)
        def schemaSource = mock(SchemaSource)
        def environment = mock(DataFetchingEnvironment)

        when(schemaSource.getLinks()).thenReturn(Collections.emptyList())
        when(schemaSource.getSchema()).thenReturn(innerSchema1)

        when(environment.getGraphQLSchema()).thenReturn(overallSchema)
        when(environment.getParentType()).thenReturn(overallSchema.getType("Query") as GraphQLType)
        def context = mock(ExecutionContext)
        when(environment.getExecutionContext()).thenReturn(context)
        when(context.getVariables()).thenReturn(new LinkedHashMap<String, Object>())


        def query = parseQuery("{unionField {__typename}}")
        def rootField = (query.definitions[0] as OperationDefinition).selectionSet.selections[0] as Field

        TrimFieldsSelection.trimFieldSelection(schemaSource, environment, rootField, false)
        String expectedQuery = "unionField { __typename }"
        assertEquals(expectedQuery, AstPrinter.printAstCompact(rootField))

    }


    @Test
    void "removes field duplicates"() {
        def schemaSource = mock(SchemaSource)
        def environment = mock(DataFetchingEnvironment)

        def link = Link.newLink()
                .sourceNamespace(namespace1)
                .sourceType("Foo")
                .sourceInputFieldName("barId")
                .newFieldName("bar")
                .targetNamespace(namespace2)
                .targetType("Bar")
                .topLevelQueryField("topbar")
                .queryArgumentName("tobBarId")
                .build()


        when(schemaSource.getLinks()).thenReturn(Arrays.asList(link))
        when(schemaSource.getSchema()).thenReturn(innerSchema1)

        when(environment.getGraphQLSchema()).thenReturn(overallSchema)
        when(environment.getParentType()).thenReturn(overallSchema.getType("Query") as GraphQLType)
        def context = mock(ExecutionContext)
        when(environment.getExecutionContext()).thenReturn(context)
        when(context.getVariables()).thenReturn(new LinkedHashMap<String, Object>())


        def query = parseQuery("{foo{barId bar{myId} }}")
        def rootField = (query.definitions[0] as OperationDefinition).selectionSet.selections[0] as Field

        TrimFieldsSelection.trimFieldSelection(schemaSource, environment, rootField, false)
        String expectedQuery = "foo { barId }"
        assertEquals(expectedQuery, AstPrinter.printAstCompact(rootField))
    }

    @Test
    void "removes field duplicates in fragments"() {
        def schemaSource = mock(SchemaSource)
        def environment = mock(DataFetchingEnvironment)

        def link = Link
                .from(namespace1, "Foo", "bar", "barId")
                .to(namespace2, "Bar", "topbar", "topBarId")
                .build()

        when(schemaSource.getLinks()).thenReturn(Arrays.asList(link))
        when(schemaSource.getSchema()).thenReturn(innerSchema1)

        when(environment.getGraphQLSchema()).thenReturn(overallSchema)
        when(environment.getParentType()).thenReturn(overallSchema.getType("Query") as GraphQLType)
        def context = mock(ExecutionContext)
        when(environment.getExecutionContext()).thenReturn(context)
        when(context.getVariables()).thenReturn(new LinkedHashMap<String, Object>())


        def query = parseQuery("""
            { foo { ...FooFragment  } }
            
            fragment BarFragment on Bar {
                myId
            }                
            fragment FooFragment on Foo {
                barId
                bar {
                ...BarFragment
                }
            }
        """)
        def barFragment = (query.definitions[1] as FragmentDefinition)
        def fooFragment = (query.definitions[2] as FragmentDefinition)
        when(environment.getFragmentsByName()).thenReturn([BarFragment: barFragment, FooFragment: fooFragment])
        def rootField = (query.definitions[0] as OperationDefinition).selectionSet.selections[0] as Field

        def referencedFragments = TrimFieldsSelection.trimFieldSelection(schemaSource, environment, rootField, false)
        assertEquals(1, referencedFragments.size())

        assertEquals("fragment FooFragment on Foo { barId }", AstPrinter.printAstCompact(referencedFragments.get(0)));
        assertEquals("foo { ...FooFragment }", AstPrinter.printAstCompact(rootField))
    }

    @Test
    void "doesn't remove link that replaces original field"() {
        def schemaSource = mock(SchemaSource)
        def environment = mock(DataFetchingEnvironment)

        def link = Link.newSimpleLink()
                .sourceNamespace(namespace1)
                .sourceType("Foo")
                .sourceInputFieldName("bar2")
                .newFieldName("bar2")
                .targetNamespace(namespace2)
                .targetType("Bar")
                .topLevelQueryField("topbar")
                .queryArgumentName("tobBarId")
                .build()


        when(schemaSource.getLinks()).thenReturn(Arrays.asList(link))
        when(schemaSource.getSchema()).thenReturn(innerSchema1)

        when(environment.getGraphQLSchema()).thenReturn(overallSchema)
        when(environment.getParentType()).thenReturn(overallSchema.getType("Query") as GraphQLType)
        def context = mock(ExecutionContext)
        when(environment.getExecutionContext()).thenReturn(context)
        when(context.getVariables()).thenReturn(new LinkedHashMap<String, Object>())


        def query = parseQuery("{foo{ bar2{myId} }}")
        def rootField = (query.definitions[0] as OperationDefinition).selectionSet.selections[0] as Field

        TrimFieldsSelection.trimFieldSelection(schemaSource, environment, rootField, false)
        String expectedQuery = "foo { bar2 }"
        assertEquals(expectedQuery, AstPrinter.printAstCompact(rootField))
    }

    @Test
    void "doesn't remove field with alias"() {
        def schemaSource = mock(SchemaSource)
        def environment = mock(DataFetchingEnvironment)

        def link = Link.newLink()
                .sourceNamespace(namespace1)
                .sourceType("Foo")
                .sourceInputFieldName("barId")
                .newFieldName("bar")
                .targetNamespace(namespace2)
                .targetType("Bar")
                .topLevelQueryField("topbar")
                .queryArgumentName("tobBarId")
                .build()


        when(schemaSource.getLinks()).thenReturn(Arrays.asList(link))
        when(schemaSource.getSchema()).thenReturn(innerSchema1)

        when(environment.getGraphQLSchema()).thenReturn(overallSchema)
        when(environment.getParentType()).thenReturn(overallSchema.getType("Query") as GraphQLType)
        def context = mock(ExecutionContext)
        when(environment.getExecutionContext()).thenReturn(context)
        when(context.getVariables()).thenReturn(new LinkedHashMap<String, Object>())


        def query = parseQuery("{foo{ myAlias: barId bar{myId} }}")
        def rootField = (query.definitions[0] as OperationDefinition).selectionSet.selections[0] as Field

        TrimFieldsSelection.trimFieldSelection(schemaSource, environment, rootField, false)
        String expectedQuery = "foo { myAlias: barId barId }"
        assertEquals(expectedQuery, AstPrinter.printAstCompact(rootField))
    }


    @Test
    void "duplicate links do not cause duplicates"() {
        def schemaSource = mock(SchemaSource)
        def environment = mock(DataFetchingEnvironment)


        def link = Link.newLink()
                .sourceNamespace(namespace1)
                .sourceType("Foo")
                .sourceInputFieldName("barId")
                .newFieldName("bar")
                .targetNamespace(namespace2)
                .targetType("Bar")
                .topLevelQueryField("topbar")
                .queryArgumentName("tobBarId")
                .build()


        when(schemaSource.getLinks()).thenReturn(Arrays.asList(link, complexLink))
        when(schemaSource.getSchema()).thenReturn(innerSchema1)

        when(environment.getGraphQLSchema()).thenReturn(overallSchema)
        when(environment.getParentType()).thenReturn(overallSchema.getType("Query") as GraphQLType)
        def context = mock(ExecutionContext)
        when(environment.getExecutionContext()).thenReturn(context)
        when(context.getVariables()).thenReturn(new LinkedHashMap<String, Object>())


        def query = parseQuery("{foo{ bar{myId} bar{myId} complexBar{myId} complexBar{myId}}}")
        def rootField = (query.definitions[0] as OperationDefinition).selectionSet.selections[0] as Field

        TrimFieldsSelection.trimFieldSelection(schemaSource, environment, rootField, false)
        String expectedQuery = "foo { barId bar2 }"
        assertEquals(expectedQuery, AstPrinter.printAstCompact(rootField))
    }

}


