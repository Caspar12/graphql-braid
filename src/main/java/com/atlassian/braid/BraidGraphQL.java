package com.atlassian.braid;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.dataloader.DataLoaderRegistry;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public class BraidGraphQL {
    private final DataLoaderRegistry dlr;
    private final Supplier<GraphQL> graphQLFactory;

    BraidGraphQL(Supplier<DataLoaderRegistry> dlr, Supplier<GraphQL> graphQLFactory) {
        this.dlr = requireNonNull(dlr.get());
        this.graphQLFactory = requireNonNull(graphQLFactory);
    }

    /**
     * Executes a GraphQL query asynchronously from the {@link ExecutionInput}
     *
     * @param executionInput {@link ExecutionInput}
     *
     * @return a promise to an {@link ExecutionResult} which can include errors
     */
    @Nonnull
    public CompletableFuture<ExecutionResult> execute(ExecutionInput executionInput) {
        final GraphQL graphQL = this.graphQLFactory.get();

        final ExecutionInput newInput = executionInput
                .transform(builder -> builder
                        .dataLoaderRegistry(dlr)
                        .context(new MutableBraidContext<>(executionInput.getContext())));

        return graphQL.executeAsync(newInput);
    }
}