package com.atlassian.braid;

import com.atlassian.braid.graphql.language.AliasablePropertyDataFetcher;
import com.atlassian.braid.transformation.BraidSchemaSource;
import com.atlassian.braid.transformation.BraidTypeDefinition;
import com.atlassian.braid.transformation.BraidingContext;
import com.atlassian.braid.transformation.ExtensionSchemaTransformation;
import com.atlassian.braid.transformation.LinkSchemaTransformation;
import com.atlassian.braid.transformation.SchemaTransformation;
import com.atlassian.braid.transformation.TopLevelSchemaTransformation;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.BatchLoader;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.atlassian.braid.TypeUtils.DEFAULT_MUTATION_TYPE_NAME;
import static com.atlassian.braid.TypeUtils.DEFAULT_QUERY_TYPE_NAME;
import static com.atlassian.braid.TypeUtils.MUTATION_FIELD_NAME;
import static com.atlassian.braid.TypeUtils.QUERY_FIELD_NAME;
import static com.atlassian.braid.TypeUtils.addMutationTypeToSchema;
import static com.atlassian.braid.TypeUtils.addQueryTypeToSchema;
import static com.atlassian.braid.TypeUtils.createDefaultQueryTypeDefinition;
import static com.atlassian.braid.TypeUtils.findMutationType;
import static com.atlassian.braid.TypeUtils.findQueryType;
import static com.atlassian.braid.java.util.BraidCollectors.singleton;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

final class BraidSchema {

    private final GraphQLSchema schema;
    private final Map<String, BatchLoader> batchLoaders;

    // order matters because TopLevelSchemaTransformation overwrites dataFetchers for the query fields which
    // have links
    private static final List<SchemaTransformation> schemaTransformationFactories = asList(
            new LinkSchemaTransformation(),
            new TopLevelSchemaTransformation(),
            new ExtensionSchemaTransformation()
    );

    private BraidSchema(GraphQLSchema schema, Map<String, BatchLoader> batchLoaders) {
        this.schema = requireNonNull(schema);
        this.batchLoaders = requireNonNull(batchLoaders);
    }

    static BraidSchema from(TypeDefinitionRegistry typeDefinitionRegistry,
                            RuntimeWiring.Builder runtimeWiringBuilder,
                            List<SchemaSource> schemaSources) {
        return from(typeDefinitionRegistry,
                runtimeWiringBuilder,
                schemaSources,
                Collections.emptyList(),
                null);
    }

    static BraidSchema from(TypeDefinitionRegistry typeDefinitionRegistry,
                            RuntimeWiring.Builder runtimeWiringBuilder,
                            List<SchemaSource> schemaSources,
                            List<SchemaTransformation> customSchemaTransformations,
                            BatchLoaderEnvironment batchLoaderEnvironment) {

        final Map<SchemaNamespace, BraidSchemaSource> dataSourceTypes = toBraidSchemaSourceMap(schemaSources);

        findSchemaDefinitionOrCreateOne(typeDefinitionRegistry);

        final ObjectTypeDefinition queryObjectTypeDefinition =
                findQueryType(typeDefinitionRegistry)
                        .orElseGet(() -> addQueryTypeToSchema(typeDefinitionRegistry, createDefaultQueryTypeDefinition()));

        final ObjectTypeDefinition mutationObjectTypeDefinition =
                findMutationType(typeDefinitionRegistry)
                        .orElseGet(TypeUtils::createDefaultMutationTypeDefinition);

        final Map<String, BatchLoader> batchLoaders =
                addDataSources(dataSourceTypes, typeDefinitionRegistry, runtimeWiringBuilder, queryObjectTypeDefinition,
                        mutationObjectTypeDefinition, customSchemaTransformations, batchLoaderEnvironment);

        if (!mutationObjectTypeDefinition.getFieldDefinitions().isEmpty()) {
            addMutationTypeToSchema(typeDefinitionRegistry, mutationObjectTypeDefinition);
        }
        final GraphQLSchema graphQLSchema = new SchemaGenerator()
                .makeExecutableSchema(typeDefinitionRegistry, runtimeWiringBuilder.build());

        return new BraidSchema(graphQLSchema, batchLoaders);
    }

    private static void findSchemaDefinitionOrCreateOne(TypeDefinitionRegistry typeDefinitionRegistry) {
        typeDefinitionRegistry.schemaDefinition()
                .orElseGet(() -> createDefaultSchemaDefinition(typeDefinitionRegistry));
    }

    private static SchemaDefinition createDefaultSchemaDefinition(TypeDefinitionRegistry typeDefinitionRegistry) {
        SchemaDefinition.Builder builder = SchemaDefinition.newSchemaDefinition();

        typeDefinitionRegistry.getType(DEFAULT_QUERY_TYPE_NAME)
                .ifPresent(__ -> addOperation(builder, QUERY_FIELD_NAME, DEFAULT_QUERY_TYPE_NAME));

        typeDefinitionRegistry.getType(DEFAULT_MUTATION_TYPE_NAME)
                .ifPresent(__ -> addOperation(builder, MUTATION_FIELD_NAME, DEFAULT_MUTATION_TYPE_NAME));

        SchemaDefinition schemaDefinition = builder.build();

        typeDefinitionRegistry.add(schemaDefinition);
        return schemaDefinition;
    }

