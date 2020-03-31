package com.atlassian.braid;

import graphql.language.Field;

import java.util.List;
import java.util.Map;

/**
 * <p>Defines the context of Braid GraphQL execution, from which the underlying context can be retrieved.
 * <p>Note: this class is for Braid's internal usage, and should not be used directly. Use methods on
 * {@link BraidContexts} to access context information
 *
 * @see BraidContexts
 */
public interface BraidContext<C> {

    void addMissingFields(String typeName, List<Field> fields);

    List<Field> getMissingFields(String typeName);

    Map<String, List<Field>> getAllMissingFields();

    /**
     * @return the underlying user set context
     */
    C getContext();
}
