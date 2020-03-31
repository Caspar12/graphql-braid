package com.atlassian.braid.source;

import com.atlassian.braid.Extension;
import com.atlassian.braid.FieldRename;
import com.atlassian.braid.FieldTransformation;
import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.TypeRename;
import com.atlassian.braid.BatchLoaderEnvironment;
import com.atlassian.braid.document.DocumentMapper;
import com.atlassian.braid.document.DocumentMapperFactory;
import com.atlassian.braid.document.DocumentMappers;
import com.atlassian.braid.java.util.BraidObjects;
import graphql.ExecutionInput;
import graphql.GraphQLError;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.BatchLoader;

import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.atlassian.braid.java.util.BraidObjects.cast;
import static com.atlassian.braid.source.SchemaUtils.loadPublicSchema;
import static com.atlassian.braid.source.SchemaUtils.loadSchema;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Data source for an external graphql service.  Loads the schema on construction.
 */
@SuppressWarnings("WeakerAccess")
public class QueryExecutorSchemaSource<C> implements SchemaSource {

    private SchemaNamespace namespace;
    private TypeDefinitionRegistry publicSchema;
    private TypeDefinitionRegistry privateSchema;
    private QueryExecutor<C> queryExecutor;
    private List<Link> links;
    private List<Extension> extensions;
    private DocumentMapperFactory documentMapperFactory;
    private List<TypeRename> typeRenames;
    private List<FieldRename> queryFieldRenames;
    private List<FieldRename> mutationFieldRenames;

    public QueryExecutorSchemaSource(SchemaNamespace namespace,
                                     TypeDefinitionRegistry publicSchema,
                                     TypeDefinitionRegistry privateSchema,
                                     GraphQLRemoteRetriever<C> graphQLRemoteRetriever,
                                     List<Link> links,
                                     List<Extension> extensions,
                                     DocumentMapperFactory documentMapperFactory,
                                     List<TypeRename> typeRenames,
                                     List<FieldRename> queryFieldRenames,
                                     List<FieldRename> mutationFieldRenames) {
        this(namespace,
                publicSchema,
                privateSchema,
                links,
                extensions,
                documentMapperFactory,
                typeRenames,
                queryFieldRenames,
                mutationFieldRenames);
        this.queryExecutor = new QueryExecutor<>(remoteQuery(graphQLRemoteRetriever));
    }

    public QueryExecutorSchemaSource(SchemaNamespace namespace,
                                     TypeDefinitionRegistry publicSchema,
                                     TypeDefinitionRegistry privateSchema,
                                     Function<Query, Object> localExecutor,
                                     List<Link> links,
                                     List<Extension> extensions,
                                     DocumentMapperFactory documentMapperFactory,
                                     List<TypeRename> typeRenames,
                                     List<FieldRename> queryFieldRenames,
                                     List<FieldRename> mutationFieldRenames) {
        this(namespace,
                publicSchema,
                privateSchema,
                links,
                extensions,
                documentMapperFactory,
                typeRenames,
                queryFieldRenames,
                mutationFieldRenames);
        this.queryExecutor = new QueryExecutor<>(localQuery(localExecutor));
    }

    private QueryExecutorSchemaSource(SchemaNamespace namespace,
                                      TypeDefinitionRegistry publicSchema,
                                      TypeDefinitionRegistry privateSchema,
                                      List<Link> links,
                                      List<Extension> extensions,
                                      DocumentMapperFactory documentMapperFactory,
                                      List<TypeRename> typeRenames,
                                      List<FieldRename> queryFieldRenames,
                                      List<FieldRename> mutationFieldRenames) {
        this.namespace = namespace;
        this.publicSchema = publicSchema;
        this.privateSchema = privateSchema;
        this.links = links;
        this.extensions = extensions;
        this.documentMapperFactory = documentMapperFactory;
        this.typeRenames = typeRenames;
        this.queryFieldRenames = queryFieldRenames;
        this.mutationFieldRenames = mutationFieldRenames;
    }

    @Override
    public BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> newBatchLoader(SchemaSource schemaSource,
                                                                                          FieldTransformation fieldTransformation,
                                                                                          BatchLoaderEnvironment batchLoaderEnvironment) {
        return queryExecutor.newBatchLoader(schemaSource, fieldTransformation, batchLoaderEnvironment);
    }

    private QueryFunction<C> localQuery(Function<Query, Object> queryExecutor) {
        return (query, context) -> {
            final Object result = queryExecutor.apply(transformExecutionInput(query, context));
            if (result instanceof DataFetcherResult) {
                return completedFuture((cast(result)));
            } else if (result instanceof Map) {
                return completedFuture(new DataFetcherResult<>(cast(result), emptyList()));
            } else {
                CompletableFuture<DataFetcherResult<Map<String, Object>>> future = new CompletableFuture<>();
                future.completeExceptionally(new IllegalStateException("Unexpected result type: " + nullSafeGetClass(result)));
                return future;
            }
        };
    }

    private <C> Query transformExecutionInput(Query query, C context) {
        return query.transform(builder -> builder.context(context));
    }

    private static Class<?> nullSafeGetClass(Object result) {
        return Optional.ofNullable(result).map(Object::getClass).orElse(null);
    }

    public static <C> Builder<C> builder() {
        return new Builder<>();
    }


