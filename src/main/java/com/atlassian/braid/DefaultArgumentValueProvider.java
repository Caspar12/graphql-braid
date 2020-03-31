package com.atlassian.braid;

import graphql.schema.DataFetchingEnvironment;

import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class DefaultArgumentValueProvider implements ArgumentValueProvider {
    public static final ArgumentValueProvider INSTANCE = new DefaultArgumentValueProvider();

    @Override
    public CompletableFuture<Object> fetchValueForArgument(LinkArgument linkArgument, DataFetchingEnvironment environment) {
        switch (linkArgument.getArgumentSource()) {
            case OBJECT_FIELD:
                return BatchLoaderUtils.getFieldValue(environment, linkArgument.getSourceName())
                        .thenApply(v -> v.orElse(null));
            case CONTEXT:
                throw new UnsupportedOperationException("not supported in default implementation");
            case FIELD_ARGUMENT:
                return completedFuture(environment.getArgument(linkArgument.getSourceName()));
            default:
                throw new UnsupportedOperationException("Unsupported argument source.");
        }
    }
}