    private static void addOperation(SchemaDefinition.Builder schemaDefinition, String queryFieldName, String defaultQueryTypeName) {
        schemaDefinition.operationTypeDefinition(new OperationTypeDefinition(queryFieldName, new TypeName(defaultQueryTypeName)));
    }

    private static Map<String, BatchLoader> addDataSources(Map<SchemaNamespace, BraidSchemaSource> dataSources,
                                                           TypeDefinitionRegistry registry,
                                                           RuntimeWiring.Builder runtimeWiringBuilder,
                                                           ObjectTypeDefinition queryObjectTypeDefinition,
                                                           ObjectTypeDefinition mutationObjectTypeDefinition,
                                                           List<SchemaTransformation> customSchemaTransformations,
                                                           BatchLoaderEnvironment batchLoaderEnvironment) {
        addAllNonOperationTypes(dataSources, registry, runtimeWiringBuilder);

        BraidingContext braidingContext = new BraidingContext(dataSources, registry, runtimeWiringBuilder,
                queryObjectTypeDefinition, mutationObjectTypeDefinition, batchLoaderEnvironment);

        return Stream.concat(schemaTransformationFactories.stream(), customSchemaTransformations.stream())
                .map(schemaTransformation -> schemaTransformation.transform(braidingContext))
                .map(Map::entrySet)
                .flatMap(Collection::stream)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    Map<String, BatchLoader> getBatchLoaders() {
        return Collections.unmodifiableMap(batchLoaders);
    }

    public GraphQLSchema getSchema() {
        return schema;
    }

    private static void addAllNonOperationTypes(Map<SchemaNamespace, BraidSchemaSource> dataSources,
                                                TypeDefinitionRegistry registry,
                                                RuntimeWiring.Builder runtimeWiringBuilder) {

        final Map<String, List<BraidTypeDefinition>> allNonOperationTypeDefinitions = dataSources.values().stream()
                .map(BraidSchemaSource::getNonOperationTypes)
                .flatMap(Collection::stream)
                .collect(groupingBy(BraidTypeDefinition::getName));

        final List<List<BraidTypeDefinition>> duplicateTypes =
                allNonOperationTypeDefinitions.values().stream()
                        .filter(e -> e.size() > 1)
                        .collect(toList());

        if (!duplicateTypes.isEmpty()) {
            duplicateTypes.stream().flatMap(Collection::stream)
                    .forEach(c -> System.out.printf("Type `%s` from %s is in conflict\n", c.getName(), c.getNamespace()));
            throw new IllegalStateException("Type name conflict exists");
        }

        // add custom scalars and a default implementation if one is not provided
        wireScalarDefinitions(dataSources, registry, runtimeWiringBuilder);

        allNonOperationTypeDefinitions.values().stream()
                .map(types -> types.get(0))
                .peek(type -> wireFieldDefinitions(runtimeWiringBuilder, type.getType(), type.getFieldDefinitions()))
                .map(BraidTypeDefinition::getType)
                .forEach(registry::add);

        //Copy directives into typeDefinitionRegistry
        dataSources.values().stream().map(d -> d.getTypeRegistry().getDirectiveDefinitions().values())
                .forEach(t -> t.stream().forEach(registry::add));
    }

    private static void wireFieldDefinitions(RuntimeWiring.Builder runtimeWiringBuilder,
                                             TypeDefinition type,
                                             List<FieldDefinition> fieldDefinitions) {
        fieldDefinitions.forEach(fd ->
                runtimeWiringBuilder.type(
                        type.getName(),
                        wiring -> wiring.dataFetcher(fd.getName(), new AliasablePropertyDataFetcher(fd.getName()))));
    }

    private static void wireScalarDefinitions(Map<SchemaNamespace, BraidSchemaSource> dataSources,
                                              TypeDefinitionRegistry registry,
                                              RuntimeWiring.Builder runtimeWiringBuilder) {
        // add all custom defined and default scalars to the TypeDefinitionRegistry
        Function<TypeDefinitionRegistry, Map<String, ScalarTypeDefinition>> scalars = TypeDefinitionRegistry::scalars;
        dataSources.values().stream()
                .map(BraidSchemaSource::getTypeRegistry)
                .map(scalars)
                .map(Map::values)
                .flatMap(Collection::stream)
                .forEach(registry::add);

        // if a coercing mapping is not defined for any of the scalars that we just added to the TypeDefinitionRegistry,
        // add a DefaultScalarCoercing mapping for it so that the combined schema can be built
        final Predicate<String> notAlreadyWired = isNotAlreadyWired(runtimeWiringBuilder.build().getScalars());
        registry.scalars()
                .values()
                .stream()
                .map(ScalarTypeDefinition::getName)
                .filter(notAlreadyWired)
                .map(scalarName -> new GraphQLScalarType(scalarName,"", new DefaultScalarCoercing()))
                .forEach(runtimeWiringBuilder::scalar);
    }

    private static Predicate<String> isNotAlreadyWired(Map<String, GraphQLScalarType> wiredScalarTypeByName) {
        return scalarName -> !wiredScalarTypeByName.containsKey(scalarName);
    }

    private static Map<SchemaNamespace, BraidSchemaSource> toBraidSchemaSourceMap(List<SchemaSource> schemaSources) {
        return schemaSources.stream()
                .map(BraidSchemaSource::new)
                .collect(groupingBy(BraidSchemaSource::getNamespace, singleton()));
    }
}
