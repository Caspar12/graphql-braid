package com.atlassian.braid;

import com.atlassian.braid.source.QueryPartitionFunction;

/**
 * Builder to create BatchLoaderEnvironment
 */
public class BatchLoaderEnvironmentBuilder {
    private QueryPartitionFunction queryPartitionFunc;

    public BatchLoaderEnvironmentBuilder queryPartitionFunction(QueryPartitionFunction queryPartitionFunc) {
        this.queryPartitionFunc = queryPartitionFunc;
        return this;
    }

    public BatchLoaderEnvironment build() {
        return new BatchLoaderEnvironmentImpl(queryPartitionFunc);
    }
}

class BatchLoaderEnvironmentImpl implements BatchLoaderEnvironment {
    private QueryPartitionFunction queryPartitionFunc;

    public BatchLoaderEnvironmentImpl(QueryPartitionFunction queryPartitionFunc) {
        this.queryPartitionFunc = queryPartitionFunc;
    }

    @Override
    public QueryPartitionFunction getQueryPartitionFunction() {
        return queryPartitionFunc;
    }
}
