package com.atlassian.braid.source;

import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.Reader;
import java.util.function.Supplier;

import static com.atlassian.braid.source.SchemaUtils.loadSchema;

public class ReaderSupplierSchemaLoader implements SchemaLoader {

    private final Type type;
    private final Supplier<Reader> readerSupplier;

    public ReaderSupplierSchemaLoader(SchemaLoader.Type type, Supplier<Reader> readerSupplier) {
        this.type = type;
        this.readerSupplier = readerSupplier;
    }

    @Override
    public TypeDefinitionRegistry load() {
        return loadSchema(type, readerSupplier.get());
    }
}
