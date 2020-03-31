package com.atlassian.braid.document;

import com.atlassian.braid.TypeUtils;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.TypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeInfo;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.atlassian.braid.document.Fields.getFieldAliasOrName;
import static com.atlassian.braid.document.Fields.maybeFindTypeDefinition;
import static com.atlassian.braid.document.Fields.maybeGetTypeInfo;
import static com.atlassian.braid.document.TypeMappers.maybeFindTypeMapper;
import static com.atlassian.braid.java.util.BraidLists.concat;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.concat;

abstract class MappingContext<C> {

    private final TypeDefinitionRegistry schema;
    private final List<TypeMapper> typeMappers;
    private final List<FragmentDefinition> fragmentDefinitions;

    @Nullable
    private final C context;

    MappingContext(MappingContext<C> mappingContext) {
        this(mappingContext.context, mappingContext.schema, mappingContext.typeMappers, mappingContext.fragmentDefinitions);
    }

    MappingContext(C context,
                   TypeDefinitionRegistry schema,
                   List<TypeMapper> typeMappers,
                   List<FragmentDefinition> fragmentDefinitions) {
        this.context = context;
        this.schema = requireNonNull(schema);
        this.typeMappers = requireNonNull(typeMappers);
        this.fragmentDefinitions = requireNonNull(fragmentDefinitions);
    }

    final Optional<TypeMapper> getTypeMapper() {
        return getTypeDefinition() instanceof ObjectTypeDefinition
                ? maybeFindTypeMapper(typeMappers, (ObjectTypeDefinition) getTypeDefinition())
                : Optional.empty();
    }

    private Optional<FragmentDefinition> maybeGetFragmentDefinition(String name) {
        return fragmentDefinitions.stream().filter(fm -> fm.getName().equals(name)).findFirst();
    }

    final FragmentDefinition getFragmentDefinition(FragmentSpread fragmentSpread) {
        return maybeGetFragmentDefinition(fragmentSpread.getName()).orElseThrow(IllegalStateException::new);
    }

    protected List<String> getPath() {
        return Collections.emptyList();
    }

    final String getSpringPath(String targetKey) {
        return concat(getPath().stream(), Stream.of(targetKey)).map(p -> "['" + p + "']").collect(joining());
    }

    boolean inList() {
        return false;
    }

    protected abstract TypeDefinition getTypeDefinition();

    MappingContext forField(Field field) {
        throw new IllegalStateException();
    }

    MappingContext forInlineFragment(InlineFragment inlineFragment) {
        throw new IllegalStateException();
    }

    static <C> RootMappingContext<C> rootContext(C customContext, TypeDefinitionRegistry schema, List<TypeMapper> typeMappers) {
        return new RootMappingContext(customContext, schema, typeMappers, emptyList());
    }

    public C getCustomContext() {
        return context;
    }

    static final class RootMappingContext<C> extends MappingContext<C> {

        RootMappingContext(MappingContext<C> parentContext, List<FragmentDefinition> fragmentMappings) {
            this(parentContext.context, parentContext.schema, parentContext.typeMappers, fragmentMappings);
        }

        RootMappingContext(C customContext, TypeDefinitionRegistry schema, List<TypeMapper> typeMappers, List<FragmentDefinition> fragmentMappings) {
            super(customContext, schema, typeMappers, fragmentMappings);
        }

        FragmentDefinitionMappingContext<C> forFragment(FragmentDefinition definition) {
            return new FragmentDefinitionMappingContext(this, definition);
        }

        OperationDefinitionMappingContext forOperationDefinition(OperationDefinition definition) {
            return new OperationDefinitionMappingContext(this, definition);
        }

        RootMappingContext withFragments(List<FragmentDefinition> fragmentMappings) {
            return new RootMappingContext(this, fragmentMappings);
        }

        @Override
        protected TypeDefinition getTypeDefinition() {
            throw new IllegalStateException();
        }
    }

    static final class FragmentDefinitionMappingContext<C> extends NodeMappingContext<C> {

        private final TypeDefinition typeDefinition;

        FragmentDefinitionMappingContext(MappingContext<C> parentContext, FragmentDefinition definition) {
            super(parentContext);
            this.typeDefinition = findFragmentObjectTypeDefinition(parentContext.schema, definition);
        }

