package com.atlassian.braid;

public interface TypeRename {

    String getSourceName();

    String getBraidName();

    static TypeRename from(String source, String target) {
        return new TypeRename() {
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
