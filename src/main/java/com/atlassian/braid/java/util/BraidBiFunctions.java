package com.atlassian.braid.java.util;

import java.util.function.BiFunction;

/**
 * Utility class to extend BiFunction with custom functions needed for mapping process.
 * */
public final class BraidBiFunctions {

    private BraidBiFunctions(){}
    /**
     * Similar as Function.identity. It will return a function that always returns its second input argument.
     *
     * @param <T> the type of the first input argument to the function
     * @param <U> the type of the second input argument to the function
     * @param <R> the type pf return object, which is casted from type U
     * @return a BiFunction that always returns its second input argument as Type R.
     */
    public static <T, U, R> BiFunction<T, U, R> right() {
        return (t, u) -> BraidObjects.cast(u);
    }
}