        @Override
        public TypeDefinition getTypeDefinition() {
            return typeDefinition;
        }

        private static ObjectTypeDefinition findFragmentObjectTypeDefinition(TypeDefinitionRegistry schema, FragmentDefinition definition) {
            return schema.getType(definition.getTypeCondition().getName()).map(ObjectTypeDefinition.class::cast).orElseThrow(IllegalStateException::new);
        }
    }

    static abstract class NodeMappingContext<C> extends MappingContext<C> {

        NodeMappingContext(MappingContext<C> parentContext) {
            super(parentContext);
        }

        @Override
        NodeMappingContext forField(Field field) {
            return new FieldMappingContext(this, field);
        }

        @Override
        MappingContext forInlineFragment(InlineFragment inlineFragment) {
            return new InlineFragmentMappingContext(this, inlineFragment);
        }
    }

    static class OperationDefinitionMappingContext extends NodeMappingContext {
        private final TypeDefinition typeDefinition;

        OperationDefinitionMappingContext(MappingContext parentContext, OperationDefinition operationDefinition) {
            super(parentContext);
            this.typeDefinition = findOperationTypeDefinition(parentContext.schema, operationDefinition);
        }

        @Override
        public TypeDefinition getTypeDefinition() {
            return typeDefinition;
        }

        private static ObjectTypeDefinition findOperationTypeDefinition(TypeDefinitionRegistry schema, OperationDefinition op) {
            if (op.getOperation() == OperationDefinition.Operation.QUERY) {
                return TypeUtils.findQueryType(schema).get();
            } else if (op.getOperation() == OperationDefinition.Operation.MUTATION) {
                return TypeUtils.findMutationType(schema).get();
            } else {
                throw new IllegalStateException("Unexpected operation type" + op.getOperation());
            }
        }

        private static Function<List<OperationTypeDefinition>, Optional<OperationTypeDefinition>> maybeFindOperationTypeDefinition(OperationDefinition op) {
            return ops -> ops.stream().filter(isOperationTypeDefinitionForOperationType(op)).findFirst();
        }

        private static Predicate<OperationTypeDefinition> isOperationTypeDefinitionForOperationType(OperationDefinition op) {
            return otd -> otd.getName().equalsIgnoreCase(op.getOperation().name());
        }
    }

    private static class FieldMappingContext extends NodeMappingContext {

        private final Field field;
        private final List<String> parentPath;
        private final TypeInfo typeInfo;
        private final TypeDefinition typeDefinition;

        FieldMappingContext(MappingContext parentContext, Field field) {
            super(parentContext);
            this.field = requireNonNull(field);
            this.parentPath = parentContext.getPath();
            this.typeInfo = maybeGetTypeInfo((ObjectTypeDefinition) parentContext.getTypeDefinition(), field).orElseThrow(IllegalStateException::new);
            this.typeDefinition = maybeFindTypeDefinition(parentContext.schema, this.typeInfo).orElseThrow(IllegalStateException::new);
        }

        @Override
        protected TypeDefinition getTypeDefinition() {
            return typeDefinition;
        }

        @Override
        protected List<String> getPath() {
            return inList() ? emptyList() : concat(parentPath, getFieldAliasOrName(field));
        }

        @Override
        boolean inList() {
            return typeInfo.isList();
        }
    }

    private static class InlineFragmentMappingContext extends NodeMappingContext {

        private final List<String> parentPath;
        private final ObjectTypeDefinition objectTypeDefinition;

        InlineFragmentMappingContext(MappingContext parentContext, InlineFragment inlineFragment) {
            super(parentContext);
            this.parentPath = parentContext.getPath();
            this.objectTypeDefinition = parentContext.schema.getType(inlineFragment.getTypeCondition().getName()).map(ObjectTypeDefinition.class::cast).orElseThrow(IllegalStateException::new);
        }

        @Override
        protected TypeDefinition getTypeDefinition() {
            return objectTypeDefinition;
        }

        @Override
        protected List<String> getPath() {
            return parentPath;
        }

        @Override
        boolean inList() {
            return false;
        }
    }
}
