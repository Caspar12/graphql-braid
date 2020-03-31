package com.atlassian.braid.source;

import graphql.execution.DataFetcherResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
interface QueryFunction<C> {

    CompletableFuture<DataFetcherResult<Map<String, Object>>> query(Query query, C context);
}