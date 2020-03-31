Changelog for graphql-braid
===============================

(Unreleased)
-------------------

- 

0.23.1 (2019-08-15)
-------------------

- Fix error "interface type <iface> is not present when resolving type <type>" when interface is renamed

0.23.0 (2019-04-02)
-------------------

- Added default wiring for custom scalar types. This should allow a stitching service to include custom scalars from 
  other schemas without having to implement their type coersion unless needed.
- Fixed issue where custom scalars were not included in the TypeDefinitionRegistry when merging schemas which resulted 
  in errors about undefined types.

0.22.0 (2019-01-15)
-------------------

- Breaking: Load schemas via new SchemaLoader instead of a reader supplier
- Support loading local schema source as introspection query result to allow richer type definitions

0.21.0 (2018-12-07)
-------------------

- Breaking change (although fields were used internally only): Removed some deprecated fields from Link class (getSourceField,
 getSourceFromField, isReplaceFromField, getTargetQueryField, getTargetVariableQueryField, getArgumentName). 
- Added support for passing multiple arguments to link from different sources (object fields, field arguments, context).
 To use this feature Links need to be created using Link.newComplexLink() method.
- Fixed issue with variables set by client that ends '100' postfix.

0.20.3 (2018-11-21)
-------------------

- Fix linking within aliased (renamed) types

0.20.2 (2018-11-15)
-------------------

- Make `com.atlassian.braid.transformation.ExtensionSchemaTransformation` data fetchers `null` safe

0.20.1 (2018-11-12)
-------------------

- Add support for nested source keys in Mapper copyMap and copyList

0.20.0 (2018-11-09)
-------------------

- Breaking change (although this feature has not been used so far): Schema transformation function must now be passed 
as an argument of Braid#newGraphQL instead of passing it to the Builder.

0.19.0 (2018-11-08)
-------------------

- Update GraphQL Java to version 11.0
- Breaking change: BraidBuilder.dataLoaderInstrumentationFactory receives an Instrumentation Supplier now
- Breaking change: Removed BraidContexts.copyWithNewContext
- Breaking change: KeyedDataFetchingEnvironment.getFieldTypeInfo is now getExecutionStepInfo and returns an ExecutionStepInfo
- Breaking change (although expected to be used internally only): Braid contexts don't have the DataLoaderRegistry anymore

0.18.2 (2018-11-07)
-------------------

- Added the ability to change the GraphqlSchema object per execution 

0.18.1 (2018-11-05)
-------------------

- Add support for inline fragments for merged types
- New NodeTransformer class for immutabily changing a document

0.18.0 (2018-11-02)
-------------------

- Breaking change: GraphQLRemoteRetriever now takes a Query object instead of ExecutionInput.  Can call 
  Query.toExecutionInput() to get the old object.

0.17.1 (2018-10-16)
-------------------

- Update org.springframework and jackson due known security vulnerabilities

0.17.0 (2018-10-16)
-------------------
- Breaking change: Extend TypeMapper to accept BiFunction as part of copy definition and use that function to accept custom context 
during transforming process. 

0.16.0 (2018-10-11)
-------------------
- Breaking change: Add QueryPartitionFunction to support splitting query based on partition before sending to remote service. 
  Default is to send out in one request.
- Breaking change: Rename TypeAlias to TypeRename and FieldAlias to FieldRename
- Rename Link fields. Not a breaking change, but old methods are deprecated now
- Add the ability for more flexible field renames and linking queries via arbitrary AST rewrites
- Allow for custom SchemaTransformations
- Breaking change: FieldRenames only renames and doesn't filter
- Remove LazyRecursiveDataLoaderDispatcherInstrumentation 
- New noSchemaChangeNeeded Link property to allow for Links without modifying the schema of a data source 
- Fix extension queries without the identifying id field

0.15.0 (2018-09-27)
-------------------

- Breaking change: rename everything related to FieldMutation from *Mutation to *Transformation
- Breaking change: refactor all query execution schema sources and related classes into one class QueryExecutorSchemaSource:
  Migration: Instead of LocalQueryExecutingSchemaSource and GraphqlRemoteSchemaSource there is only QueryExecutorSchemaSource which 
  allows for localQuery and remoteQuery builder arguments depending on the use-case. All other arguments are the same as
  before.
- Breaking change: Rename FieldTransformationInjector into SchemaTransformation
- Fix query rewriting to capture nested fragments properly

0.14.0 (2018-09-13)
-------------------

- Upgrade to GraphQL Java 10.0 

0.13.0 (2018-09-06)
-------------------

- Add experimental support for type extension merging

0.12.1 (2018-09-04)
-------------------

- Add predicates to copy and put operations in Mapper

0.12.0 (2018-08-13)
-------------------

- Add experimental support for top level field aliases
- Add experimental support for aliasing type names
- Breaking change: Changed the BatchLoaderFactory definition
- Breaking change: Changed many schema source constructors as they now use a builder
- Breaking change: 'name' yaml property for a schema source is now 'namespace'

0.11.2 (2018-07-23)
-------------------

- Fix blocking when resolving link source ids

0.11.1 (2018-07-20)
-------------------

- Add possibility to configure the data loader instrumentation on Braid builder
- Re-add (probably temporarily) `LazyRecursiveDataLoaderDispatcherInstrumentation` as an option

