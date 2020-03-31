package com.atlassian.braid.transformation;

import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.BatchLoaderEnvironment;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.Map;


/**
 * Context information used by {@link SchemaTransformation} instances when braiding the schema
 */
public class BraidingContext {

    private final Map<SchemaNamespace, BraidSchemaSource> dataSources;
    private final TypeDefinitionRegistry registry;
    private final RuntimeWiring.Builder runtimeWiringBuilder;
    private final ObjectTypeDefinition queryObjectTypeDefinition;
    private final ObjectTypeDefinition mutationObjectTypeDefinition;
    private final BatchLoaderEnvironment batchLoaderEnvironment;

    public BraidingContext(Map<SchemaNamespace, BraidSchemaSource> dataSources,
                           TypeDefinitionRegistry registry,
                           RuntimeWiring.Builder runtimeWiringBuilder,
                           ObjectTypeDefinition queryObjectTypeDefinition,
                           ObjectTypeDefinition mutationObjectTypeDefinition,
                           BatchLoaderEnvironment batchLoaderEnvironment) {

        this.dataSources = dataSources;
        this.registry = registry;
        this.runtimeWiringBuilder = runtimeWiringBuilder;
        this.queryObjectTypeDefinition = queryObjectTypeDefinition;
        this.mutationObjectTypeDefinition = mutationObjectTypeDefinition;
        this.batchLoaderEnvironment = batchLoaderEnvironment;
    }

    public Map<SchemaNamespace, BraidSchemaSource> getDataSources() {
        return dataSources;
    }

    public TypeDefinitionRegistry getRegistry() {
        return registry;
    }

    public RuntimeWiring.Builder getRuntimeWiringBuilder() {
        return runtimeWiringBuilder;
    }

    public ObjectTypeDefinition getQueryObjectTypeDefinition() {
        return queryObjectTypeDefinition;
    }

    public ObjectTypeDefinition getMutationObjectTypeDefinition() {
        return mutationObjectTypeDefinition;
    }

    BatchLoaderEnvironment getBatchLoaderEnvironment(){
        return batchLoaderEnvironment;
    }
}
