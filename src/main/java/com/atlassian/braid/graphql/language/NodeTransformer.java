package com.atlassian.braid.graphql.language;

import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.Definition;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.DirectiveLocation;
import graphql.language.Document;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValue;
import graphql.language.EnumValueDefinition;
import graphql.language.Field;
import graphql.language.FieldDefinition;
import graphql.language.FloatValue;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.IntValue;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;

import java.util.List;

import static java.util.stream.Collectors.toList;


/**
 * Parent class for transforming a node recursively.
 */
@SuppressWarnings("WeakerAccess")
public class NodeTransformer {

    public Argument argument(Argument node) {
        return node.transform(b ->
                b.value(value(node.getValue())));
    }

    
    public ArrayValue arrayValue(ArrayValue node) {
        return node.transform(b ->
                b.values(node.getValues().stream()
                        .map(this::value)
                        .collect(toList())));
    }

    
    public BooleanValue booleanValue(BooleanValue node) {
        return node;
    }

    
    public Directive directive(Directive node) {
        return node.transform(b ->
                b.arguments(node.getArguments().stream()
                        .map(this::argument)
                        .collect(toList())));
    }

    
    public DirectiveDefinition directiveDefinition(DirectiveDefinition node) {
        return node.transform(b ->
                b.inputValueDefinitions(transformInputValues( node.getInputValueDefinitions()))
                        .directiveLocations(node.getDirectiveLocations().stream()
                                .map(this::directiveLocation)
                                .collect(toList())));
    }

    
    public DirectiveLocation directiveLocation(DirectiveLocation node) {
        return node;
    }

    
    public Document document(Document node) {
        return node.transform(b ->
                b.definitions(node.getDefinitions().stream()
                        .map(this::definition)
                        .collect(toList())));
    }

    
    public EnumTypeDefinition enumTypeDefinition(EnumTypeDefinition node) {
        return node.transform(b ->
                b.directives(transformDirectives(node.getDirectives()))
                        .enumValueDefinitions(node.getEnumValueDefinitions().stream()
                                .map(this::enumValueDefinition)
                                .collect(toList())));
    }

    
    public EnumValue enumValue(EnumValue node) {
        return node;
    }

    
    public EnumValueDefinition enumValueDefinition(EnumValueDefinition node) {
        return node.transform(b ->
                b.directives(transformDirectives(node.getDirectives())));
    }

    
    public Field field(Field node) {
        return node.transform(b ->
                b.arguments(node.getArguments().stream()
                        .map(this::argument)
                        .collect(toList()))
                        .selectionSet(selectionSet(node.getSelectionSet()))
                        .directives(transformDirectives(node.getDirectives())));
    }

    
    public FieldDefinition fieldDefinition(FieldDefinition node) {
        return node.transform(b ->
                b.directives(transformDirectives(node.getDirectives()))
                        .type(type(node.getType()))
                        .inputValueDefinitions(transformInputValues(node.getInputValueDefinitions())));
    }

    
    public FloatValue floatValue(FloatValue node) {
        return node;
    }

    
    public FragmentDefinition fragmentDefinition(FragmentDefinition node) {
        return node.transform(b ->
                b.directives(transformDirectives(node.getDirectives()))
                        .selectionSet(selectionSet(node.getSelectionSet()))
                        .typeCondition(typeName(node.getTypeCondition())));
    }

    
    public FragmentSpread fFragmentSpread(FragmentSpread node) {
        return node.transform(b ->
                b.directives(transformDirectives(node.getDirectives())));
    }

    
    public InlineFragment inlineFragment(InlineFragment node) {
        return node.transform(b ->
                b.directives(transformDirectives(node.getDirectives()))
                        .selectionSet(selectionSet(node.getSelectionSet()))
                        .typeCondition(typeName(node.getTypeCondition())));
    }

    
    public InputObjectTypeDefinition inputObjectTypeDefinition(InputObjectTypeDefinition node) {
        return node.transform(b ->
                b.directives(transformDirectives(node.getDirectives()))
                        .inputValueDefinitions(transformInputValues(node.getInputValueDefinitions())));
    }

    
    public InputValueDefinition inputValueDefinition(InputValueDefinition node) {
        return node.transform(b ->
                b.directives(transformDirectives(node.getDirectives())));
    }

    
    public IntValue intValue(IntValue node) {
        return node;
    }

    
    public InterfaceTypeDefinition interfaceTypeDefinition(InterfaceTypeDefinition node) {
        return node.transform(b ->
                b.directives(transformDirectives(node.getDirectives()))
                        .definitions(transformFieldDefinitions(node.getFieldDefinitions())));
    }

    
    public ListType listType(ListType node) {
        return node.transform(b ->
                b.type(type(node.getType())));
    }

    
    public NonNullType nonNullType(NonNullType node) {
        return node.transform(b ->
                b.type(type(node.getType())));
    }

    
    public NullValue nullValue(NullValue node) {
        return node;
    }

    
    public ObjectField objectField(ObjectField node) {
        return node;
    }

    
    public ObjectTypeDefinition objectTypeDefinition(ObjectTypeDefinition node) {
        return node.transform(b ->
                b.directives(transformDirectives(node.getDirectives()))
                        .fieldDefinitions(transformFieldDefinitions(node.getFieldDefinitions())));
    }

    
    public ObjectValue objectValue(ObjectValue node) {
        return node.transform(b ->
                b.objectFields(node.getObjectFields().stream()
                    .map(this::objectField)
                    .collect(toList())));
    }

    
    public OperationDefinition operationDefinition(OperationDefinition node) {
        return node.transform(b ->
                b.variableDefinitions(node.getVariableDefinitions().stream()
                        .map(this::variableDefinition)
                        .collect(toList()))
                        .selectionSet(selectionSet(node.getSelectionSet()))
                        .directives(transformDirectives(node.getDirectives())));
    }

    
    public OperationTypeDefinition operationTypeDefinition(OperationTypeDefinition node) {
        return node.transform(b ->
                b.type(type(node.getType())));
    }

    
    public ScalarTypeDefinition scalarTypeDefinition(ScalarTypeDefinition node) {
        return node.transform(b ->
                b.directives(transformDirectives(node.getDirectives())));
    }

    
    public SchemaDefinition schemaDefinition(SchemaDefinition node) {
        return node.transform(b ->
                b.directives(transformDirectives(node.getDirectives()))
                    .operationTypeDefinitions(node.getOperationTypeDefinitions().stream()
                        .map(this::operationTypeDefinition)
                        .collect(toList())));
    }

    
    public SelectionSet selectionSet(SelectionSet node) {
        if (node == null) {
            return null;
        }
        return node.transform(b ->
                b.selections(node.getSelections().stream()
                            .map(this::selection)
                            .collect(toList())));
    }

    
    public StringValue stringValue(StringValue node) {
        return node;
    }

    
    public TypeName typeName(TypeName node) {
        return node;
    }

    
    public UnionTypeDefinition unionTypeDefinition(UnionTypeDefinition node) {
        return node.transform(b ->
                b.directives(transformDirectives(node.getDirectives()))
                        .memberTypes(node.getMemberTypes().stream()
                            .map(this::type)
                            .collect(toList())));
    }

    
    public VariableDefinition variableDefinition(VariableDefinition node) {
        return node.transform(b ->
                b.type(type(node.getType())));
    }

    
    public VariableReference variableReference(VariableReference node) {
        return node;
    }

