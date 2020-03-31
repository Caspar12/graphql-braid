package com.atlassian.braid;

import javax.annotation.Nullable;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Represent an argument that is passed to a {@link Link}. Argument can be sourced from object field
 * ({@link ArgumentSource#OBJECT_FIELD}, field argument({@link ArgumentSource#FIELD_ARGUMENT} or context
 * ({@link ArgumentSource#CONTEXT}.
 */
public class LinkArgument {
    private final String sourceName;
    private final String queryArgumentName;
    private final ArgumentSource argumentSource;
    private final String targetFieldMatchingArgument;
    private final boolean removeInputField;
    private final boolean nullable;

    private LinkArgument(String sourceName, String queryArgumentName,
                         ArgumentSource argumentSource,
                         @Nullable String targetFieldMatchingArgument,
                         boolean removeInputField,
                         boolean nullable) {
        this.sourceName = requireNonNull(sourceName, "sourceName");
        this.queryArgumentName = requireNonNull(queryArgumentName, "queryArgumentName");
        this.argumentSource = requireNonNull(argumentSource, "argumentSource");
        this.targetFieldMatchingArgument = targetFieldMatchingArgument;
        this.removeInputField = removeInputField;
        this.nullable = nullable;
    }

    /**
     * @return name of the property that will be extracted from {@link #argumentSource}
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * @return the name of the query argument used to retrieve the linked object. This argument will be given the value
     * extracted from property named {@link #getSourceName()}/
     */
    public String getQueryArgumentName() {
        return queryArgumentName;
    }

    /**
     * @return source of the property from which the value will be extracted.
     */
    public ArgumentSource getArgumentSource() {
        return argumentSource;
    }

    /**
     * @return name of the field in the object returned by the Link that corresponds to this argument. It is used to
     * bypass quering if possible, by returning the argument value directly.
     */
    @Nullable
    public String getTargetFieldMatchingArgument() {
        return targetFieldMatchingArgument;
    }

    /**
     * Only used if {@link #getArgumentSource()} is set to {@link ArgumentSource#OBJECT_FIELD}.
     *
     * @return if true the source field for an argument will be removed from the object in a schema.
     */
    public boolean isRemoveInputField() {
        return removeInputField;
    }

    /**
     * @return whether a null source field value should prompt a remote link call
     */
    public boolean isNullable() {
        return nullable;
    }

    @Override
    public String toString() {
        return "LinkArgument{" +
                "sourceName='" + sourceName + '\'' +
                ", queryArgumentName='" + queryArgumentName + '\'' +
                ", argumentSource=" + argumentSource +
                ", targetFieldMatchingArgument='" + targetFieldMatchingArgument + '\'' +
                ", removeInputField=" + removeInputField +
                ", nullable=" + nullable +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LinkArgument that = (LinkArgument) o;
        return removeInputField == that.removeInputField &&
                Objects.equals(sourceName, that.sourceName) &&
                Objects.equals(queryArgumentName, that.queryArgumentName) &&
                argumentSource == that.argumentSource &&
                nullable == that.nullable &&
                Objects.equals(targetFieldMatchingArgument, that.targetFieldMatchingArgument);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceName, queryArgumentName, argumentSource, targetFieldMatchingArgument,
                removeInputField, nullable);
    }

    public enum ArgumentSource {FIELD_ARGUMENT, CONTEXT, OBJECT_FIELD}

    public static LinkArgumentBuilder newLinkArgument() {
        return new LinkArgumentBuilder();
    }

    public static class LinkArgumentBuilder {
        private String sourceName;
        private String queryArgumentName;
        private ArgumentSource argumentSource;
        private String targetFieldMatchingArgument;
        private boolean removeInputField;
        private boolean nullable;

        public LinkArgumentBuilder sourceName(String sourceName) {
            this.sourceName = sourceName;
            return this;
        }

        public LinkArgumentBuilder queryArgumentName(String queryArgumentName) {
            this.queryArgumentName = queryArgumentName;
            return this;
        }

        public LinkArgumentBuilder argumentSource(ArgumentSource argumentSource) {
            this.argumentSource = argumentSource;
            return this;
        }

        public LinkArgumentBuilder targetFieldMatchingArgument(String targetFieldMatchingArgument) {
            this.targetFieldMatchingArgument = targetFieldMatchingArgument;
            return this;
        }

        public LinkArgumentBuilder removeInputField(boolean removeInputField) {
            this.removeInputField = removeInputField;
            return this;
        }

        public LinkArgumentBuilder nullable(boolean nullable) {
            this.nullable = nullable;
            return this;
        }

        public LinkArgument build() {
            return new LinkArgument(sourceName, queryArgumentName, argumentSource, targetFieldMatchingArgument,
                    removeInputField, nullable);
        }
    }
}
