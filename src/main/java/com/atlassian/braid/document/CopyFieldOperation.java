package com.atlassian.braid.document;

import com.atlassian.braid.java.util.BraidBiFunctions;
import com.atlassian.braid.java.util.BraidObjects;
import graphql.language.Field;
import graphql.language.SelectionSet;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.atlassian.braid.document.DocumentMapperPredicates.all;
import static com.atlassian.braid.document.DocumentMapperPredicates.fieldNamed;
import static com.atlassian.braid.document.Fields.cloneFieldWithNewName;
import static com.atlassian.braid.document.Fields.getFieldAliasOrName;
import static com.atlassian.braid.document.SelectionMapper.getSelectionMapper;
import static com.atlassian.braid.document.SelectionOperation.result;
import static com.atlassian.braid.mapper.MapperOperations.copy;
import static java.util.Objects.requireNonNull;

final class CopyFieldOperation<C, T, R> extends AbstractTypeOperation<Field> {

    private static final String ANY_NAME = "*";

    private final Function<Field, String> target;

    private final BiFunction<C, T, R> transform;

    CopyFieldOperation() {
        this(ANY_NAME, ANY_NAME);
    }

    CopyFieldOperation(String key, String target) {
        this(copyPredicate(key), copyTarget(target), BraidBiFunctions.right());
    }

    CopyFieldOperation(String key, String target, BiFunction<C, T, R> transform) {
        this(copyPredicate(key), copyTarget(target), transform);
    }

    private CopyFieldOperation(Predicate<Field> predicate, Function<Field, String> target,
                               BiFunction<C, T, R> transform) {
        super(Field.class, predicate);
        this.target = requireNonNull(target);
        this.transform = requireNonNull(transform);
    }

    @Override
    protected OperationResult applyToType(MappingContext mappingContext, Field field) {
        return getSelectionSet(field)
                .map(__ -> getSelectionMapper(field).map(mappingContext)) // graph node (object field)
                .orElseGet(() -> mapLeaf(mappingContext, field)); // graph leaf ('scalar' field)
    }

    private OperationResult mapLeaf(MappingContext<C> mappingContext, Field field) {
        final String targetKey = target.apply(field);
        return result(
                cloneFieldWithNewName(field, targetKey),
                copy(mappingContext.getSpringPath(targetKey),
                        getFieldAliasOrName(field),
                        t -> transform.apply(mappingContext.getCustomContext(), BraidObjects.cast(t))));
    }

    private static Optional<SelectionSet> getSelectionSet(Field field) {
        return Optional.ofNullable(field.getSelectionSet());
    }

    private static Predicate<Field> copyPredicate(String key) {
        return Objects.equals("*", key) ? all() : fieldNamed(key);
    }

    private static Function<Field, String> copyTarget(String target) {
        return Objects.equals(ANY_NAME, target) ? Fields::getFieldAliasOrName : __ -> target;
    }
}
