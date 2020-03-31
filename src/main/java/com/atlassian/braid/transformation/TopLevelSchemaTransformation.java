package com.atlassian.braid.transformation;

import com.atlassian.braid.BatchLoaderEnvironment;
import com.atlassian.braid.FieldRename;
import com.atlassian.braid.SchemaNamespace;
import graphql.execution.DataFetcherResult;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.atlassian.braid.TypeUtils.unwrap;
import static com.atlassian.braid.transformation.DataFetcherUtils.getDataLoaderKey;
import static com.atlassian.braid.transformation.DataFetcherUtils.getLinkDataLoaderKey;
import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;


public class TopLevelSchemaTransformation implements SchemaTransformation {
    @Override
    public Map<String, BatchLoader> transform(BraidingContext braidingContext) {
        Map<SchemaNamespace, BraidSchemaSource> dataSources = braidingContext.getDataSources();

        List<FieldDataLoaderRegistration> queryLoaders = addSchemaSourcesTopLevelFieldsToOperation(dataSources, braidingContext.getQueryObjectTypeDefinition(),
                BraidSchemaSource::getQueryType, BraidSchemaSource::getQueryFieldRenames, braidingContext.getBatchLoaderEnvironment());
        List<FieldDataLoaderRegistration> mutationLoaders = addSchemaSourcesTopLevelFieldsToOperation(dataSources, braidingContext.getMutationObjectTypeDefinition(),
                BraidSchemaSource::getMutationType, BraidSchemaSource::getMutationFieldRenames, braidingContext.getBatchLoaderEnvironment());

        return Stream.of(queryLoaders, mutationLoaders)
                .flatMap(Collection::stream)
                .map(r -> {
                    String dataLoaderKey = getDataLoaderKey(r.type, r.field);
                    braidingContext.getRuntimeWiringBuilder().type(r.type, wiring -> wiring.dataFetcher(r.field, new TopLevelDataFetcher(r.type, r.field)));
                    return singletonMap(dataLoaderKey, r.loader);
                })
                .map(Map::entrySet)
                .flatMap(Collection::stream)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static List<FieldDataLoaderRegistration> addSchemaSourcesTopLevelFieldsToOperation
            (Map<SchemaNamespace, BraidSchemaSource> dataSources,
             ObjectTypeDefinition braidOperationType,
             Function<BraidSchemaSource, Optional<ObjectTypeDefinition>> findOperationType,
             BiFunction<BraidSchemaSource, String, Optional<FieldRename>> getFieldRename,
             BatchLoaderEnvironment batchLoaderEnvironment) {
        return dataSources.values()
                .stream()
                .map(source -> addSchemaSourceTopLevelFieldsToOperation(source, braidOperationType, findOperationType,
                        getFieldRename, batchLoaderEnvironment))
                .flatMap(Collection::stream)
                .collect(toList());
    }

    private static List<FieldDataLoaderRegistration> addSchemaSourceTopLevelFieldsToOperation(
            BraidSchemaSource source,
            ObjectTypeDefinition braidOperationType,
            Function<BraidSchemaSource, Optional<ObjectTypeDefinition>> findOperationType,
            BiFunction<BraidSchemaSource, String, Optional<FieldRename>> getFieldRename,
            BatchLoaderEnvironment batchLoaderEnvironment) {

        return findOperationType.apply(source)
                .map(operationType -> addSchemaSourceTopLevelFieldsToOperation(source, braidOperationType,
                        operationType, getFieldRename, batchLoaderEnvironment))
                .orElse(emptyList());
    }

    private static List<FieldDataLoaderRegistration> addSchemaSourceTopLevelFieldsToOperation(
            BraidSchemaSource schemaSource,
            ObjectTypeDefinition braidOperationType,
            ObjectTypeDefinition sourceOperationType,
            BiFunction<BraidSchemaSource, String, Optional<FieldRename>> getFieldRename,
            BatchLoaderEnvironment batchLoaderEnvironment) {

        // todo: smarter merge, optional namespacing, etc
        final List<RenamedFieldDefinition> fieldDefinitions = renamedFieldDefinitions(schemaSource, sourceOperationType, getFieldRename);

        final List<FieldDefinition> braidOperationTypeFieldDefinitions = braidOperationType.getFieldDefinitions();
        fieldDefinitions.forEach(bfd -> braidOperationTypeFieldDefinitions.add(bfd.definition));

        return wireOperationFields(braidOperationType.getName(), schemaSource, fieldDefinitions, batchLoaderEnvironment);
    }

    private static List<RenamedFieldDefinition> renamedFieldDefinitions(BraidSchemaSource schemaSource,
                                                                        ObjectTypeDefinition sourceOperationType,
                                                                        BiFunction<BraidSchemaSource, String, Optional<FieldRename>> getFieldRename) {
        return sourceOperationType.getFieldDefinitions().stream()
                .map(definition -> {
                    Optional<FieldRename> optionalFieldRename = getFieldRename.apply(schemaSource, definition.getName());
                    return optionalFieldRename
                            .map(fieldRename -> new RenamedFieldDefinition(fieldRename, definition))
                            .orElseGet(() -> new RenamedFieldDefinition(FieldRename.from(definition.getName(), definition.getName()), definition));
                })
                .map(def -> renamedFieldDefinition(schemaSource, def))
                .collect(toList());
    }

    private static RenamedFieldDefinition renamedFieldDefinition(BraidSchemaSource schemaSource, RenamedFieldDefinition renamedFieldDefinition) {
        final FieldDefinition definition = renamedFieldDefinition.definition;
        Type renamedType = schemaSource.renameTypeToBraidName(definition.getType());
        return new RenamedFieldDefinition(
                renamedFieldDefinition.fieldRename,
                FieldDefinition.newFieldDefinition()
                        .name(renamedFieldDefinition.fieldRename.getBraidName())
                        .type(renamedType)
                        .inputValueDefinitions(schemaSource.renameInputValueDefinitionsToBraidTypes(definition.getInputValueDefinitions()))
                        .directives(definition.getDirectives()).build());
    }

    private static List<FieldDataLoaderRegistration> wireOperationFields(String typeName, BraidSchemaSource schemaSource,
                                                                         List<RenamedFieldDefinition> fieldDefinitions,
                                                                         BatchLoaderEnvironment batchLoaderEnvironment) {
        return fieldDefinitions.stream()
                .map(queryField -> wireOperationField(typeName, schemaSource, queryField, batchLoaderEnvironment))
                .collect(toList());
    }

    private static FieldDataLoaderRegistration wireOperationField(
            String typeName,
            BraidSchemaSource schemaSource,
            RenamedFieldDefinition operationField,
            BatchLoaderEnvironment batchLoaderEnvironment) {

        String sourceType = schemaSource.getSourceTypeName(unwrap(operationField.definition.getType()));
        BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> batchLoader =
                schemaSource.getSchemaSource().newBatchLoader(schemaSource.getSchemaSource(),
                        new TopLevelFieldTransformation(operationField.fieldRename, schemaSource.getExtensions(sourceType)), batchLoaderEnvironment);

        return new FieldDataLoaderRegistration(typeName, operationField.fieldRename.getBraidName(), batchLoader);
    }

    private static final class RenamedFieldDefinition {
        private final FieldRename fieldRename;
        private final FieldDefinition definition;

        private RenamedFieldDefinition(FieldRename fieldRename, FieldDefinition definition) {
            this.fieldRename = fieldRename;
            this.definition = definition;
        }
    }

    private static class FieldDataLoaderRegistration {
        private final String type;
        private final String field;
        private final BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> loader;

        private FieldDataLoaderRegistration(String type, String field, BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> loader) {
            this.type = requireNonNull(type);
            this.field = requireNonNull(field);
            this.loader = requireNonNull(loader);
        }
    }

    public static class TopLevelDataFetcher implements DataFetcher {

        private final String type;
        private final String field;

        public TopLevelDataFetcher(String type, String field) {
            this.type = type;
            this.field = field;
        }

        @Override
        public Object get(DataFetchingEnvironment env) {
            final Object loadedValue = env.getDataLoader(getDataLoaderKey(type, field)).load(env);

            // allows a top level field to also be linked
            return Optional.ofNullable(env.getDataLoader(getLinkDataLoaderKey(type, field)))
                    .map(l -> loadFromLinkLoader(env, loadedValue, l))
                    .orElse(loadedValue);
        }

        private static Object loadFromLinkLoader(DataFetchingEnvironment env,
                                                 Object source,
                                                 DataLoader<Object, Object> dataLoader) {
            return dataLoader.load(newDataFetchingEnvironment(env)
                    .source(source)
                    .fieldDefinition(env.getFieldDefinition())
                    .build());
        }
    }

}
