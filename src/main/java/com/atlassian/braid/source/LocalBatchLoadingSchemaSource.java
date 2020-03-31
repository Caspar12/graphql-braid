package com.atlassian.braid.source;

import com.atlassian.braid.BatchLoaderFactory;
import com.atlassian.braid.Extension;
import com.atlassian.braid.FieldTransformation;
import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.BatchLoaderEnvironment;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.BatchLoader;

import java.io.Reader;
import java.util.List;
import java.util.function.Supplier;

import static com.atlassian.braid.source.SchemaUtils.loadPublicSchema;
import static com.atlassian.braid.source.SchemaUtils.loadSchema;
import static java.util.Objects.requireNonNull;

public final class LocalBatchLoadingSchemaSource extends AbstractSchemaSource implements SchemaSource {

    private final BatchLoaderFactory batchLoaderFactory;

    public LocalBatchLoadingSchemaSource(SchemaNamespace namespace,
                                         SchemaLoader schemaLoader,
                                         List<Link> links,
                                         List<Extension> extensions,
                                         BatchLoaderFactory batchLoaderFactory,
                                         String... topLevelFields) {
        super(namespace, loadPublicSchema(schemaLoader, links, topLevelFields), schemaLoader.load(), links, extensions);
        this.batchLoaderFactory = requireNonNull(batchLoaderFactory);
    }

    @Override
    public BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> newBatchLoader(SchemaSource schemaSource,
                                                                                          FieldTransformation fieldTransformation,
                                                                                          BatchLoaderEnvironment batchLoaderEnvironment) {
        return batchLoaderFactory.newBatchLoader(schemaSource, fieldTransformation, batchLoaderEnvironment);
    }
}
