package com.atlassian.braid.transformation;

public class DataFetcherUtils {

    public static String getDataLoaderKey(String sourceType, String sourceField) {
        return sourceType + "." + sourceField;
    }

    public static String getLinkDataLoaderKey(String sourceType, String sourceField) {
        return sourceType + "." + sourceField + "-link";
    }
}
