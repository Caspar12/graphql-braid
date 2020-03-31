package com.atlassian.braid;

import com.atlassian.braid.source.QueryPartitionFunction;

/**
 * Functions which will be used by batchloader
 * */
public interface BatchLoaderEnvironment {
    /**
     * The function to apply to query executor to split query execution based on partition
     * */
    QueryPartitionFunction getQueryPartitionFunction();
}
