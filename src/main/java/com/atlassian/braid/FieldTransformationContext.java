package com.atlassian.braid;

import com.atlassian.braid.source.QueryExecutorSchemaSource;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * The context of a field in graphql request
 */
public class FieldTransformationContext {

    private final Map<String, Object> variables;
    private final Map<DataFetchingEnvironment, List<FieldKey>> clonedFields;
    private final AtomicInteger counter;
    private final Map<FieldKey, Object> shortCircuitedData;
    private final Document document;
    private final QueryExecutorSchemaSource schemaSource;
    private final OperationDefinition queryOp;
    private final List<Field> missingFields = new ArrayList<>();

    public FieldTransformationContext(QueryExecutorSchemaSource schemaSource, OperationDefinition queryOp) {
        this.schemaSource = schemaSource;
        this.queryOp = queryOp;
        document = Document.newDocument().build();

        document.getDefinitions().add(queryOp);

        variables = new HashMap<>();
        clonedFields = new HashMap<>();

        // start at 99 so that we can find variables already counter-namespaced via startsWith()
        counter = new AtomicInteger(99);

        // this is to gather data we don't need to fetch through batch loaders, e.g. when on the the variable used in
        // the query is fetched
        shortCircuitedData = new HashMap<>();
    }

    public OperationDefinition getQueryOp() {
        return queryOp;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public Document getDocument() {
        return document;
    }

    public Map<DataFetchingEnvironment, List<FieldKey>> getClonedFields() {
        return clonedFields;
    }

    public AtomicInteger getCounter() {
        return counter;
    }

    public QueryExecutorSchemaSource getSchemaSource() {
        return schemaSource;
    }

    public List<Field> getMissingFields() {
        return missingFields;
    }

    public Map<FieldKey, Object> getShortCircuitedData() {
        return shortCircuitedData;
    }

    public void addMissingFields(List<Field> missingFields) {
        this.missingFields.clear();
        this.missingFields.addAll(missingFields);
    }
}
