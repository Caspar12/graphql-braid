package com.atlassian.braid.source;


import graphql.schema.idl.TypeDefinitionRegistry;


/**
 * Loads a new schema from a source
 */
@FunctionalInterface
public interface SchemaLoader {

    enum Type {
        IDL,
        INTROSPECTION
    }

    TypeDefinitionRegistry load();
}
