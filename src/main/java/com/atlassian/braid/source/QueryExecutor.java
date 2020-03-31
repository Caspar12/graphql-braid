package com.atlassian.braid.source;

import com.atlassian.braid.BatchLoaderEnvironment;
import com.atlassian.braid.BatchLoaderFactory;
import com.atlassian.braid.BraidContext;
import com.atlassian.braid.BraidContexts;
import com.atlassian.braid.FieldKey;
import com.atlassian.braid.FieldTransformation;
import com.atlassian.braid.FieldTransformationContext;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.TypeRename;
import com.atlassian.braid.document.DocumentMapper.MappedDocument;
import com.atlassian.braid.java.util.BraidObjects;
import com.atlassian.braid.transformation.BraidSchemaSource;
import graphql.GraphQLError;
import graphql.execution.DataFetcherResult;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.OperationDefinition.Operation;
import graphql.language.SelectionSet;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import org.dataloader.BatchLoader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.IntFunction;

import static com.atlassian.braid.graphql.language.DocumentTransformations.removeMissingFieldsIfBraidAndSourceTypeFieldsDiffer;
import static com.atlassian.braid.graphql.language.DocumentTransformations.renameTypesToSourceNames;
import static com.atlassian.braid.java.util.BraidCollectors.SingletonCharacteristics.ALLOW_MULTIPLE_OCCURRENCES;
import static com.atlassian.braid.java.util.BraidCollectors.nullSafeToMap;
import static com.atlassian.braid.java.util.BraidCollectors.singleton;
import static graphql.language.OperationDefinition.Operation.MUTATION;
import static graphql.language.OperationDefinition.Operation.QUERY;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;

/**
 * Executes a query against the data source
 */
class QueryExecutor<C> implements BatchLoaderFactory {

    private final QueryFunction<C> queryFunction;

    QueryExecutor(QueryFunction<C> queryFunction) {
        this.queryFunction = requireNonNull(queryFunction);
    }

    @Override
    public BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> newBatchLoader(SchemaSource schemaSource,
                                                                                          FieldTransformation fieldTransformation,
                                                                                          BatchLoaderEnvironment batchLoaderEnvironment) {
        return new QueryExecutorBatchLoader<>(BraidObjects.cast(schemaSource), queryFunction, fieldTransformation, batchLoaderEnvironment);
    }

    private static class QueryExecutorBatchLoader<C> implements BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> {

        private final QueryExecutorSchemaSource<C> schemaSource;
        private final FieldTransformation fieldTransformation;
        private final QueryFunction<C> queryFunction;
        private final BraidSchemaSource braidSchemaSource;
        private final BatchLoaderEnvironment batchLoaderEnvironment;

        private QueryExecutorBatchLoader(QueryExecutorSchemaSource<C> schemaSource, QueryFunction<C> queryFunction,
                                         FieldTransformation fieldTransformation,
                                         BatchLoaderEnvironment batchLoaderEnvironment) {
            this.schemaSource = requireNonNull(schemaSource);
            this.braidSchemaSource = new BraidSchemaSource(schemaSource);
            this.fieldTransformation = fieldTransformation;
            this.queryFunction = requireNonNull(queryFunction);
            this.batchLoaderEnvironment = batchLoaderEnvironment;
        }

        @Override
        public CompletionStage<List<DataFetcherResult<Object>>> load(List<DataFetchingEnvironment> environments) {
            return batchLoaderEnvironment == null || batchLoaderEnvironment.getQueryPartitionFunction() == null
                    ? loadInternal(environments)
                    : batchLoaderEnvironment.getQueryPartitionFunction().apply(environments, this::loadInternal);
        }

        private CompletionStage<List<DataFetcherResult<Object>>> loadInternal(List<DataFetchingEnvironment> environments) {
            final C context = checkAndGetContext(environments);
            final Operation operationType = checkAndGetOperationType(environments).orElse(QUERY);
            final GraphQLOutputType fieldOutputType = checkAndGetFieldOutputType(environments);

            OperationDefinition queryOp = newQueryOperationDefinition(braidSchemaSource, fieldOutputType, operationType);

            FieldTransformationContext fieldTransformationContext = new FieldTransformationContext(schemaSource, queryOp);

            // build batch queryResult
            CompletableFuture<Void>[] fieldFutures = environments.stream()
                    .map(env -> fieldTransformation.apply(env, fieldTransformationContext)
                            .thenAccept(fields -> fieldTransformationContext.getClonedFields().put(env, fields.stream()
                                    .map(Field::getAlias)
                                    .map(FieldKey::new)
                                    .collect(toList()))))
                    .toArray((IntFunction<CompletableFuture<Void>[]>) CompletableFuture[]::new);

            return CompletableFuture.allOf(fieldFutures)
                    .thenCompose(__ -> {
                        //Type rename must go after document mapper applied in order to have field rename and type rename work together.
                        MappedDocument mappedDocument = schemaSource.getDocumentMapper().apply(context,fieldTransformationContext.getDocument());
                        Document renamedTypesDoc = renameTypesToSourceNames(braidSchemaSource, mappedDocument.getDocument());
                        Document noMissingFieldsDoc = removeMissingFieldsIfBraidAndSourceTypeFieldsDiffer(fieldTransformationContext,
                                renamedTypesDoc, fieldOutputType);

                        mappedDocument = new MappedDocument(noMissingFieldsDoc, mappedDocument.getResultMapper());

                        return queryAndHandleResult(environments, context, fieldOutputType, queryOp, fieldTransformationContext, mappedDocument);
                    });
        }

