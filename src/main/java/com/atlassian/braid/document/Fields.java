package com.atlassian.braid.document;

import graphql.language.Field;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SelectionSet;
import graphql.language.TypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeInfo;

import java.util.Optional;

/**
 * Simple utility class to deal with common field operations
 */
final class Fields {

    private Fields() {
    }

    static Optional<TypeDefinition> maybeFindTypeDefinition(TypeDefinitionRegistry schema, TypeInfo typeInfo) {
        return schema.getType(typeInfo.getName());
    }

    static Optional<TypeInfo> maybeGetTypeInfo(ObjectTypeDefinition parent, Field field) {
        return parent.getFieldDefinitions().stream()
                .filter(fd -> fd.getName().equals(field.getName()))
                .findFirst()
                .map(FieldDefinition::getType)
                .map(TypeInfo::typeInfo);
    }

    static String getFieldAliasOrName(Field field) {
        return field.getAlias() != null ? field.getAlias() : field.getName();
    }

    static Field cloneFieldWithNewSelectionSet(Field field, SelectionSet selectionSet) {
        return field.transform(builder -> builder.selectionSet(selectionSet));
    }

    static Field cloneFieldWithNewName(Field field, String newName) {
        return field.transform(builder -> builder.name(newName));

    }
}
