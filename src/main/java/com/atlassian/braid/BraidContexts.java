package com.atlassian.braid;

import com.atlassian.braid.java.util.BraidObjects;
import graphql.execution.ExecutionContext;
import graphql.schema.DataFetchingEnvironment;

import static java.util.Objects.requireNonNull;

/**
 * Helper class to access context in a Braid GraphQL execution environment
 */
public final class BraidContexts {

    private BraidContexts() {
    }

    /**
     * Gets the underlying context from the {@link BraidContext} in a given {@link DataFetchingEnvironment}
     *
     * @param env the data fetching env
     * @param <C> the expected type of the context
     * @return the context, as per {@link BraidContext#getContext()}
     */
    public static <C> C get(DataFetchingEnvironment env) {
        return env.<BraidContext<C>>getContext().getContext();
    }

    /**
     * Gets the underlying context from the {@link BraidContext} in a given {@link ExecutionContext}
     *
     * @param executionContext the execution context
     * @param <C>              the expected type of the context
     * @return the context, as per {@link BraidContext#getContext()}
     */
    public static <C> C get(ExecutionContext executionContext) {
        return BraidObjects.<BraidContext<C>>cast(executionContext.getContext()).getContext();
    }

    /**
     * Create a copy of BraidContext with new underlying context
     * @param context existing braidContext try to copy from
     * @param customContext new underlying context which will replace existing one
     * @param <C> the expected type of the custom context
     * @return new BraidContext with new underlying custom context attached
     * */
    public static <C> BraidContext<C> copyWithNewContext(BraidContext<C> context, C customContext) {
        return new MutableBraidContext<>(context, customContext);
    }
}
