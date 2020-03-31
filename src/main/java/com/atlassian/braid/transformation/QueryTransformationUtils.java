package com.atlassian.braid.transformation;

import com.atlassian.braid.FieldTransformationContext;
import com.atlassian.braid.source.TrimFieldsSelection;
import com.atlassian.braid.source.VariableNamespacingGraphQLQueryVisitor;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.NodeTraverser;
import graphql.language.NodeVisitor;
import graphql.language.OperationDefinition;
import graphql.schema.DataFetchingEnvironment;

import java.util.List;

class QueryTransformationUtils {

    static OperationDefinition getOperationDefinition(DataFetchingEnvironment environment) {
        return environment.getExecutionContext().getOperationDefinition();
    }

    static FieldWithCounter cloneTrimAndAliasField(FieldTransformationContext fieldTransformationContext, List<Integer> usedCounterIds,
                                                   DataFetchingEnvironment environment, boolean dontTrimFirstField) {
        Field field = environment.getField().deepCopy();
        field.setAlias(field.getName() + fieldTransformationContext.getCounter().incrementAndGet());
        usedCounterIds.add(fieldTransformationContext.getCounter().get());

        List<FragmentDefinition> referencedFragments = TrimFieldsSelection.trimFieldSelection(fieldTransformationContext.getSchemaSource(), environment, field, dontTrimFirstField);
        return new FieldWithCounter(field, fieldTransformationContext.getCounter().get(), referencedFragments);
    }

    static void addFieldToQuery(FieldTransformationContext fieldTransformationContext,
                                DataFetchingEnvironment environment,
                                OperationDefinition operationDefinition,
                                FieldWithCounter field) {
        final NodeVisitor variableNameSpacer =
                new VariableNamespacingGraphQLQueryVisitor(field.counter, operationDefinition,
                        fieldTransformationContext.getVariables(), environment,
                        fieldTransformationContext.getQueryOp());
        field.referencedFragments.forEach(d -> {
            NodeTraverser nodeTraverser = new NodeTraverser();
            nodeTraverser.preOrder(variableNameSpacer, d);
            fieldTransformationContext.getDocument().getDefinitions().add(d);
        });

        NodeTraverser nodeTraverser = new NodeTraverser();
        nodeTraverser.preOrder(variableNameSpacer, field.field);
        fieldTransformationContext.getQueryOp().getSelectionSet().getSelections().add(field.field);
    }

}
