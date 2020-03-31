package com.atlassian.braid.source;

import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.Reader;
import java.io.StringReader;
import java.util.function.Supplier;

import static com.atlassian.braid.source.SchemaUtils.loadSchema;

public class StringSchemaLoader implements SchemaLoader {

    private final Type type;
    private final String schemaText;

    public StringSchemaLoader(Type type, String schemaText) {
        this.type = type;
        this.schemaText = schemaText;
    }

    @Override
    public TypeDefinitionRegistry load() {
        return loadSchema(type, new StringReader(schemaText));
    }
}
