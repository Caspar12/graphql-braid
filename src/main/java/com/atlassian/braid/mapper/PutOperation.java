package com.atlassian.braid.mapper;

import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

final class PutOperation<V> implements MapperOperation {

    private final String key;
    private final Supplier<V> value;
    private final Predicate<MapperInputOutput> predicate;

    PutOperation(String key, Supplier<V> value, Predicate<MapperInputOutput> predicate) {
        this.key = requireNonNull(key);
        this.value = requireNonNull(value);
        this.predicate = requireNonNull(predicate);
    }

    @Override
    public void accept(Map<String, Object> input, Map<String, Object> output) {
        if (predicate.test(MapperInputOutputPair.of(input, output))) {
            output.put(key, value.get());
        }
    }
}