    protected Value value(Value value) {
        if (value instanceof ArrayValue) {
            return arrayValue((ArrayValue) value);
        } else if (value instanceof BooleanValue) {
            return booleanValue((BooleanValue) value);
        } else if (value instanceof EnumValue) {
            return enumValue((EnumValue) value);
        } else if (value instanceof FloatValue) {
            return floatValue((FloatValue) value);
        } else if (value instanceof IntValue) {
            return intValue((IntValue) value);
        } else if (value instanceof ObjectValue) {
            return objectValue((ObjectValue) value);
        } else if (value instanceof NullValue) {
            return nullValue((NullValue) value);
        } else if (value instanceof StringValue) {
            return stringValue(((StringValue)value));
        } else if (value instanceof VariableReference) {
            return variableReference((VariableReference) value);
        } else {
            // unexpected/unhandled value
            return value;
        }
    }

    protected Definition definition(Definition def) {
        if (def instanceof DirectiveDefinition) {
            return directiveDefinition((DirectiveDefinition) def);
        } else if (def instanceof EnumTypeDefinition) {
            return enumTypeDefinition((EnumTypeDefinition) def);
        } else if (def instanceof FragmentDefinition) {
            return fragmentDefinition((FragmentDefinition) def);
        } else if (def instanceof InputObjectTypeDefinition) {
            return inputObjectTypeDefinition((InputObjectTypeDefinition) def);
        } else if (def instanceof InterfaceTypeDefinition) {
            return interfaceTypeDefinition((InterfaceTypeDefinition) def);
        } else if (def instanceof ObjectTypeDefinition) {
            return objectTypeDefinition((ObjectTypeDefinition) def);
        } else if (def instanceof OperationDefinition) {
            return operationDefinition((OperationDefinition) def);
        } else if (def instanceof ScalarTypeDefinition) {
            return scalarTypeDefinition((ScalarTypeDefinition) def);
        } else if (def instanceof SchemaDefinition) {
            return schemaDefinition((SchemaDefinition) def);
        } else if (def instanceof UnionTypeDefinition) {
            return unionTypeDefinition((UnionTypeDefinition) def);
        } else {
            // unexpected/unhandled type
            return def;
        }
    }

    protected Type type(Type type) {
        if (type instanceof ListType) {
            return listType((ListType) type);
        } else if (type instanceof NonNullType) {
            return nonNullType((NonNullType) type);
        } else if (type instanceof TypeName) {
            return typeName((TypeName) type);
        } else {
            // unexpected/unhandled type
            return type;
        }
    }

    protected Selection selection(Selection sel) {
        if (sel instanceof Field) {
            return field((Field) sel);
        } else if (sel instanceof FragmentSpread) {
            return fFragmentSpread((FragmentSpread) sel);
        } else if (sel instanceof InlineFragment) {
            return inlineFragment((InlineFragment) sel);
        } else {
            // unexpected/unhandled selection
            return sel;
        }
    }

    private List<InputValueDefinition> transformInputValues(List<InputValueDefinition> inputValueDefinitions) {
        return inputValueDefinitions.stream()
                .map(this::inputValueDefinition)
                .collect(toList());
    }

    private List<Directive> transformDirectives(List<Directive> directives) {
        return directives.stream()
                .map(this::directive)
                .collect(toList());
    }

    private List<FieldDefinition> transformFieldDefinitions(List<FieldDefinition> fieldDefinitions) {
        return fieldDefinitions.stream()
                .map(this::fieldDefinition)
                .collect(toList());
    }
}