    private QueryFunction<C> remoteQuery(GraphQLRemoteRetriever<C> graphQLRemoteRetriever) {
        return (query, context) ->
                graphQLRemoteRetriever.queryGraphQL(query, context).thenApply(response -> {

                    Map<String, Object> data = Optional.ofNullable(response.get("data"))
                            .map(BraidObjects::<Map<String, Object>>cast)
                            .orElse(Collections.emptyMap());
                    final List<Map> errorsMap = Optional.ofNullable(response.get("errors"))
                            .map(BraidObjects::<List<Map>>cast)
                            .orElse(emptyList());

                    List<GraphQLError> errors = errorsMap.stream()
                            .map(val -> Optional.ofNullable(val)
                                    .map(BraidObjects::<Map<String, Object>>cast)
                                    .orElseThrow(IllegalArgumentException::new))
                            .map(MapGraphQLError::new)
                            .collect(Collectors.toList());
                    return new DataFetcherResult<>(data, errors);
                });
    }

    public DocumentMapper<C> getDocumentMapper() {
        return documentMapperFactory.apply(getSchema());
    }

    @Override
    public TypeDefinitionRegistry getSchema() {
        return publicSchema;
    }

    @Override
    public TypeDefinitionRegistry getPrivateSchema() {
        return privateSchema;
    }

    @Override
    public SchemaNamespace getNamespace() {
        return namespace;
    }

    @Override
    public List<Link> getLinks() {
        return links;
    }

    @Override
    public List<Extension> getExtensions() {
        return extensions;
    }

    @Override
    public List<TypeRename> getTypeRenames() {
        return typeRenames;
    }

    @Override
    public List<FieldRename> getMutationFieldRenames() {
        return mutationFieldRenames;
    }

    @Override
    public List<FieldRename> getQueryFieldRenames() {
        return queryFieldRenames;
    }


    public static class Builder<C> {

        private List<Link> links = emptyList();
        private List<Extension> extensions = emptyList();
        private SchemaLoader schemaLoader;
        private SchemaNamespace schemaNamespace;
        private DocumentMapperFactory documentMapperFactory = DocumentMappers.identity();
        private GraphQLRemoteRetriever<C> remoteRetriever;
        private List<FieldRename> queryFieldRenames = emptyList();
        private List<FieldRename> mutationFieldRenames = emptyList();
        private List<TypeRename> typeRenames = emptyList();
        private Function<Query, Object> localRetriever;

        private Builder() {
        }

        @Deprecated
        public Builder<C> schemaProvider(Supplier<Reader> schemaProvider) {
            this.schemaLoader = new ReaderSupplierSchemaLoader(SchemaLoader.Type.IDL, schemaProvider);
            return this;
        }

        public Builder<C> schemaLoader(SchemaLoader schemaLoader) {
            this.schemaLoader = schemaLoader;
            return this;
        }

        public Builder<C> queryFieldRenames(List<FieldRename> fieldRenames) {
            this.queryFieldRenames = fieldRenames;
            return this;
        }

        public Builder<C> mutationFieldRenames(List<FieldRename> fieldRenames) {
            this.mutationFieldRenames = fieldRenames;
            return this;
        }

        public Builder<C> topLevelFields(String... topLevelFields) {
            return queryFieldRenames(Arrays.stream(topLevelFields)
                    .map(name -> FieldRename.from(name, name))
                    .collect(Collectors.toList()));
        }

        public Builder<C> namespace(SchemaNamespace schemaNamespace) {
            this.schemaNamespace = schemaNamespace;
            return this;
        }

        public Builder<C> links(List<Link> links) {
            this.links = links;
            return this;
        }

        public Builder<C> extensions(List<Extension> extensions) {
            this.extensions = extensions;
            return this;
        }

        public Builder<C> documentMapperFactory(DocumentMapperFactory documentMapperFactory) {
            this.documentMapperFactory = documentMapperFactory;
            return this;
        }

        public Builder<C> remoteRetriever(GraphQLRemoteRetriever<C> remoteRetriever) {
            this.remoteRetriever = remoteRetriever;
            return this;
        }

        public Builder<C> localRetriever(Function<Query, Object> queryExecutor) {
            this.localRetriever = queryExecutor;
            return this;
        }

        public Builder<C> typeRenames(List<TypeRename> typeRenames) {
            this.typeRenames = typeRenames;
            return this;
        }

        public QueryExecutorSchemaSource<C> build() {
            if (localRetriever != null && remoteRetriever != null) {
                throw new IllegalStateException("not allowed to have a localRetriever and a remoteRetriever");
            }
            if (localRetriever != null) {
                return new QueryExecutorSchemaSource<C>(
                        requireNonNull(schemaNamespace),
                        loadPublicSchema(
                                requireNonNull(schemaLoader),
                                requireNonNull(links),
                                queryFieldRenames.stream().map(FieldRename::getSourceName).toArray(String[]::new)),
                        schemaLoader.load(),
                        requireNonNull(localRetriever),
                        requireNonNull(links),
                        requireNonNull(extensions),
                        requireNonNull(documentMapperFactory),
                        requireNonNull(typeRenames),
                        requireNonNull(queryFieldRenames),
                        requireNonNull(mutationFieldRenames));

            }
            return new QueryExecutorSchemaSource<C>(
                    requireNonNull(schemaNamespace),
                    loadPublicSchema(
                            requireNonNull(schemaLoader),
                            requireNonNull(links),
                            queryFieldRenames.stream().map(FieldRename::getSourceName).toArray(String[]::new)),
                    schemaLoader.load(),
                    requireNonNull(remoteRetriever),
                    requireNonNull(links),
                    requireNonNull(extensions),
                    requireNonNull(documentMapperFactory),
                    requireNonNull(typeRenames),
                    requireNonNull(queryFieldRenames),
                    requireNonNull(mutationFieldRenames));
        }
    }

}