        private CompletionStage<List<DataFetcherResult<Object>>> queryAndHandleResult(List<DataFetchingEnvironment> environments,
                                                                                      C context, GraphQLOutputType fieldOutputType,
                                                                                      OperationDefinition queryOp,
                                                                                      FieldTransformationContext fieldTransformationContext,
                                                                                      MappedDocument mappedDocument) {
            return executeQuery(context, mappedDocument.getDocument(), queryOp, fieldTransformationContext.getVariables())
                    .thenApply(result -> resultWithShortCircuitedData(fieldTransformationContext.getShortCircuitedData(), result))
                    .thenApply(result -> resultWithMappedData(mappedDocument, result))
                    .thenApply(result -> transformBatchResultIntoResultList(environments, fieldTransformationContext.getClonedFields(), result))
                    .thenApply(result -> result.stream()
                            .map(dfr -> {
                                if (fieldTransformationContext.getMissingFields().isEmpty()) {
                                    return dfr;
                                } else {
                                    ((BraidContext) environments.get(0).getContext()).addMissingFields(fieldOutputType.getName(), fieldTransformationContext.getMissingFields());
                                    return dfr;
                                }
                            })
                            .collect(toList()));
        }

        private List<DataFetcherResult<Object>> transformBatchResultIntoResultList(
                List<DataFetchingEnvironment> environments,
                Map<DataFetchingEnvironment, List<FieldKey>> clonedFields,
                DataFetcherResult<Map<FieldKey, Object>> result) {
            List<DataFetcherResult<Object>> queryResults = new ArrayList<>();
            Map<FieldKey, Object> data = result.getData();
            for (DataFetchingEnvironment environment : environments) {
                List<FieldKey> fields = clonedFields.get(environment);
                Object fieldData;
                DataFetcherResult<Object> dataFetcherResult;
                if (!fields.isEmpty()) {
                    FieldKey field = fields.get(0);
                    fieldData = BraidObjects.cast(data.getOrDefault(field, null));

                    if (environment.getFieldType() instanceof GraphQLList && !(fieldData instanceof List)) {
                        fieldData = fields.stream()
                                .map(f -> BraidObjects.cast(data.getOrDefault(f, null)))
                                .collect(toList());
                    } else if (fields.size() > 1) {
                        throw new IllegalStateException("Can't query for multiple fields if the target type isn't a list");
                    }
                    dataFetcherResult = new DataFetcherResult<>(
                            fieldData,
                            buildDataFetcherResultErrors(result, fields)
                    );
                } else if (environment.getSource() instanceof Map &&
                        environment.<Map<String, Object>>getSource().get(environment.getFieldDefinition().getName()) instanceof List) {
                    dataFetcherResult = new DataFetcherResult<>(
                            emptyList(),
                            buildDataFetcherResultErrors(result, fields)
                    );
                } else {
                    dataFetcherResult = new DataFetcherResult<>(
                            null,
                            buildDataFetcherResultErrors(result, fields)
                    );
                }
                dataFetcherResult = fieldTransformation.unapply(environment, dataFetcherResult);
                queryResults.add(dataFetcherResult);
            }
            return queryResults;
        }

        private static <C> C checkAndGetContext(Collection<DataFetchingEnvironment> environments) {
            return environments.stream().map(BraidContexts::<C>get).collect(singleton(ALLOW_MULTIPLE_OCCURRENCES));
        }

        private static Optional<Operation> checkAndGetOperationType(Collection<DataFetchingEnvironment> environments) {
            return environments.stream()
                    .map(QueryExecutorBatchLoader::getOperationType)
                    .collect(singleton(ALLOW_MULTIPLE_OCCURRENCES));
        }

