package com.atlassian.braid.document;

import graphql.language.ObjectTypeDefinition;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Helper class to build {@link TypeMapper type mappers}
 *
 * @see TypeMapper
 * @see DocumentMapper
 */

public final class TypeMappers {

    private TypeMappers() {
    }

    /**
     * Builds a type mapper for types with a given name
     *
     * @param name the name of the type to match
     * @return a <em>new</em> {@link TypeMapper}
     */
    public static TypeMapper typeNamed(String name) {
        return matching(DocumentMapperPredicates.typeNamed(name));
    }

    static TypeMapper matching(Predicate<ObjectTypeDefinition> predicate) {
        return new TypeMapperImpl(predicate);
    }

    static Optional<TypeMapper> maybeFindTypeMapper(List<TypeMapper> typeMappers, ObjectTypeDefinition definition) {
        return typeMappers
                .stream()
                .filter(typeMapper -> typeMapper.test(definition))
                .findFirst();
    }

}
