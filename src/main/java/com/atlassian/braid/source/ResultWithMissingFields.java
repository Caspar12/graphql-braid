package com.atlassian.braid.source;

import graphql.language.Field;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class ResultWithMissingFields implements Map<String, Object> {

    private final Map<String, Object> result;
    private final List<Field> missingFields;

    public ResultWithMissingFields(Map<String, Object> result, List<Field> missingFields) {
        this.result = result;
        this.missingFields = missingFields;
    }

    public <T> T getResult() {
        return (T) result;
    }

    public List<Field> getMissingFields() {
        return missingFields;
    }

    @Override
    public int size() {
        return result.size();
    }

    @Override
    public boolean isEmpty() {
        return result.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return result.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return result.containsValue(value);
    }

    @Override
    public Object get(Object key) {
        return result.get(key);
    }

    @Override
    public Object put(String key, Object value) {
        return result.put(key, value);
    }

    @Override
    public Object remove(Object key) {
        return result.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        result.putAll(m);
    }

    @Override
    public void clear() {
        result.clear();
    }

    @Override
    public Set<String> keySet() {
        return result.keySet();
    }

    @Override
    public Collection<Object> values() {
        return result.values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return result.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        return result.equals(o);
    }

    @Override
    public int hashCode() {
        return result.hashCode();
    }

    @Override
    public Object getOrDefault(Object key, Object defaultValue) {
        return result.getOrDefault(key, defaultValue);
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super Object> action) {
        result.forEach(action);
    }

    @Override
    public void replaceAll(BiFunction<? super String, ? super Object, ?> function) {
        result.replaceAll(function);
    }

    @Override
    public Object putIfAbsent(String key, Object value) {
        return result.putIfAbsent(key, value);
    }

    @Override
    public boolean remove(Object key, Object value) {
        return result.remove(key, value);
    }

    @Override
    public boolean replace(String key, Object oldValue, Object newValue) {
        return result.replace(key, oldValue, newValue);
    }
}
