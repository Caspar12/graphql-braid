package com.atlassian.braid.transformation;

import com.atlassian.braid.Extension;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.graphql.language.KeyedDataFetchingEnvironment;
import com.atlassian.braid.java.util.BraidObjects;
import graphql.execution.DataFetcherResult;
import graphql.language.Field;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import org.dataloader.BatchLoader;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.atlassian.braid.TypeUtils.unwrap;
import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

public class ExtensionSchemaTransformation implements SchemaTransformation {
    @Override
    public Map<String, BatchLoader> transform(BraidingContext ctx) {
        return ctx.getRegistry().types().values().stream()
                .flatMap(typeDef -> BraidTypeDefinition.getFieldDefinitions(typeDef).stream()
                        .flatMap(fieldDef -> ctx.getDataSources().values().stream()
                                .filter(ds -> ds.hasTypeAndField(ctx.getRegistry(), typeDef, fieldDef))
                                .flatMap(ds -> ds.getExtensions(ds.getSourceTypeName(unwrap(fieldDef.getType()))).stream()
                                        .map(ext -> mergeType(ds, ctx, typeDef, fieldDef, ext))
                                        .flatMap(m -> m.entrySet().stream())
                                )))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>>> mergeType(BraidSchemaSource ds,
                                                                                                   BraidingContext ctx,
                                                                                                   TypeDefinition typeDef,
                                                                                                   FieldDefinition fieldDef,
                                                                                                   Extension ext) {
        ObjectTypeDefinition originalType = findRequiredOriginalType(ctx, ds, fieldDef);

        Set<String> originalTypeFieldNames = originalType.getFieldDefinitions().stream().map(FieldDefinition::getName).collect(toSet());

        BraidSchemaSource targetSource = ctx.getDataSources().get(ext.getBy().getNamespace());

        ObjectTypeDefinition targetType = findRequiredTargetType(targetSource, ext);

        String key = "ext-" + typeDef.getName();

        wireNewFields(ctx, ds, typeDef, fieldDef, ext, originalType, originalTypeFieldNames, targetType, key);

        SchemaSource schemaSource = ctx.getDataSources().get(ext.getBy().getNamespace()).getSchemaSource();
        return singletonMap(key, schemaSource.newBatchLoader(schemaSource, new ExtensionTransformation(ext), ctx.getBatchLoaderEnvironment()));
    }

    private ObjectTypeDefinition findRequiredTargetType(BraidSchemaSource targetSource, Extension ext) {
        return (ObjectTypeDefinition) targetSource.getType(ext.getBy().getType()).orElseThrow(IllegalAccessError::new);
    }

    private ObjectTypeDefinition findRequiredOriginalType(BraidingContext ctx, BraidSchemaSource braidSchemaSource, FieldDefinition fieldDef) {
        return (ObjectTypeDefinition) ctx.getRegistry().getType(braidSchemaSource.getBraidTypeName(unwrap(fieldDef.getType())))
                .orElseThrow(IllegalArgumentException::new);
    }

    private void wireNewFields(BraidingContext ctx, BraidSchemaSource ds, TypeDefinition typeDef, FieldDefinition fieldDef, Extension ext, ObjectTypeDefinition originalType, Set<String> originalTypeFieldNames, ObjectTypeDefinition targetType, String key) {
        targetType.getFieldDefinitions().stream()
                .filter(fd -> !originalTypeFieldNames.contains(fd.getName()))
                .forEach(fd -> {
                    originalType.getFieldDefinitions().add(fd);
                    ctx.getRuntimeWiringBuilder().type(ds.getBraidTypeName(ext.getType()), wiring ->
                            wiring.dataFetcher(fd.getName(), buildDataFetcher(ds, typeDef, fieldDef, key, fd)));
                });
    }

    private DataFetcher buildDataFetcher(BraidSchemaSource ds, TypeDefinition typeDef, FieldDefinition fieldDef, String key, FieldDefinition fd) {
        return env -> env.getDataLoader(key)
                .load(new KeyedDataFetchingEnvironment(updateDataFetchingEnvironment(ds, typeDef, fieldDef, env)))
                .thenApply(BraidObjects::<DataFetcherResult<Map<String, Object>>>cast)
                .thenApply(dfr -> nullSafeGetFieldValue(dfr, fd.getName()));
    }

    private static Object nullSafeGetFieldValue(@Nullable DataFetcherResult<Map<String, Object>> dfr, String fieldName) {
        return Optional.ofNullable(dfr)
                .flatMap(r -> Optional.ofNullable(r.getData()))
                .map(data -> data.get(fieldName))
                .orElse(null);
    }

    private static DataFetchingEnvironment updateDataFetchingEnvironment(BraidSchemaSource ds,
                                                                         TypeDefinition typeDef,
                                                                         FieldDefinition fieldDef,
                                                                         DataFetchingEnvironment env) {
        final GraphQLSchema graphQLSchema = env.getGraphQLSchema();
        return newDataFetchingEnvironment(env)
                .source(env.getSource())
                .fieldDefinition(graphQLSchema.getObjectType(ds.getBraidTypeName(typeDef.getName())).getFieldDefinition(fieldDef.getName()))
                .fields(singletonList(new Field(fieldDef.getName())))
                .fieldType(graphQLSchema.getObjectType(((TypeName) fieldDef.getType()).getName()))
                .parentType(graphQLSchema.getObjectType("Query"))
                .build();
    }
}
