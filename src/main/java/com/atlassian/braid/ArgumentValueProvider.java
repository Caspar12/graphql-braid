package com.atlassian.braid;

import graphql.schema.DataFetchingEnvironment;

import java.util.concurrent.CompletableFuture;

public interface ArgumentValueProvider {
    static ArgumentValueProvider staticArgumentValue(Object value) {
        return (linkArgument, environment) -> CompletableFuture.completedFuture(value);
    }

    CompletableFuture<Object> fetchValueForArgument(LinkArgument linkArgument,
                                                    DataFetchingEnvironment environment);
}
