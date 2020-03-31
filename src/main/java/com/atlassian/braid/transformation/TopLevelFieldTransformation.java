package com.atlassian.braid.transformation;

import com.atlassian.braid.Extension;
import com.atlassian.braid.FieldRename;
import com.atlassian.braid.FieldTransformation;
import com.atlassian.braid.FieldTransformationContext;
import graphql.execution.DataFetcherResult;
import graphql.language.Field;
import graphql.language.SelectionSet;
import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.atlassian.braid.transformation.QueryTransformationUtils.addFieldToQuery;
import static com.atlassian.braid.transformation.QueryTransformationUtils.cloneTrimAndAliasField;
import static com.atlassian.braid.transformation.QueryTransformationUtils.getOperationDefinition;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;


/**
 * A transformation that will fetch the top level field from a remote source, optionally changing the name of the field
 */
public class TopLevelFieldTransformation implements FieldTransformation {

    private final FieldRename fieldRename;
    private final List<Extension> extensions;

    public TopLevelFieldTransformation(FieldRename fieldRename, List<Extension> extensions) {
        this.fieldRename = fieldRename;
        this.extensions = extensions;
    }

    @Override
    public CompletableFuture<List<Field>> apply(DataFetchingEnvironment environment, FieldTransformationContext context) {

        FieldWithCounter field = cloneTrimAndAliasField(context, new ArrayList<>(), environment, false);

        // maybe a link did change the toplevel field already
        if (fieldRename.getBraidName().equals(field.field.getName()) && !fieldRename.getSourceName().equals(field.field.getName())) {
            field.field.setName(fieldRename.getSourceName());
        }
        fieldRename.applyForQuery(environment, field.field);

        addExtensionOnFields(field);
        addFieldToQuery(context, environment, getOperationDefinition(environment), field);

        return completedFuture(singletonList(field.field));
    }

    private void addExtensionOnFields(FieldWithCounter field) {
        extensions.forEach(ext -> {
            if (!selectionSetContainsField(field.field.getSelectionSet(), ext.getOn())) {
                field.field.getSelectionSet().getSelections().add(new Field(ext.getOn()));
            }
        });
    }

    @Override
    public DataFetcherResult<Object> unapply(DataFetchingEnvironment environment, DataFetcherResult<Object> dataFetcherResult) {
        return fieldRename.unapplyForResult(environment.getField(), dataFetcherResult);
    }

    private static boolean selectionSetContainsField(SelectionSet selectionSet, String name) {
        return selectionSet.getSelections().stream()
                .filter(selection -> selection instanceof Field)
                .map(field -> (Field) field)
                .anyMatch(field -> field.getName().equals(name));
    }
}
