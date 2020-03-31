package com.atlassian.braid;

public final class Extension {
    /**
     * The type being extended
     */
    private final String type;

    /**
     * The name of the field used as the argument to query the 'extension'
     */
    private final String on;


    /**
     * The type used to extend {@link #type} (usually?) defined in another schema namespace
     */
    private final By by;

    public Extension(String type, String on, By by) {
        this.type = type;
        this.on = on;
        this.by = by;
    }

    public String getType() {
        return type;
    }

    public By getBy() {
        return by;
    }

    public String getOn() {
        return on;
    }

    public static final class By {
        /**
         * the namespace of the schema where to find the extension type
         */
        private final SchemaNamespace namespace;

        /**
         * The name of the type in the schema referenced by {@link #namespace}
         */
        private final String type;

        /**
         * The name of the query field to fetch extension objects
         */
        private final String query;

        /**
         * The name of the query argument used to fetch the extension objects, this will be filled with the value of the {@link Extension#on} field value.
         */
        private final String arg;

        public By(SchemaNamespace namespace, String type, String query, String arg) {
            this.namespace = namespace;
            this.type = type;
            this.query = query;
            this.arg = arg;
        }

        public SchemaNamespace getNamespace() {
            return namespace;
        }

        public String getType() {
            return type;
        }

        public String getQuery() {
            return query;
        }

        public String getArg() {
            return arg;
        }
    }
}
