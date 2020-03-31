package com.atlassian.braid.graphql.language;


import com.atlassian.braid.TypeUtils;
import graphql.Assert;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.FieldDefinition;
import graphql.language.FragmentDefinition;
import graphql.language.InlineFragment;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.Node;
import graphql.language.NonNullType;
import graphql.language.ObjectField;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationDefinition;
import graphql.language.SelectionSet;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLNullableType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.validation.DocumentVisitor;

import java.util.ArrayList;
import java.util.List;

import static graphql.introspection.Introspection.SchemaMetaFieldDef;
import static graphql.introspection.Introspection.TypeMetaFieldDef;
import static graphql.introspection.Introspection.TypeNameMetaFieldDef;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;


/**
 * Basically the same thing as {@link graphql.validation.TraversalContext} but traverses the schema document
 * ({@link TypeDefinitionRegistry} instead of the wired {@link GraphQLSchema}.
 *
 * This is probably very buggy, hence the commented out bits that are probably important.
 */
public class DefinitionTraversalContext implements DocumentVisitor {
    private final TypeDefinitionRegistry registry;
    private final List<TypeDefinition> outputTypeStack = new ArrayList<>();
    private final List<TypeDefinition> parentTypeStack = new ArrayList<>();
    private final List<TypeDefinition> inputTypeStack = new ArrayList<>();
    private final List<FieldDefinition> fieldDefStack = new ArrayList<>();
    private final List<String> nameStack = new ArrayList<>();

    DefinitionTraversalContext(TypeDefinitionRegistry typeDefinitionRegistry) {
        this.registry = typeDefinitionRegistry;
    }

    @Override
    public void enter(Node node, List<Node> path) {
        if (node instanceof OperationDefinition) {
            enterImpl((OperationDefinition) node);
        } else if (node instanceof SelectionSet) {
            enterImpl((SelectionSet) node);
        } else if (node instanceof Field) {
            enterImpl((Field) node);
        } else if (node instanceof Directive) {
//            enterImpl((Directive) node);
        } else if (node instanceof InlineFragment) {
            enterImpl((InlineFragment) node);
        } else if (node instanceof FragmentDefinition) {
            enterImpl((FragmentDefinition) node);
        } else if (node instanceof VariableDefinition) {
            enterImpl((VariableDefinition) node);
        } else if (node instanceof Argument) {
            enterImpl((Argument) node);
        } else if (node instanceof ArrayValue) {
            enterImpl((ArrayValue) node);
        } else if (node instanceof ObjectField) {
//            enterImpl((ObjectField) node);
        }
    }


    private void enterImpl(SelectionSet selectionSet) {
//        GraphQLUnmodifiedType rawType = unwrapAll(getOutputType());
//        GraphQLCompositeType parentType = null;
//        if (rawType instanceof GraphQLCompositeType) {
//            parentType = (GraphQLCompositeType) rawType;
//        }
        addParentType(getOutputType());
    }

    private void enterImpl(Field field) {
        enterName(field.getName());
        TypeDefinition parentType = getParentType();
        FieldDefinition fieldDefinition = null;
        if (parentType != null) {
            fieldDefinition = getFieldDef(registry, parentType, field);
        }
        addFieldDef(fieldDefinition);
        addOutputType(fieldDefinition != null && fieldDefinition.getType() != null ? registry.getType(fieldDefinition.getType()).orElse(null) : null);
    }

    private void enterImpl(OperationDefinition operationDefinition) {
        if (operationDefinition.getOperation() == OperationDefinition.Operation.MUTATION) {
            addOutputType(TypeUtils.findMutationType(registry).orElseThrow(IllegalStateException::new));
        } else if (operationDefinition.getOperation() == OperationDefinition.Operation.QUERY) {
            addOutputType(TypeUtils.findQueryType(registry).orElseThrow(IllegalStateException::new));
        } else if (operationDefinition.getOperation() == OperationDefinition.Operation.SUBSCRIPTION) {
            throw new UnsupportedOperationException("subscriptions not supported yet");
        } else {
            Assert.assertShouldNeverHappen();
        }
    }

    private void enterImpl(InlineFragment inlineFragment) {
        TypeName typeCondition = inlineFragment.getTypeCondition();
        TypeDefinition type;
        if (typeCondition != null) {
            type = registry.getType(typeCondition.getName()).orElseThrow(IllegalArgumentException::new);
        } else {
            type = getParentType();
        }
        addOutputType(type);
    }

    private void enterImpl(FragmentDefinition fragmentDefinition) {
        enterName(fragmentDefinition.getName());
        TypeDefinition type = registry.getType(fragmentDefinition.getTypeCondition().getName()).orElseThrow(IllegalStateException::new);
        addOutputType(type);
    }

    private void enterImpl(VariableDefinition variableDefinition) {
        TypeDefinition type = getTypeDefinitionFromAST(registry, variableDefinition.getType());
        addInputType(type instanceof InputObjectTypeDefinition ? (InputObjectTypeDefinition) type : null);
    }

    private static TypeDefinition getTypeDefinitionFromAST(TypeDefinitionRegistry registry, Type type) {
        if (type instanceof ListType) {
            return getTypeDefinitionFromAST(registry, ((ListType) type).getType());
        } else if (type instanceof NonNullType) {
            return getTypeDefinitionFromAST(registry, ((NonNullType) type).getType());
        }

        return registry.getType(((TypeName) type).getName()).orElseThrow(IllegalArgumentException::new);
    }

