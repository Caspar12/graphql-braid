package com.atlassian.braid;

import graphql.execution.DataFetcherResult;
import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;

public interface FieldRename {

    String getSourceName();

    String getBraidName();

    default void applyForQuery(DataFetchingEnvironment dataFetchingEnvironment, Field field) {
    }

    default DataFetcherResult<Object> unapplyForResult(Field field, DataFetcherResult<Object> dataFetcherResult) {
        return dataFetcherResult;
    }

    static FieldRename from(String source, String target) {
        return new FieldRename() {
            @Override
            public String getSourceName() {
                return source;
            }

            @Override
            public String getBraidName() {
                return target;
            }

        };
    }
}
