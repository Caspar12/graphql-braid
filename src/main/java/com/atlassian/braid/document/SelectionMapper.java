package com.atlassian.braid.document;

import com.atlassian.braid.document.SelectionOperation.OperationResult;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Selection;

import static com.atlassian.braid.document.SelectionOperation.identityResult;
import static com.atlassian.braid.document.SelectionOperation.result;
import static java.util.Objects.requireNonNull;

abstract class SelectionMapper<C> {

    static SelectionMapper getSelectionMapper(Selection selection) {
        if (selection instanceof Field) {
            return new FieldMapper((Field) selection);
        } else if (selection instanceof FragmentSpread) {
            return new FragmentSpreadMapper((FragmentSpread) selection);
        } else if (selection instanceof InlineFragment) {
            return new InlineFragmentMapper((InlineFragment) selection);
        } else {
            throw new IllegalStateException("Unknown selection type: " + selection.getClass());
        }
    }

    abstract OperationResult map(MappingContext<C> mappingContext);

    private static class FieldMapper<C> extends SelectionMapper<C> {
        private final Field field;

        private FieldMapper(Field field) {
            this.field = requireNonNull(field);
        }

        OperationResult map(MappingContext<C> mappingContext) {
            final MappingContext<C> fieldMappingContext = mappingContext.forField(field);

            if (fieldMappingContext.getTypeDefinition() instanceof ObjectTypeDefinition) {
                return fieldMappingContext.getTypeMapper()
                        .map(typeMapper -> typeMapper.apply(fieldMappingContext, field.getSelectionSet()))
                        .map(mappingResult -> mappingResult.toOperationResult(field, fieldMappingContext))
                        .orElseGet(() -> result(field));
            } else {
                return identityResult(field);
            }
        }
    }

    private static class FragmentSpreadMapper<C> extends SelectionMapper<C> {
        private final FragmentSpread fragmentSpread;

        private FragmentSpreadMapper(FragmentSpread fragmentSpread) {
            this.fragmentSpread = requireNonNull(fragmentSpread);
        }

        @Override
        OperationResult map(MappingContext<C> mappingContext) {
            final FragmentDefinition fragmentDefinition = mappingContext.getFragmentDefinition(fragmentSpread);
            return mappingContext.getTypeMapper()
                    .map(tm -> tm.apply(mappingContext, fragmentDefinition.getSelectionSet()))
                    .map(mappingResult -> mappingResult.toOperationResult(fragmentSpread))
                    .orElseGet(() -> result(fragmentSpread));
        }
    }

    private static class InlineFragmentMapper<C> extends SelectionMapper<C> {
        private final InlineFragment inlineFragment;

        public InlineFragmentMapper(InlineFragment inlineFragment) {
            this.inlineFragment = requireNonNull(inlineFragment);
        }

        @Override
        OperationResult map(MappingContext<C> mappingContext) {
            final MappingContext<C> inlineFragmentMappingContext = mappingContext.forInlineFragment(inlineFragment);

            return inlineFragmentMappingContext.getTypeMapper()
                    .map(typeMapper -> typeMapper.apply(inlineFragmentMappingContext, inlineFragment.getSelectionSet()))
                    .map(mappingResult -> mappingResult.toOperationResult(inlineFragment))
                    .orElseGet(() -> result(inlineFragment));
        }
    }
}
