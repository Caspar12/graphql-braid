package com.atlassian.braid.transformation;

import org.dataloader.BatchLoader;

import java.util.Map;

/**
 * Injects one or more field transformations into the schema during the braiding stage
 */
public interface SchemaTransformation {

    /**
     * @param braidingContext the context of the schema being braided
     *
     * @return A map of batch loader key and instances to be made available during query execution as data loaders
     */
    Map<String, BatchLoader> transform(BraidingContext braidingContext);
}
