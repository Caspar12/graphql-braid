package com.atlassian.braid.source;

import graphql.ExecutionInput;
import graphql.language.Document;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static com.atlassian.braid.graphql.language.GraphQLNodes.printNode;

public class Query {
    private final Document query;
    private final String operationName;
    private final Object context;
    private final Object root;
    private final Map<String, Object> variables;


    private Query(Document query, String operationName, Object context, Object root, Map<String, Object> variables) {
        this.query = query;
        this.operationName = operationName;
        this.context = context;
        this.root = root;
        this.variables = variables;
    }

    public ExecutionInput asExecutionInput() {
        return new ExecutionInput(
                printNode(query),
                operationName,
                context,
                root,
                variables
        );
    }

    public Document getQuery() {
        return query;
    }

    public String getOperationName() {
        return operationName;
    }

    public Object getContext() {
        return context;
    }

    public Object getRoot() {
        return root;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    /**
     * This helps you transform the current Query object into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new Query object based on calling build on that builder
     */
    public Query transform(Consumer<Query.Builder> builderConsumer) {
        Query.Builder builder = new Query.Builder()
                .query(this.query)
                .operationName(this.operationName)
                .context(this.context)
                .root(this.root)
                .variables(this.variables);

        builderConsumer.accept(builder);

        return builder.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Query query1 = (Query) o;
        return Objects.equals(getQuery(), query1.getQuery()) &&
                Objects.equals(getOperationName(), query1.getOperationName()) &&
                Objects.equals(getContext(), query1.getContext()) &&
                Objects.equals(getRoot(), query1.getRoot()) &&
                Objects.equals(getVariables(), query1.getVariables());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getQuery(), getOperationName(), getContext(), getRoot(), getVariables());
    }

    @Override
    public String toString() {
        return "Query{" +
                "query=" + query +
                ", operationName='" + operationName + '\'' +
                ", context=" + context +
                ", root=" + root +
                ", variables=" + variables +
                '}';
    }

    /**
     * @return a new builder of Query objects
     */
    public static Query.Builder newQuery() {
        return new Query.Builder();
    }

    public static class Builder {

        private Document query;
        private String operationName;
        private Object context;
        private Object root;
        private Map<String, Object> variables = Collections.emptyMap();

        public Query.Builder query(Document query) {
            this.query = query;
            return this;
        }

        public Query.Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        public Query.Builder context(Object context) {
            this.context = context;
            return this;
        }

        public Query.Builder root(Object root) {
            this.root = root;
            return this;
        }

        public Query.Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        public Query build() {
            return new Query(query, operationName, context, root, variables);
        }
    }
}
