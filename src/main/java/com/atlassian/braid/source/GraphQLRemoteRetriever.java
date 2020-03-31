package com.atlassian.braid.source;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Retrieves maps from a remote service.  Meant to be used with {@link QueryExecutorSchemaSource}.
 */
public interface GraphQLRemoteRetriever<C> {

    /**
     * @param query the query to execute
     * @param context        the GraphQL execution context
     * @return the response body of the query
     */
    CompletableFuture<Map<String, Object>> queryGraphQL(Query query, C context);
}
