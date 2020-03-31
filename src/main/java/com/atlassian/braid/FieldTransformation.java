package com.atlassian.braid;

import graphql.execution.DataFetcherResult;
import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A field transformation that will turn a field request into one more more fields to usually request from a remote source
 */
public interface FieldTransformation {

    /**
     * Applies the transformation to an instance of a field in a query or mutation
     *
     * @param environment The environment for the field
     * @param context     The context of the mutation
     *
     * @return A list of fields to fetch from the target schema source
     */
    CompletableFuture<List<Field>> apply(DataFetchingEnvironment environment, FieldTransformationContext context);

    default DataFetcherResult<Object> unapply(DataFetchingEnvironment environment, DataFetcherResult<Object> dataFetcherResult) {
        return dataFetcherResult;
    }
}
