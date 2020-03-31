package com.atlassian.braid;

import graphql.language.FieldDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.atlassian.braid.LinkArgument.ArgumentSource.OBJECT_FIELD;
import static com.atlassian.braid.java.util.BraidOptionals.firstNonEmpty;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/**
 * Utils for helping to navigate types
 */
public final class TypeUtils {

    public static final String QUERY_FIELD_NAME = "query";
    public static final String MUTATION_FIELD_NAME = "mutation";

    public static final String DEFAULT_QUERY_TYPE_NAME = "Query";
    public static final String DEFAULT_MUTATION_TYPE_NAME = "Mutation";

    private TypeUtils() {
    }

    static ObjectTypeDefinition createDefaultQueryTypeDefinition() {
        return new ObjectTypeDefinition(DEFAULT_QUERY_TYPE_NAME);
    }


    static ObjectTypeDefinition createDefaultMutationTypeDefinition() {
        return new ObjectTypeDefinition(DEFAULT_MUTATION_TYPE_NAME);
    }

    public static ObjectTypeDefinition addQueryTypeToSchema(TypeDefinitionRegistry typeDefinitionRegistry, ObjectTypeDefinition objectTypeDefinition) {
        addObjectTypeToSchemaDefinition(typeDefinitionRegistry, QUERY_FIELD_NAME, DEFAULT_QUERY_TYPE_NAME);
        typeDefinitionRegistry.add(objectTypeDefinition);
        return objectTypeDefinition;
    }

    public static void addMutationTypeToSchema(TypeDefinitionRegistry typeDefinitionRegistry, ObjectTypeDefinition objectTypeDefinition) {
        addObjectTypeToSchemaDefinition(typeDefinitionRegistry, MUTATION_FIELD_NAME, DEFAULT_MUTATION_TYPE_NAME);
        typeDefinitionRegistry.add(objectTypeDefinition);
    }

    private static void addObjectTypeToSchemaDefinition(TypeDefinitionRegistry registry,
                                                        String operationFieldName,
                                                        String operationTypeName) {
        registry.schemaDefinition()
                .orElseThrow(IllegalStateException::new) // by now the schema definition should have been created
                .getOperationTypeDefinitions()
                .add(new OperationTypeDefinition(operationFieldName, new TypeName(operationTypeName)));

    }

    /**
     * Finds the query field definitions
     *
     * @param registry the type registry to look into
     *
     * @return the optional query fields definitions
     */
    public static Optional<List<FieldDefinition>> findQueryFieldDefinitions(TypeDefinitionRegistry registry) {
        return findQueryType(registry)
                .map(ObjectTypeDefinition::getFieldDefinitions);
    }

    /**
     * Finds the query type definition.
     *
     * @param registry the type registry to look into
     *
     * @return the optional query type
     */

    public static Optional<ObjectTypeDefinition> findQueryType(TypeDefinitionRegistry registry) throws IllegalArgumentException {
        return firstNonEmpty(
                () -> findOperationType(registry, TypeUtils::isQueryOperation),
                () -> getObjectTypeDefinitionByName(registry, DEFAULT_QUERY_TYPE_NAME));
    }


    /**
     * Finds the query type definition.
     *
     * @param registry the type registry to look into
     *
     * @return the optional query type
     */

    public static Optional<ObjectTypeDefinition> findMutationType(TypeDefinitionRegistry registry) throws IllegalArgumentException {
        return firstNonEmpty(
                () -> findOperationType(registry, TypeUtils::isMutationOperation),
                () -> getObjectTypeDefinitionByName(registry, DEFAULT_MUTATION_TYPE_NAME));
    }

    private static Optional<ObjectTypeDefinition> findOperationType(TypeDefinitionRegistry registry, Predicate<OperationTypeDefinition> isQueryOperation) {
        return findOperationDefinitions(registry)
                .flatMap(definitions -> findOperationDefinition(definitions, isQueryOperation))
                .flatMap(getObjectTypeDefinition(registry));
    }

    static List<ObjectTypeDefinition> findOperationTypes(TypeDefinitionRegistry registry) {
        return findOperationDefinitions(registry)
                .map(toObjectTypeDefinition(registry))
                .orElse(emptyList());
    }

    public static Optional<List<OperationTypeDefinition>> findOperationDefinitions(TypeDefinitionRegistry registry) {
        return registry.schemaDefinition()
                .map(SchemaDefinition::getOperationTypeDefinitions);
    }

