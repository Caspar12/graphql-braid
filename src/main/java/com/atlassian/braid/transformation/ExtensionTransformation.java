package com.atlassian.braid.transformation;

import com.atlassian.braid.BraidContext;
import com.atlassian.braid.Extension;
import com.atlassian.braid.FieldTransformation;
import com.atlassian.braid.FieldTransformationContext;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.source.NamespacedVariableReference;
import graphql.execution.DataFetcherResult;
import graphql.language.Argument;
import graphql.language.Field;
import graphql.language.InputValueDefinition;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.language.Type;
import graphql.language.VariableDefinition;
import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.atlassian.braid.TypeUtils.findQueryFieldDefinitions;
import static com.atlassian.braid.source.NamespacedVariableReference.namespacedVariableReference;
import static com.atlassian.braid.transformation.QueryTransformationUtils.addFieldToQuery;
import static com.atlassian.braid.transformation.QueryTransformationUtils.cloneTrimAndAliasField;
import static com.atlassian.braid.transformation.QueryTransformationUtils.getOperationDefinition;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;


/**
 * A field mutation that processes a extension to a target data source and generates fields for to fetch from that source
 */
public class ExtensionTransformation implements FieldTransformation {

    private final Extension extension;

    ExtensionTransformation(Extension extension) {
        this.extension = requireNonNull(extension);
    }

    @Override
    public CompletableFuture<List<Field>> apply(DataFetchingEnvironment environment, FieldTransformationContext context) {

        Map<String, Object> source;
        if (environment.getSource() instanceof DataFetcherResult) {
            source = environment.<DataFetcherResult<Map<String, Object>>>getSource().getData();
        } else if (environment.getSource() instanceof Map) {
            source = environment.getSource();
        } else {
            throw new IllegalArgumentException("Unexpected source type: " + environment.getSource());
        }
        Object targetId = source.get(extension.getOn());

        final OperationDefinition operationDefinition = getOperationDefinition(environment);

        final FieldWithCounter field = cloneTrimAndAliasField(
                context,
                new ArrayList<>(),
                environment,
                true);

        addQueryVariable(
                context,
                targetId,
                field);
        addFieldToQuery(context, environment, operationDefinition, field);

        SelectionSet selectionSet = SelectionSet.newSelectionSet()
                .selections(((BraidContext) environment.getContext()).getMissingFields(environment.getFieldType().getName()))
                .build();
        field.field.setSelectionSet(selectionSet);

        return CompletableFuture.completedFuture(singletonList(field.field));
    }

    private void addQueryVariable(FieldTransformationContext fieldTransformationContext, Object targetId, FieldWithCounter field) {
        final String variableName = extension.getBy().getArg() + fieldTransformationContext.getCounter();

        field.field.setName(extension.getBy().getQuery());
        field.field.setArguments(linkQueryArgumentAsList(extension, variableName));

        fieldTransformationContext.getQueryOp().getVariableDefinitions().add(linkQueryVariableDefinition(extension, variableName,
                fieldTransformationContext.getSchemaSource()));
        fieldTransformationContext.getVariables().put(variableName, targetId);
    }

    private static List<Argument> linkQueryArgumentAsList(Extension link, String variableName) {
        return singletonList(new Argument(link.getBy().getArg(), namespacedVariableReference(variableName)));
    }

    private static VariableDefinition linkQueryVariableDefinition(Extension link, String variableName, SchemaSource schemaSource) {
        return new VariableDefinition(variableName, findArgumentType(schemaSource, link));
    }

    private static Type findArgumentType(SchemaSource schemaSource, Extension link) {
        return findQueryFieldDefinitions(schemaSource.getPrivateSchema())
                .orElseThrow(IllegalStateException::new)
                .stream()
                .filter(f -> f.getName().equals(link.getBy().getQuery()))
                .findFirst()
                .map(f -> f.getInputValueDefinitions().stream()
                        .filter(iv -> iv.getName().equals(link.getBy().getArg()))
                        .findFirst()
                        .map(InputValueDefinition::getType)
                        .orElseThrow(IllegalArgumentException::new))
                .orElseThrow(IllegalArgumentException::new);
    }


}
