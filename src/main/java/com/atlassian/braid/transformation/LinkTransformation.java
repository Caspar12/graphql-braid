package com.atlassian.braid.transformation;

import com.atlassian.braid.FieldKey;
import com.atlassian.braid.FieldTransformation;
import com.atlassian.braid.FieldTransformationContext;
import com.atlassian.braid.Link;
import com.atlassian.braid.LinkUtils.ResolvedArgument;
import com.atlassian.braid.java.util.BraidFutures;
import graphql.execution.DataFetcherResult;
import graphql.language.Argument;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.VariableDefinition;
import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.atlassian.braid.ArgumentValueProvider.staticArgumentValue;
import static com.atlassian.braid.BatchLoaderUtils.getTargetIdsFromEnvironment;
import static com.atlassian.braid.LinkUtils.resolveArgumentsForLink;
import static com.atlassian.braid.transformation.QueryTransformationUtils.addFieldToQuery;
import static com.atlassian.braid.transformation.QueryTransformationUtils.cloneTrimAndAliasField;
import static com.atlassian.braid.transformation.QueryTransformationUtils.getOperationDefinition;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;


/**
 * A field transformation that processes a link to a target data source and generates fields for to fetch from that source
 */
public class LinkTransformation implements FieldTransformation {

    private final Link link;

    LinkTransformation(Link link) {
        this.link = requireNonNull(link);
    }

    public Link getLink() {
        return link;
    }

    @Override
    public CompletableFuture<List<Field>> apply(DataFetchingEnvironment environment, FieldTransformationContext context) {
        //TODO: why is this cloned, why not just get the selections from it as they are?
        Field cloneOfCurrentField = environment.getField().deepCopy();
        List<Selection> selections = cloneOfCurrentField.getSelectionSet().getSelections();
        Set<String> shortCircuitFields = selectFieldsForShortCircuit(selections, link);
        if (link.isSimpleLink()) {
            return getTargetIdsFromEnvironment(link.getSourceInputFieldName(), environment)
                    .thenCompose(targetIds -> {
                        @SuppressWarnings("unchecked") CompletableFuture<Field>[] futureFields = targetIds.stream()
                                .map(targetId -> transformSimpleLink(targetId, context, environment, shortCircuitFields))
                                .toArray(CompletableFuture[]::new);
                        return BraidFutures.all(Collectors.toList(), futureFields);
                    });
        } else {
            return transformComplexLink(context, environment, shortCircuitFields);
        }
    }

    private CompletableFuture<Field> transformSimpleLink(Object argumentValue,
                                                         FieldTransformationContext context,
                                                         DataFetchingEnvironment environment,
                                                         Set<String> shortCircuitFields) {
        final FieldWithCounter field = cloneTrimAndAliasField(
                context,
                new ArrayList<>(),
                environment,
                true);
        CompletableFuture<List<ResolvedArgument>> args = resolveArgumentsForLink(this.link, context.getSchemaSource(),
                environment, staticArgumentValue(argumentValue), field.counter);
        return args.thenApply(resolvedArguments ->
                createFieldForSelection(field, environment, context, shortCircuitFields, resolvedArguments));
    }

    private CompletableFuture<List<Field>> transformComplexLink(FieldTransformationContext context,
                                                                DataFetchingEnvironment environment,
                                                                Set<String> shortCircuitFields) {
        final FieldWithCounter field = cloneTrimAndAliasField(
                context,
                new ArrayList<>(),
                environment,
                true);
        CompletableFuture<List<ResolvedArgument>> args = resolveArgumentsForLink(this.link, context.getSchemaSource(),
                environment, link.getArgumentValueProvider(), field.counter);
        return args.thenApply(resolvedArguments ->
                singletonList(createFieldForSelection(field, environment, context, shortCircuitFields, resolvedArguments)));
    }

    private Field createFieldForSelection(FieldWithCounter field,
                                          DataFetchingEnvironment environment,
                                          FieldTransformationContext context,
                                          Set<String> shortCircuitFields,
                                          List<ResolvedArgument> resolvedArguments) {

        if (!areAllArgumentsValuesAllowed(resolvedArguments)) {
            context.getShortCircuitedData().put(new FieldKey(field.field.getAlias()), null);
        } else if (shortCircuitFields != null) {
            Map<String, Object> result = resolvedArguments.stream()
                    .filter(arg -> shortCircuitFields.contains(arg.getLinkArgument().getTargetFieldMatchingArgument()))
                    .collect(Collectors.toMap(arg -> arg.getLinkArgument().getTargetFieldMatchingArgument(), ResolvedArgument::getValue));
            context.getShortCircuitedData().put(new FieldKey(field.field.getAlias()), result);
        } else {
            OperationDefinition operationDefinition = getOperationDefinition(environment);
            createQueryField(
                    context,
                    field,
                    resolvedArguments);
            addFieldToQuery(context, environment, operationDefinition, field);
        }
        return field.field;
    }

    private void createQueryField(FieldTransformationContext fieldTransformationContext,
                                  FieldWithCounter field,
                                  List<ResolvedArgument> resolvedArguments) {
        if (link.getCustomTransformation() != null) {
            link.getCustomTransformation().createQuery(field.field, null);
            return;
        }
        field.field.setName(link.getTopLevelQueryField());

        List<Argument> fieldArguments = new ArrayList<>(resolvedArguments.size());
        List<VariableDefinition> variableDefinitions = fieldTransformationContext.getQueryOp()
                .getVariableDefinitions();
        for (ResolvedArgument resolvedArgument : resolvedArguments) {
            variableDefinitions.add(resolvedArgument.getVariableDefinition());
            fieldArguments.add(resolvedArgument.getArgument());
            fieldTransformationContext.getVariables()
                    .put(resolvedArgument.getVariableDefinition().getName(), resolvedArgument.getValue());
        }

        field.field.setArguments(fieldArguments);
    }


    private static boolean areAllArgumentsValuesAllowed(List<ResolvedArgument> resolvedArguments) {
        return resolvedArguments.stream()
                .noneMatch(arg -> arg.getValue() == null && !arg.getLinkArgument().isNullable());
    }

    /**
     * Gets list of fields that can be short-circuited or null if selection contains at least one field that cannot be
     * short-circuited.
     */
    private static Set<String> selectFieldsForShortCircuit(List<Selection> selections, Link link) {
        Set<String> selectionFields = new HashSet<>();
        for (Selection selection : selections) {
            if (!(selection instanceof Field)) {
                return null; // this means that any fragment will make this return false
            }

            String fieldName = ((Field) selection).getName();
            if (!link.isFieldMatchingArgument(fieldName)) {
                return null;
            }
            selectionFields.add(fieldName);
        }
        return selectionFields;
    }

    @Override
    public DataFetcherResult<Object> unapply(DataFetchingEnvironment environment, DataFetcherResult<Object> dataFetcherResult) {
        if (link.getCustomTransformation() != null) {
            return link.getCustomTransformation().unapplyForResult(environment.getField(), dataFetcherResult);
        }
        return dataFetcherResult;
    }
}