    public static String unwrap(Type wrappedOriginal) {
        Type current = wrappedOriginal;
        while (true) {
            if (current instanceof NonNullType) {
                current = ((NonNullType)current).getType();
            } else if (current instanceof ListType) {
                current = ((ListType) current).getType();
            } else if (current instanceof TypeName) {
                return ((TypeName)current).getName();
            } else {
                throw new IllegalArgumentException("Unexpected type wrapper: " + current);
            }
        }
    }

    static Type retype(Type wrappedOriginal, TypeName replacement) {
        return retype(wrappedOriginal, replacement, t -> true).get();
    }

    static Optional<Type> retype(Type wrappedOriginal, TypeName targetOriginal, TypeName replacement) {
        return retype(wrappedOriginal, replacement,
                t -> t instanceof TypeName && ((TypeName)t).getName().equals(targetOriginal.getName()));
    }

    static Optional<Type> retype(Type wrappedOriginal, TypeName replacement, Predicate<Type> oldTypeMatcher) {
        List<Function<Type, Type>> typeWrapperStack = new ArrayList<>();
        Type current = wrappedOriginal;
        while (true) {
            if (current instanceof NonNullType) {
                typeWrapperStack.add(typeWrapperStack.size(), NonNullType::new);
                current = ((NonNullType)current).getType();
            } else if (current instanceof ListType) {
                typeWrapperStack.add(typeWrapperStack.size(), ListType::new);
                current = ((ListType) current).getType();
            } else if (oldTypeMatcher.test(current)) {
                current = replacement;
                for (Function<Type, Type> wrapper : typeWrapperStack) {
                    current = wrapper.apply(current);
                }
                return Optional.of(current);
            } else {
                return Optional.empty();
            }
        }
    }


    private static Optional<OperationTypeDefinition> findOperationDefinition(List<OperationTypeDefinition> definitions,
                                                                             Predicate<OperationTypeDefinition> isOperation) {
        return definitions.stream()
                .filter(isOperation)
                .findFirst();
    }

    private static boolean isQueryOperation(OperationTypeDefinition operationTypeDefinition) {
        return Objects.equals(operationTypeDefinition.getName(), QUERY_FIELD_NAME);
    }

    private static boolean isMutationOperation(OperationTypeDefinition operationTypeDefinition) {
        return Objects.equals(operationTypeDefinition.getName(), MUTATION_FIELD_NAME);
    }

    /**
     * Filters the top level fields on a query just to the provided list.  If no fields specified, no action is taken.
     *
     * @param registry       the types
     * @param links
     * @param topLevelFields the fields to allow or if empty, all of them
     *
     * @return the registry passed as a parameter, updated
     */
    public static TypeDefinitionRegistry filterQueryType(TypeDefinitionRegistry registry, List<Link> links, String... topLevelFields) {
        List<String> topFields = asList(topLevelFields);
        if (!topFields.isEmpty()) {
            Optional<ObjectTypeDefinition> queryType = findQueryType(registry);

            if (queryType.isPresent()) {
                ObjectTypeDefinition objectTypeDefinition = queryType.get();
                List<String> linkReplacedSourceFromFields = links.stream()
                        .filter(link -> link.getSourceType().equals(objectTypeDefinition.getName()) &&
                                topFields.contains(link.getNewFieldName()))
                        .flatMap(link -> link.getLinkArguments().stream())
                        .filter(linkArgument -> linkArgument.getArgumentSource() == OBJECT_FIELD && linkArgument.isRemoveInputField())
                        .map(LinkArgument::getSourceName)
                        .collect(toList());
                objectTypeDefinition.getFieldDefinitions().removeIf(field -> !linkReplacedSourceFromFields.contains(field.getName()) &&
                        !topFields.contains(field.getName()));
            }
        }
        return registry;
    }

    private static Function<List<OperationTypeDefinition>, List<ObjectTypeDefinition>> toObjectTypeDefinition(TypeDefinitionRegistry registry) {
        return ods -> ods.stream()
                .map(getObjectTypeDefinition(registry))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toList());
    }

    private static Function<OperationTypeDefinition, Optional<ObjectTypeDefinition>> getObjectTypeDefinition(
            TypeDefinitionRegistry registry) {
        return otd -> registry.getType(otd.getType()).map(ObjectTypeDefinition.class::cast);
    }

    private static Optional<ObjectTypeDefinition> getObjectTypeDefinitionByName(TypeDefinitionRegistry registry, String typeName) {
        return registry.getType(typeName).map(ObjectTypeDefinition.class::cast);
    }
}
