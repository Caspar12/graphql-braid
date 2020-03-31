package com.atlassian.braid.graphql.language;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionStepInfo;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import org.dataloader.DataLoader;

import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * {@link DataFetchingEnvironment} that is identified solely by its source object
 */
public class KeyedDataFetchingEnvironment implements DataFetchingEnvironment {

    private final DataFetchingEnvironment delegate;
    private final Object source;

    public KeyedDataFetchingEnvironment(DataFetchingEnvironment delegate) {
        this.delegate = delegate;
        this.source = delegate.getSource();
    }

    public <T> T getSource() {
        return delegate.getSource();
    }

    public Map<String, Object> getArguments() {
        return delegate.getArguments();
    }

    public boolean containsArgument(String name) {
        return delegate.containsArgument(name);
    }

    public <T> T getArgument(String name) {
        return delegate.getArgument(name);
    }

    public <T> T getContext() {
        return delegate.getContext();
    }

    public <T> T getRoot() {
        return delegate.getRoot();
    }

    public GraphQLFieldDefinition getFieldDefinition() {
        return delegate.getFieldDefinition();
    }

    public List<Field> getFields() {
        return delegate.getFields();
    }

    public Field getField() {
        return delegate.getField();
    }

    public GraphQLOutputType getFieldType() {
        return delegate.getFieldType();
    }

    public ExecutionStepInfo getExecutionStepInfo() {
        return delegate.getExecutionStepInfo();
    }

    public GraphQLType getParentType() {
        return delegate.getParentType();
    }

    public GraphQLSchema getGraphQLSchema() {
        return delegate.getGraphQLSchema();
    }

    public Map<String, FragmentDefinition> getFragmentsByName() {
        return delegate.getFragmentsByName();
    }

    public ExecutionId getExecutionId() {
        return delegate.getExecutionId();
    }

    public DataFetchingFieldSelectionSet getSelectionSet() {
        return delegate.getSelectionSet();
    }

    public ExecutionContext getExecutionContext() {
        return delegate.getExecutionContext();
    }

    @Override
    public <K, V> DataLoader<K, V> getDataLoader(String s) {
        return delegate.getDataLoader(s);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeyedDataFetchingEnvironment that = (KeyedDataFetchingEnvironment) o;
        return Objects.equals(getSource(), that.getSource());
    }

    @Override
    public int hashCode() {
        return Objects.hash(source);
    }
}