    private void enterImpl(Argument argument) {
        InputValueDefinition argumentType = null;
//        if (getDirective() != null) {
//            argumentType = find(getDirective().getArguments(), argument.getName());
        if (getFieldDef() != null) {
            argumentType = find(getFieldDef().getInputValueDefinitions(), argument.getName());
        }

        addInputType(argumentType != null ? registry.getType(argumentType.getType()).orElse(null) : null);
    }

    private void enterImpl(ArrayValue arrayValue) {
//        GraphQLNullableType nullableType = getNullableType(getInputType());
//        GraphQLInputType inputType = null;
//        if (isList(nullableType)) {
//            inputType = (GraphQLInputType) unwrapOne(nullableType);
//        }
        addInputType(getInputType());
    }

    private InputValueDefinition find(List<InputValueDefinition> arguments, String name) {
        for (InputValueDefinition argument : arguments) {
            if (argument.getName().equals(name)) return argument;
        }
        return null;
    }


    @Override
    public void leave(Node node, List<Node> ancestors) {
        if (node instanceof OperationDefinition) {
            outputTypeStack.remove(outputTypeStack.size() - 1);
        } else if (node instanceof SelectionSet) {
            parentTypeStack.remove(parentTypeStack.size() - 1);
        } else if (node instanceof Field) {
            leaveName(((Field) node).getName());
            fieldDefStack.remove(fieldDefStack.size() - 1);
            outputTypeStack.remove(outputTypeStack.size() - 1);
        } else if (node instanceof Directive) {
//            directive = null;
        } else if (node instanceof InlineFragment) {
            outputTypeStack.remove(outputTypeStack.size() - 1);
        } else if (node instanceof FragmentDefinition) {
            leaveName(((FragmentDefinition) node).getName());
            outputTypeStack.remove(outputTypeStack.size() - 1);
        } else if (node instanceof VariableDefinition) {
            inputTypeStack.remove(inputTypeStack.size() - 1);
        } else if (node instanceof Argument) {
//            argument = null;
            inputTypeStack.remove(inputTypeStack.size() - 1);
        } else if (node instanceof ArrayValue) {
            inputTypeStack.remove(inputTypeStack.size() - 1);
        } else if (node instanceof ObjectField) {
            inputTypeStack.remove(inputTypeStack.size() - 1);
        }
    }

    private void enterName(String name) {
        if (!isEmpty(name)) {
            nameStack.add(name);
        }
    }

    private void leaveName(String name) {
        if (!isEmpty(name)) {
            nameStack.remove(nameStack.size() - 1);
        }
    }

    private boolean isEmpty(String name) {
        return name == null || name.isEmpty();
    }

    private GraphQLNullableType getNullableType(GraphQLType type) {
        return (GraphQLNullableType) (isNonNull(type) ? unwrapOne(type) : type);
    }

    /**
     * @return can be null if current node does not have a OutputType associated: for example
     * if the current field is unknown
     */
    public TypeDefinition getOutputType() {
        return lastElement(outputTypeStack);
    }

    private void addOutputType(TypeDefinition type) {
        outputTypeStack.add(type);
    }


    private <T> T lastElement(List<T> list) {
        if (list.size() == 0) return null;
        return list.get(list.size() - 1);
    }

    /**
     * @return can be null if the parent is not a CompositeType
     */
    public TypeDefinition getParentType() {
        return lastElement(parentTypeStack);
    }

    private void addParentType(TypeDefinition compositeType) {
        parentTypeStack.add(compositeType);
    }

    public TypeDefinition getInputType() {
        return lastElement(inputTypeStack);
    }

    private void addInputType(TypeDefinition graphQLInputType) {
        inputTypeStack.add(graphQLInputType);
    }

    public FieldDefinition getFieldDef() {
        return lastElement(fieldDefStack);
    }

    public List<String> getQueryPath() {
        if (nameStack.isEmpty()) {
            return null;
        }
        return new ArrayList<>(nameStack);
    }

    private void addFieldDef(FieldDefinition fieldDefinition) {
        fieldDefStack.add(fieldDefinition);
    }



    private FieldDefinition getFieldDef(TypeDefinitionRegistry registry, TypeDefinition parentType, Field field) {
        if (TypeUtils.findQueryType(registry).map(def -> def.equals(parentType)).orElse(false)) {
            if (field.getName().equals(SchemaMetaFieldDef.getName())) {
                return FieldDefinition.newFieldDefinition().name(SchemaMetaFieldDef.getName()).build();
            }
            if (field.getName().equals(TypeMetaFieldDef.getName())) {
                return FieldDefinition.newFieldDefinition().name(TypeMetaFieldDef.getName()).build();
            }
        }
        if (field.getName().equals(TypeNameMetaFieldDef.getName())
                && (parentType instanceof ObjectTypeDefinition ||
                parentType instanceof InterfaceTypeDefinition ||
                parentType instanceof UnionTypeDefinition)) {
            return FieldDefinition.newFieldDefinition().name(TypeNameMetaFieldDef.getName()).build();
        }
        if (parentType instanceof ObjectTypeDefinition) {
            return ((ObjectTypeDefinition) parentType).getFieldDefinitions().stream()
                    .filter(fd -> fd.getName().equals(field.getName()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }
}
