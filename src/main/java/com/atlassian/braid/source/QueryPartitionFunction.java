package com.atlassian.braid.source;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * QueryPartitionFunction provides the way to split query based on partition before sending to remote service.
 * Stitching service will send out query as one batch if function is not provided.
 */
public interface QueryPartitionFunction {
    /**
     * @param environments list of DataFetchingEnvironment will be sent out to query data from remote services.
     * @param queryLoadFn  function to call remote services to get data back and merged them based on stitching logic.
     */
    CompletionStage<List<DataFetcherResult<Object>>> apply(List<DataFetchingEnvironment> environments, Function<List<DataFetchingEnvironment>,
            CompletionStage<List<DataFetcherResult<Object>>>> queryLoadFn);
}