package com.atlassian.braid.transformation;

import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.TypeRename;
import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public class BraidTypeDefinition {
    private static final Logger log = LoggerFactory.getLogger(BraidTypeDefinition.class);
    private final BraidSchemaSource source;
    private final TypeDefinition typeDefinition;

    BraidTypeDefinition(BraidSchemaSource source, TypeDefinition typeDefinition) {
        this.source = source;
        this.typeDefinition = typeDefinition;
    }

    public String getName() {
        return source.getTypeRenameFromSourceName(typeDefinition.getName()).map(TypeRename::getBraidName).orElse(typeDefinition.getName());
    }

    public SchemaNamespace getNamespace() {
        return source.getNamespace();
    }

    public List<FieldDefinition> getFieldDefinitions() {
        return getFieldDefinitions(typeDefinition);
    }

    public static List<FieldDefinition> getFieldDefinitions(TypeDefinition typeDefinition) {
        if (typeDefinition instanceof ObjectTypeDefinition) {
            return ((ObjectTypeDefinition) typeDefinition).getFieldDefinitions();
        } else if (typeDefinition instanceof InterfaceTypeDefinition) {
            return ((InterfaceTypeDefinition) typeDefinition).getFieldDefinitions();
        } else {
            return emptyList();
        }
    }

    public TypeDefinition getType() {
        String braidName =
                source.getTypeRenameFromSourceName(typeDefinition.getName())
                        .map(TypeRename::getBraidName)
                        .orElse(typeDefinition.getName());

        return newTypeDefinition(braidName);
    }

    private TypeDefinition newTypeDefinition(String braidName) {
        if (typeDefinition instanceof ObjectTypeDefinition) {
            final ObjectTypeDefinition def = (ObjectTypeDefinition) typeDefinition;
            return ObjectTypeDefinition.newObjectTypeDefinition()
                    .name(braidName)
                    .implementz(aliasImplements(def))
                    .directives(def.getDirectives())
                    .fieldDefinitions(aliasFields(def.getFieldDefinitions())).build();
        } else if (typeDefinition instanceof ScalarTypeDefinition) {
            ScalarTypeDefinition def = (ScalarTypeDefinition) typeDefinition;
            return ScalarTypeDefinition.newScalarTypeDefinition()
                    .name(braidName)
                    .directives(def.getDirectives()).build();
        } else if (typeDefinition instanceof EnumTypeDefinition) {
            EnumTypeDefinition def = (EnumTypeDefinition) typeDefinition;
            return EnumTypeDefinition.newEnumTypeDefinition()
                    .name(braidName)
                    .enumValueDefinitions(def.getEnumValueDefinitions())
                    .directives(def.getDirectives()).build();
        } else if (typeDefinition instanceof InterfaceTypeDefinition) {
            InterfaceTypeDefinition def = (InterfaceTypeDefinition) typeDefinition;
            return InterfaceTypeDefinition.newInterfaceTypeDefinition()
                    .name(braidName)
                    .definitions(aliasFields(def.getFieldDefinitions()))
                    .directives(def.getDirectives()).build();
        } else if (typeDefinition instanceof InputObjectTypeDefinition) {
            InputObjectTypeDefinition def = (InputObjectTypeDefinition) typeDefinition;
            return InputObjectTypeDefinition.newInputObjectDefinition()
                    .name(braidName)
                    .directives(def.getDirectives())
                    .inputValueDefinitions(source.renameInputValueDefinitionsToBraidTypes(def.getInputValueDefinitions())).build();
        } else {
            log.warn("Unhandled type definition for aliasing: {}  Please report as a bug.", typeDefinition);
            return typeDefinition;
        }
    }
    private List<FieldDefinition> aliasFields(List<FieldDefinition> fieldDefinitions) {
        return fieldDefinitions.stream()
                .map(field -> FieldDefinition.newFieldDefinition()
                        .name(field.getName())
                        .type(source.renameTypeToBraidName(field.getType()))
                        .inputValueDefinitions(source.renameInputValueDefinitionsToBraidTypes(field.getInputValueDefinitions()))
                        .directives(field.getDirectives()).build())
                .collect(toList());
    }
    private List<Type> aliasImplements(ObjectTypeDefinition def) {
        return def.getImplements().stream()
                .map(source::renameTypeToBraidName)
                .collect(toList());
    }
}
