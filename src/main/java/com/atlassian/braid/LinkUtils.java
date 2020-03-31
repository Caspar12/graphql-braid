package com.atlassian.braid;

import com.atlassian.braid.java.util.BraidFutures;
import graphql.language.Argument;
import graphql.language.FieldDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.VariableDefinition;
import graphql.schema.DataFetchingEnvironment;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.atlassian.braid.TypeUtils.findQueryFieldDefinitions;
import static com.atlassian.braid.source.NamespacedVariableReference.namespacedVariableReference;
import static java.lang.String.format;

public final class LinkUtils {

    public static CompletableFuture<List<ResolvedArgument>> resolveArgumentsForLink(Link link,
                                                                                    SchemaSource schemaSource,
                                                                                    DataFetchingEnvironment environment,
                                                                                    ArgumentValueProvider valueProvider,
                                                                                    int fieldCounter) {
        FieldDefinition queryField = findTopLevelQueryField(link, schemaSource);
        @SuppressWarnings("unchecked") CompletableFuture<ResolvedArgument>[] futures = link.getLinkArguments().stream()
                .map(linkArgument -> createArgument(linkArgument, queryField, environment, valueProvider, fieldCounter))
                .toArray(CompletableFuture[]::new);
        return BraidFutures.all(Collectors.toList(), futures);
    }

    private static CompletableFuture<ResolvedArgument> createArgument(LinkArgument linkArgument,
                                                                      FieldDefinition queryField,
                                                                      DataFetchingEnvironment environment,
                                                                      ArgumentValueProvider valueProvider,
                                                                      int fieldCounter) {
        final String variableName = linkArgument.getQueryArgumentName() + fieldCounter;
        InputValueDefinition inputValueDefinition = findInputValueDefinition(queryField, linkArgument);
        VariableDefinition variableDefinition = new VariableDefinition(variableName, inputValueDefinition.getType());
        Argument fieldArgument = new Argument(linkArgument.getQueryArgumentName(), namespacedVariableReference(variableName));
        CompletableFuture<Object> futureValue = valueProvider.fetchValueForArgument(linkArgument, environment);
        return futureValue.thenApply(value ->
                new ResolvedArgument(fieldArgument, variableDefinition, value, linkArgument));
    }


    private static FieldDefinition findTopLevelQueryField(Link link, SchemaSource schemaSource) {
        return findQueryFieldDefinitions(schemaSource.getPrivateSchema())
                .orElseThrow(() -> new IllegalStateException("No query field definition in private schema of "
                        + schemaSource.getNamespace()))
                .stream()
                .filter(f -> f.getName().equals(link.getTopLevelQueryField()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(format("No query field '%s' found for link in %s.",
                        link.getTopLevelQueryField(), schemaSource.getNamespace())));
    }

    private static InputValueDefinition findInputValueDefinition(FieldDefinition definition, LinkArgument linkArgument) {
        return definition.getInputValueDefinitions().stream()
                .filter(iv -> iv.getName().equals(linkArgument.getQueryArgumentName()))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(format("Query field '%s' does not contain argument named '%s'",
                                definition.getName(), linkArgument.getQueryArgumentName())));
    }

    public static class ResolvedArgument {
        private final Argument argument;
        private final VariableDefinition variableDefinition;
        private final Object value;
        private LinkArgument linkArgument;

        private ResolvedArgument(Argument argument,
                                 VariableDefinition variableDefinition,
                                 Object value,
                                 LinkArgument linkArgument) {
            this.argument = argument;
            this.variableDefinition = variableDefinition;
            this.value = value;
            this.linkArgument = linkArgument;
        }

        public Argument getArgument() {
            return argument;
        }

        public VariableDefinition getVariableDefinition() {
            return variableDefinition;
        }

        public Object getValue() {
            return value;
        }

        public LinkArgument getLinkArgument() {
            return linkArgument;
        }
    }
}
