package com.atlassian.braid.transformation;

import graphql.language.Field;
import graphql.language.FragmentDefinition;

import java.util.List;

class FieldWithCounter {
    final Field field;
    final int counter;
    final List<FragmentDefinition> referencedFragments;

    FieldWithCounter(Field field, int counter, List<FragmentDefinition> referencedFragments) {
        this.field = field;
        this.counter = counter;
        this.referencedFragments = referencedFragments;
    }
}
