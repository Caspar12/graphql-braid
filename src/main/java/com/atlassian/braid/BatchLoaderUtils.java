package com.atlassian.braid;

import com.atlassian.braid.java.util.BraidMaps;
import com.atlassian.braid.java.util.BraidObjects;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.atlassian.braid.java.util.BraidObjects.cast;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * A list of utility methods useful when building a new {@link org.dataloader.BatchLoader}
 */
public class BatchLoaderUtils {
    public static CompletableFuture<List<Object>> getTargetIdsFromEnvironment(String fieldName, DataFetchingEnvironment environment) {
        return getFieldValue(environment, fieldName)
                .thenApply(maybeValue -> {
                    Object ids = maybeValue.orElse(null);
                    if (ids instanceof String || ids instanceof Number) {
                        return singletonList(ids);
                    } else if (ids instanceof List) {
                        return BraidObjects.cast(ids);
                    } else if (ids == null) {
                        if (environment.getFieldType() instanceof GraphQLList) {
                            return emptyList();
                        } else {
                            return singletonList(null);
                        }
                    } else {
                        throw new IllegalArgumentException("Unexpected id type: " + ids);
                    }
                });
    }

    public static CompletableFuture<Optional<Object>> getFieldValue(DataFetchingEnvironment environment, String fromField) {
        Object source = environment.getSource();
        if (source instanceof CompletableFuture) {
            //noinspection unchecked
            return ((CompletableFuture<Object>) source).thenApply(resolvedSource -> getFieldValue(resolvedSource, fromField));
        } else {
            return completedFuture(getFieldValue(source, fromField));
        }
    }

    private static Optional<Object> getFieldValue(Object source, String fromField) {
        if (source instanceof Map) {
            return BraidMaps.get(cast(source), fromField);
        } else if (source instanceof DataFetcherResult) {
            return getFieldValue(((DataFetcherResult) source).getData(), fromField);
        } else if (source instanceof String || source instanceof Number || source instanceof List) {
            return Optional.of(source);
        } else {
            throw new IllegalArgumentException("Unexpected actual source type: " + source.getClass());
        }
    }
}