        private static Optional<Operation> getOperationType(DataFetchingEnvironment env) {
            final GraphQLType graphQLType = env.getParentType();
            final GraphQLSchema graphQLSchema = env.getGraphQLSchema();
            if (Objects.equals(graphQLSchema.getQueryType(), graphQLType)) {
                return Optional.of(QUERY);
            } else if (Objects.equals(graphQLSchema.getMutationType(), graphQLType)) {
                return Optional.of(MUTATION);
            } else {
                return Optional.empty();
            }
        }

        /**
         * Checks the field type for all environments is the same and returns it
         *
         * @param environments the collection of environments to check
         * @return the found {@link GraphQLOutputType}
         */
        private static GraphQLOutputType checkAndGetFieldOutputType(List<DataFetchingEnvironment> environments) {
            return environments.stream()
                    .map(DataFetchingEnvironment::getFieldDefinition)
                    .map(GraphQLFieldDefinition::getType)
                    .collect(singleton(ALLOW_MULTIPLE_OCCURRENCES));
        }

        private CompletableFuture<DataFetcherResult<Map<String, Object>>> executeQuery(C context, Document doc, OperationDefinition queryOp, Map<String, Object> variables) {
            final CompletableFuture<DataFetcherResult<Map<String, Object>>> queryResult;
            if (queryOp.getSelectionSet().getSelections().isEmpty()) {
                queryResult = completedFuture(new DataFetcherResult<>(emptyMap(), emptyList()));
            } else {
                Query input = executeBatchQuery(doc, queryOp.getName(), variables);
                queryResult = queryFunction.query(input, context);
            }
            return queryResult;
        }
    }

    private static DataFetcherResult<Map<FieldKey, Object>> resultWithMappedData(MappedDocument mappedDocument, DataFetcherResult<Map<FieldKey, Object>> result) {
        final Function<Map<String, Object>, Map<String, Object>> mapper = mappedDocument.getResultMapper();
        final Map<String, Object> data = new HashMap<>();
        result.getData().forEach((key, value) -> data.put(key.getValue(), value));

        final Map<String, Object> newData = mapper.apply(data);

        final Map<FieldKey, Object> resultData = new HashMap<>();
        newData.forEach((key, value) -> resultData.put(new FieldKey(key), value));
        return new DataFetcherResult<>(resultData, result.getErrors());
    }

    private static DataFetcherResult<Map<FieldKey, Object>> resultWithShortCircuitedData(Map<FieldKey, Object> shortCircuitedData, DataFetcherResult<Map<String, Object>> result) {
        final HashMap<FieldKey, Object> data = new HashMap<>();
        Map<FieldKey, Object> dataByKey = result.getData().entrySet().stream()
                .collect(nullSafeToMap(e -> new FieldKey(e.getKey()), Map.Entry::getValue));
        data.putAll(dataByKey);
        data.putAll(shortCircuitedData);
        return new DataFetcherResult<>(data, result.getErrors());
    }

    private static OperationDefinition newQueryOperationDefinition(BraidSchemaSource braidSchemaSource,
                                                                   GraphQLOutputType fieldType,
                                                                   Operation operationType) {
        return OperationDefinition.newOperationDefinition()
                .name(newBulkOperationName(braidSchemaSource, fieldType))
                .operation(operationType)
                .selectionSet(SelectionSet.newSelectionSet().build())
                .build();
    }

    private static String newBulkOperationName(BraidSchemaSource braidSchemaSource, GraphQLOutputType fieldType) {
        GraphQLType type = fieldType;
        while (true) {
            if (type instanceof GraphQLList) {
                type = ((GraphQLList) type).getWrappedType();
            }
            if (type instanceof GraphQLNonNull) {
                type = ((GraphQLNonNull) type).getWrappedType();
            } else {
                break;
            }
        }
        String originalTypeName = braidSchemaSource.getTypeRenameFromBraidName(type.getName())
                .map(TypeRename::getSourceName)
                .orElse(type.getName());
        return "Bulk_" + originalTypeName;
    }

    private static Query executeBatchQuery(Document doc, String operationName, Map<String, Object> variables) {
        return Query.newQuery()
                .query(doc)
                .operationName(operationName)
                .variables(variables)
                .build();
    }


    private static List<GraphQLError> buildDataFetcherResultErrors(DataFetcherResult<Map<FieldKey, Object>> result, List<FieldKey> fields) {
        return result.getErrors().stream()
                .filter(e -> e.getPath() == null || e.getPath().isEmpty()
                        || fields.contains(new FieldKey(String.valueOf(e.getPath().get(0)))))
                .map(RelativeGraphQLError::new)
                .collect(toList());
    }
}