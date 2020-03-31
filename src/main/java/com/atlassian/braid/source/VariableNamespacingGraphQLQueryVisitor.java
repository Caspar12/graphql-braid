package com.atlassian.braid.source;

import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.Node;
import graphql.language.NodeVisitorStub;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.Type;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.schema.DataFetchingEnvironment;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Map;

import static com.atlassian.braid.source.NamespacedVariableReference.namespacedVariableReference;
import static java.util.stream.Collectors.toList;

public class VariableNamespacingGraphQLQueryVisitor extends NodeVisitorStub {
    private final int counter;
    private final OperationDefinition queryType;
    private final Map<String, Object> variables;
    private final DataFetchingEnvironment environment;
    private final OperationDefinition queryOp;

    public VariableNamespacingGraphQLQueryVisitor(int counter,
                                                  OperationDefinition operationDefinition,
                                                  Map<String, Object> variables,
                                                  DataFetchingEnvironment environment,
                                                  OperationDefinition queryOp) {
        this.counter = counter;
        this.queryType = operationDefinition;
        this.variables = variables;
        this.environment = environment;
        this.queryOp = queryOp;
    }

    @Override
    public TraversalControl visitField(Field node, TraverserContext<Node> traverserContext) {
        node.setArguments(node.getArguments().stream().map(this::namespaceReferences).collect(toList()));
        node.setDirectives(node.getDirectives().stream().map(this::namespaceReferences).collect(toList()));
        return TraversalControl.CONTINUE;
    }

    private Argument namespaceReferences(Argument arg) {
        return new Argument(arg.getName(), namespaceReferences(arg.getValue()));
    }

    private Directive namespaceReferences(Directive original) {
        return new Directive(original.getName(), original.getArguments().stream().map(this::namespaceReferences).collect(toList()));
    }

    private Value namespaceReferences(Value value) {
        final Value transformedValue;
        if (value instanceof VariableReference) {
            transformedValue = maybeNamespaceReference((VariableReference) value);
        } else if (value instanceof ObjectValue) {
            transformedValue = namespaceReferencesForObjectValue((ObjectValue) value);
        } else if (value instanceof ArrayValue) {
            transformedValue = namespaceReferencesForArrayValue((ArrayValue) value);
        } else {
            transformedValue = value;
        }
        return transformedValue;
    }

    private ObjectValue namespaceReferencesForObjectValue(ObjectValue value) {
        return new ObjectValue(
                value.getChildren().stream()
                        .map(ObjectField.class::cast)
                        .map(o -> new ObjectField(o.getName(), namespaceReferences(o.getValue())))
                        .collect(toList()));
    }

    private ArrayValue namespaceReferencesForArrayValue(ArrayValue value) {
        return new ArrayValue(
                value.getChildren().stream()
                        .map(Value.class::cast)
                        .map(this::namespaceReferences)
                        .collect(toList()));
    }

    private VariableReference maybeNamespaceReference(VariableReference value) {
        return isVariableAlreadyNamespaced(value) ? value : namespaceVariable(value);
    }

    private VariableReference namespaceVariable(VariableReference varRef) {
        final String newName = varRef.getName() + counter;

        final VariableReference value =  namespacedVariableReference(newName);
        final Type type = findVariableType(varRef, queryType);

        variables.put(newName, environment.getExecutionContext().getVariables().get(varRef.getName()));
        queryOp.getVariableDefinitions().add(new VariableDefinition(newName, type));
        return value;
    }

    private boolean isVariableAlreadyNamespaced(VariableReference varRef) {
        return varRef instanceof NamespacedVariableReference;
    }

    private static Type findVariableType(VariableReference varRef, OperationDefinition queryType) {
        return queryType.getVariableDefinitions()
                .stream()
                .filter(d -> d.getName().equals(varRef.getName()))
                .map(VariableDefinition::getType)
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

}
