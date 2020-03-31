package com.atlassian.braid.mapper;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

final class CopyOperation<T, R> implements MapperOperation {

    private final String sourceKey;
    private final String targetKey;
    private final Supplier<R> defaultValue;
    private final Function<T, R> transform;
    private Predicate<MapperInputOutput> predicate;

    CopyOperation(String sourceKey,
                  String targetKey,
                  Supplier<R> defaultValue,
                  Function<T, R> transform,
                  Predicate<MapperInputOutput> predicate) {
        this.sourceKey = requireNonNull(sourceKey);
        this.targetKey = requireNonNull(targetKey);
        this.defaultValue = requireNonNull(defaultValue);
        this.transform = requireNonNull(transform);
        this.predicate = predicate;
    }

    @Override
    public void accept(Map<String, Object> input, Map<String, Object> output) {
        if (predicate.test(MapperInputOutputPair.of(input, output))) {
            final R value = MapperMaps.<T>get(input, sourceKey)
                    .map(transform)
                    .orElseGet(defaultValue);

            if (value != null) {
                output.put(targetKey, value);
            }
        }
    }
}
