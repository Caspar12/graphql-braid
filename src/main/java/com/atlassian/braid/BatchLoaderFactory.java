package com.atlassian.braid;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.BatchLoader;

/**
 * Builds a new {@link BatchLoader} instance for a source and optional link
 */
public interface BatchLoaderFactory {

    /**
     * Builds a new batch loader
     *
     * @param schemaSource  the schema source
     * @param fieldTransformation the mutation to apply to process things like links and top level fields
     * @param batchLoaderEnvironment  includes all functions to apply to batch loader
     * @return a new loader instance
     */
    BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> newBatchLoader(SchemaSource schemaSource,
                                                                                   FieldTransformation fieldTransformation,
                                                                                   BatchLoaderEnvironment batchLoaderEnvironment);
}