0.11.0 (2018-07-17)
-------------------

- Update GraphQL Java to version 9.2
- Remove `LazyRecursiveDataLoaderDispatcherInstrumentation` implementation

0.10.11 (2018-06-26)
-------------------

- Support extensions on errors

0.10.10 (2018-06-11)
-------------------

- Fix missing support for variables in directives

0.10.9 (2018-05-17)
-------------------

- Fix for AliasablePropertyDataFetcher to deal with maps keyed by proeprtyName even when property is aliased

0.10.8 (2018-05-15)
-------------------

- Adding in some null safety to AliasablePropertyDataFetcher, just in case

0.10.7 (2018-05-15)
-------------------

- Change default property DataFetcher to be aware of aliases when source is a Map

0.10.6 (2018-05-08)
-------------------

- Fix support for array arguments for top level fields

0.10.5 (2018-05-04)
-------------------

- Use null-safe `toMap` to collect results 

0.10.4 (2018-05-04)
-------------------

- Updated Javadoc around RestRemoteSchemaSource and related classes, added a test for RestRemoteSchemaSource
- Changed signature of RestRemoteRetriever to reflect the fact that the Context is wrapped in a BraidContext
- Handle requests with multiple named queries and a given operation name 

0.10.3 (2018-04-24)
-------------------

- Handle inline fragments within fragments 

0.10.2 (2018-04-23)
-------------------

- Handle interfaces in fragments 

0.10.1 (2018-04-17)
-------------------
 
- Handle fragments in document mapping

0.10.0 (2018-04-12)
-------------------

- Updates the Braid building and runtime configurations, see the README for more details  
- Support GraphQL fragments when using document mapping


0.9.1 (2018-04-09)
-------------------

- Update GraphQL Java to version 8.0 (from 7.0)
- Update Java Dataloader to version 2.0.2 (from 2.0.1)
- Add new LazyRecursiveDataLoaderDispatcherInstrumentation that optimises batch loading

0.9.0 (2018-03-29)
-------------------

- Add experimental document mapper 
- Remove non-production-worthy HTTP retriever implementations
- Move YAML source loading to its own package

0.8.2 (2018-03-21)
-------------------

- Fix support for not null list resolution

0.8.1 (2018-03-15)
-------------------

- Fix detection of source type if query name is different

0.8.0 (2018-03-15)
-------------------

- Added support for resolving lists of ids
- Added support for non-string ids
- Added support for linking top level fields

0.7.6 (2018-03-15)
-------------------

- Remove `BraidDataLoaderDispatcherInstrumentation` introduced in 0.7.5 

0.7.5 (2018-03-14)
-------------------

- Improved batching when querying linked sources

0.7.4 (2018-03-13)
-------------------

- Do not query linked schema when fields are all query variables  

0.7.3 (2018-03-09)
-------------------

-

0.7.3 (2018-03-09)
-------------------
- Added Link.LinkBuiler.replaceFromField() and Link.isReplaceFromField()

0.7.2 (2018-03-09)
-------------------

- Add mapper `list` and `map` function with predicates

0.7.1 (2018-03-05)
-------------------

- New 'nullable' property on a link to signify whether a link handles nulls or not.  Default is not, which
  is different than the old default of fetching the data from the link target for a null key value.

0.7.0 (2018-02-27)
-------------------

- Breaking change: renamed many schema sources, including RemoteSchemaSource
- Add REST schema source (RestRemoteSchemaSource) for exposing REST resources as GraphQL fields
- Add YAML configuration (YamlRemoteSchemaSourceFactory) for creating REST or GraphQL schema sources
- Add new data mapper library for converting from one map structure into another
- Add support for mutations, with:
   - input object containing variables references
   - weaving of mutation result with other schema

0.6.0 (2018-01-25)
-------------------

- Upgrade to graphql-java 7.0
- SchemaSource instances can now construct their own BatchLoaders.  Useful for local source
  instances that want to load the entities from id in non-graphql ways.
- Ability for a non-exposed fields in a schema source to be the target of a link
- Make the original GraphQL query context available to local schema source
  executions

0.5.0 (2018-01-18)
-------------------

- Add ability to link via a different field on the source type

0.4.9 (2018-01-08)
-------------------

- Fix generics with configuration

0.4.8 (2017-12-11)
-------------------

- Fix missing interfaces when import a schema from an introspection result

0.4.7 (2017-12-08)
-------------------

- Fix broken handling of interfaces in a query

0.4.6 (2017-12-04)
-------------------

- Fix broken __typename support

0.4.5 (2017-11-28)
-------------------

- Fix incorrect/missing query operation name

0.4.4 (2017-11-28)
-------------------

- Fix to not mutate query document fragments

0.4.3 (2017-11-28)
-------------------

-

0.4.2 (2017-11-28)
-------------------

-

0.4.1 (2017-11-28)
-------------------

- Fix release process

0.4.0 (2017-11-28)
-------------------

- Support multiple use of fragments
- Support multiple variables in different areas of a query
- New config builder
- Clean up functional tests

0.3.1 (2017-11-20)
-------------------

- Fix missing deployment artifact

0.3.0 (2017-11-20)
-------------------

- Initial release




