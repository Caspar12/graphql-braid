package com.atlassian.braid;

import graphql.execution.DataFetcherResult;
import graphql.language.Field;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.atlassian.braid.LinkArgument.newLinkArgument;
import static java.util.Objects.requireNonNull;

/**
 * Links a field on a type to another data source.
 */
@SuppressWarnings("WeakerAccess")
public final class Link {

    private final SchemaNamespace sourceNamespace;
    private final String sourceType;

    private final SchemaNamespace targetNamespace;
    private final String targetType;
    private final String newFieldName;

    private final String topLevelQueryField;
    private final boolean simpleLink;
    private final List<LinkArgument> linkArguments;
    private final Set<String> queryFieldsMatchingArguments;
    private final ArgumentValueProvider argumentValueProvider;

    private final boolean noSchemaChangeNeeded;

    public interface CustomTransformation {
        void createQuery(Field field, Object targetId);

        DataFetcherResult<Object> unapplyForResult(Field field, DataFetcherResult<Object> dataFetcherResult);
    }

    private final CustomTransformation customTransformation;

    public Link(SchemaNamespace sourceNamespace,
                String sourceType,
                SchemaNamespace targetNamespace,
                String targetType,
                String newFieldName,
                String topLevelQueryField,
                boolean noSchemaChangeNeeded,
                List<LinkArgument> linkArguments,
                CustomTransformation customTransformation,
                boolean isSimpleLink, ArgumentValueProvider argumentValueProvider) {
        this.sourceNamespace = requireNonNull(sourceNamespace);
        this.sourceType = requireNonNull(sourceType);
        this.targetNamespace = requireNonNull(targetNamespace);
        this.targetType = requireNonNull(targetType);
        this.newFieldName = requireNonNull(newFieldName);
        this.topLevelQueryField = requireNonNull(topLevelQueryField);
        this.linkArguments = requireNonNull(linkArguments, "linkArguments");
        this.noSchemaChangeNeeded = noSchemaChangeNeeded;
        this.simpleLink = isSimpleLink;
        this.customTransformation = customTransformation;
        this.argumentValueProvider = argumentValueProvider == null ? DefaultArgumentValueProvider.INSTANCE : argumentValueProvider;
        queryFieldsMatchingArguments = this.linkArguments.stream()
                .map(LinkArgument::getTargetFieldMatchingArgument)
                .collect(Collectors.toSet());

        ensureLinkIsValid();
    }

    private void ensureLinkIsValid() {
        if (this.simpleLink) {
            if (this.linkArguments.size() != 1) {
                throw new IllegalArgumentException("Simple link requires exactly one LinkArgument.");
            }
            if (this.linkArguments.get(0).getArgumentSource() != LinkArgument.ArgumentSource.OBJECT_FIELD) {
                throw new IllegalArgumentException("Simple link requires argument sourced to be of type OBJECT FIELD");
            }
        }
    }


    /**
     * @deprecated use .newLink() Builder
     */
    @Deprecated
    public static LinkBuilder from(SchemaNamespace sourceNamespace, String sourceType, String newFieldName) {
        return newLink()
                .sourceNamespace(sourceNamespace)
                .sourceType(sourceType)
                .newFieldName(newFieldName);
    }

    /**
     * @deprecated use .newLink() Builder
     */
    @Deprecated
    public static LinkBuilder from(SchemaNamespace sourceNamespace, String sourceType, String newFieldName, String sourceInputFieldName) {
        return newLink()
                .sourceNamespace(sourceNamespace)
                .sourceType(sourceType)
                .newFieldName(newFieldName)
                .sourceInputFieldName(sourceInputFieldName);
    }

    public String getSourceType() {
        return sourceType;
    }

    /**
     * @return the namespace of the schema where the target 'object' should be queried
     */
    public SchemaNamespace getTargetNamespace() {
        return targetNamespace;
    }

    /**
     * @return the type of the target field to which the link exists
     * (e.g the output type of the query to the target schema)
     */
    public String getTargetType() {
        return targetType;
    }


    /**
     * Simple links can only have one argument sourced from the Object field that the link is declared in. Also, in case
     * if the argument is of type list, it will be passed to link one by one. Complex links can have multiple arguments
     * and they are passed to it 'as they are'.
     *
     * @return true if this object represents simple link, false it is a complex link.
     */
    public boolean isSimpleLink() {
        return simpleLink;
    }

    public SchemaNamespace getSourceNamespace() {
        return sourceNamespace;
    }

    /**
     * @return the field name within the {@link #getSourceType() source type} that the link creates
     */
    public String getNewFieldName() {
        return newFieldName;
    }

    /**
     * @return the name of the query field used to retrieve the linked object.
     */
    public String getTopLevelQueryField() {
        return topLevelQueryField;
    }

    /**
     * @return the name of the query argument used to retrieve the linked object. This argument will be given the value
     * denoted by the {@link #getSourceInputFieldName()}  source from field}
     */
    public String getQueryArgumentName() {
        return getSimpleLinkArgument().getQueryArgumentName();
    }

    /**
     * @return the field name within the {@link #getSourceType() source type} that is used to query the linked 'object'
     */
    public String getSourceInputFieldName() {
        return getSimpleLinkArgument().getSourceName();
    }


    public LinkArgument getSimpleLinkArgument() {
        if (!simpleLink) {
            throw new IllegalStateException("Not a simple link.");
        }
        //if simpleLink is true, linkArguments will have exactly one element, it's enforced by the constructor.
        return linkArguments.get(0);
    }

    public List<LinkArgument> getLinkArguments() {
        return linkArguments;
    }

    public boolean isFieldMatchingArgument(String fieldName) {
        return queryFieldsMatchingArguments.contains(fieldName);
    }

    public boolean isNoSchemaChangeNeeded() {
        return noSchemaChangeNeeded;
    }

    public CustomTransformation getCustomTransformation() {
        return customTransformation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Link link = (Link) o;
        return Objects.equals(sourceNamespace, link.sourceNamespace) &&
                Objects.equals(sourceType, link.sourceType) &&
                Objects.equals(targetNamespace, link.targetNamespace) &&
                Objects.equals(targetType, link.targetType) &&
                Objects.equals(newFieldName, link.newFieldName) &&
                Objects.equals(topLevelQueryField, link.topLevelQueryField) &&
                Objects.equals(linkArguments, link.linkArguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceNamespace, sourceType, targetNamespace, targetType, newFieldName, topLevelQueryField,
                linkArguments);
    }

    @Override
    public String toString() {
        return "Link{" +
                "sourceNamespace=" + sourceNamespace +
                ", sourceType='" + sourceType + '\'' +
                ", targetNamespace=" + targetNamespace +
                ", targetType='" + targetType + '\'' +
                ", newFieldName='" + newFieldName + '\'' +
                ", topLevelQueryField='" + topLevelQueryField + '\'' +
                ", linkArguments=" + linkArguments +
                ", simpleLink=" + simpleLink +
                '}';
    }

    /**
     * @deprecated use {@link #newSimpleLink()}.
     */
    public static LinkBuilder newLink() {
        return new LinkBuilder();
    }

    /**
     * Simple link is a link that can have only one argument coming from a source object field. If argument
     * is of type list, each element will be passed to query target field one by one.
     *
     * @return a builder for a simple link.
     */
    public static LinkBuilder newSimpleLink() {
        return new LinkBuilder();
    }

    /**
     * Complex links can have multiple arguments coming from different sources. Arguments are passed to query
     * fields as they are.
     *
     * @return a builder for a complex link.
     */
    public static ComplexLinkBuilder newComplexLink() {
        return new ComplexLinkBuilder();
    }

    public ArgumentValueProvider getArgumentValueProvider() {
        return argumentValueProvider;
    }

    @SuppressWarnings("unchecked")
    private static abstract class BaseLinkBuilder<T extends BaseLinkBuilder<T>> {
        protected SchemaNamespace targetNamespace;
        protected String targetType;
        protected String topLevelQueryField;

        protected SchemaNamespace sourceNamespace;
        protected String sourceType;

        protected String newFieldName;

        protected boolean noSchemeChangeNeeded;
        protected CustomTransformation customTransformation;

        public T sourceNamespace(SchemaNamespace sourceNamespace) {
            this.sourceNamespace = sourceNamespace;
            return (T) this;
        }

        public T targetNamespace(SchemaNamespace targetNamespace) {
            this.targetNamespace = targetNamespace;
            return (T) this;
        }

        public T targetType(String targetType) {
            this.targetType = targetType;
            return (T) this;
        }

        public T topLevelQueryField(String topLevelQueryField) {
            this.topLevelQueryField = topLevelQueryField;
            return (T) this;
        }

        public T sourceType(String sourceType) {
            this.sourceType = sourceType;
            return (T) this;
        }

        public T newFieldName(String newFieldName) {
            this.newFieldName = newFieldName;
            return (T) this;
        }

        public T noSchemaChangeNeeded(boolean noSchemeChangeNeeded) {
            this.noSchemeChangeNeeded = noSchemeChangeNeeded;
            return (T) this;
        }

        public T customTransformation(CustomTransformation customTransformation) {
            this.customTransformation = customTransformation;
            return (T) this;
        }

        public abstract Link build();
    }

    public static final class LinkBuilder extends BaseLinkBuilder<LinkBuilder> {

        private String queryArgumentName;
        private String sourceInputFieldName;

        private String targetFieldMatchingQueryArgument;

        private boolean removeInputField;
        protected boolean nullable;


        private LinkBuilder() {
        }

        /**
         * @deprecated use single methods
         */
        @Deprecated
        public LinkBuilder to(SchemaNamespace targetNamespace, String targetType) {
            return to(targetNamespace, targetType, null);
        }

        /**
         * @deprecated use single methods
         */
        @Deprecated
        public LinkBuilder to(SchemaNamespace targetNamespace, String targetType, String topLevelQueryField) {
            return to(targetNamespace, targetType, topLevelQueryField, null);
        }

        /**
         * @deprecated use single methods
         */
        @Deprecated
        public LinkBuilder to(SchemaNamespace targetNamespace, String targetType, String topLevelQueryField, String targetFieldMatchingQueryArgument) {
            this.targetNamespace = targetNamespace;
            this.targetType = targetType;
            this.topLevelQueryField = topLevelQueryField;
            this.targetFieldMatchingQueryArgument = targetFieldMatchingQueryArgument;
            return this;
        }

        public LinkBuilder removeInputField(boolean removeInputField) {
            this.removeInputField = removeInputField;
            return this;
        }

        /**
         * @return
         * @deprecated use {@link #removeInputField(boolean)}
         */
        @Deprecated
        public LinkBuilder replaceFromField() {
            return removeInputField(true);
        }


        /**
         * @param queryArgumentName
         * @return
         * @deprecated use {@link #queryArgumentName}
         */
        @Deprecated
        public LinkBuilder argument(String queryArgumentName) {
            return queryArgumentName(queryArgumentName);
        }

        public LinkBuilder setNullable(boolean nullable) {
            this.nullable = nullable;
            return this;
        }

        public LinkBuilder queryArgumentName(String queryArgumentName) {
            this.queryArgumentName = queryArgumentName;
            return this;
        }

        public LinkBuilder sourceInputFieldName(String sourceInputFieldName) {
            this.sourceInputFieldName = sourceInputFieldName;
            return this;
        }

        public LinkBuilder targetFieldMatchingQueryArgument(String targetFieldMatchingQueryArgument) {
            this.targetFieldMatchingQueryArgument = targetFieldMatchingQueryArgument;
            return this;
        }

        public Link build() {
            requireNonNull(sourceNamespace);
            requireNonNull(sourceType);
            requireNonNull(targetNamespace);
            requireNonNull(targetType);
            requireNonNull(newFieldName);
            String topLevelQueryField = Optional.ofNullable(this.topLevelQueryField).orElse(newFieldName);
            String sourceInputFieldName = Optional.ofNullable(this.sourceInputFieldName).orElse(newFieldName);
            String queryArgumentName = Optional.ofNullable(this.queryArgumentName).orElse("id");
            String targetFieldMatchingQueryArgument = Optional.ofNullable(this.targetFieldMatchingQueryArgument).orElse(queryArgumentName);
            LinkArgument linkArgument = newLinkArgument()
                    .argumentSource(LinkArgument.ArgumentSource.OBJECT_FIELD)
                    .queryArgumentName(queryArgumentName)
                    .targetFieldMatchingArgument(targetFieldMatchingQueryArgument)
                    .sourceName(sourceInputFieldName)
                    .removeInputField(removeInputField)
                    .nullable(nullable)
                    .build();
            return new Link(
                    sourceNamespace,
                    sourceType,
                    targetNamespace,
                    targetType,
                    newFieldName,
                    topLevelQueryField,
                    noSchemeChangeNeeded,
                    Collections.singletonList(linkArgument),
                    customTransformation,
                    true,
                    DefaultArgumentValueProvider.INSTANCE);

        }
    }

    public static final class ComplexLinkBuilder extends BaseLinkBuilder<ComplexLinkBuilder> {

        private ArgumentValueProvider argumentValueProvider = DefaultArgumentValueProvider.INSTANCE;
        private final List<LinkArgument> linkArguments = new ArrayList<>();

        public ComplexLinkBuilder linkArgument(LinkArgument argument) {
            linkArguments.add(requireNonNull(argument, "argument"));
            return this;
        }

        public ComplexLinkBuilder linkArguments(Collection<LinkArgument> linkArguments) {
            this.linkArguments.addAll(requireNonNull(linkArguments, "linkArguments"));
            return this;
        }

        public ComplexLinkBuilder linkArgument(Consumer<LinkArgument.LinkArgumentBuilder> argumentBuilderConsumer) {
            LinkArgument.LinkArgumentBuilder argumentBuilder = LinkArgument.newLinkArgument();
            requireNonNull(argumentBuilderConsumer, "argumentBuilderConsumer")
                    .accept(argumentBuilder);
            linkArguments.add(argumentBuilder.build());
            return this;
        }

        public ComplexLinkBuilder argumentValueProvider(ArgumentValueProvider argumentValueProvider) {
            this.argumentValueProvider = requireNonNull(argumentValueProvider, "argumentValueProvider");
            return this;
        }

        @Override
        public Link build() {
            String topLevelQueryField = Optional.ofNullable(this.topLevelQueryField).orElse(newFieldName);
            List<LinkArgument> arguments = Collections.unmodifiableList(new ArrayList<>(this.linkArguments));
            return new Link(
                    sourceNamespace,
                    sourceType,
                    targetNamespace,
                    targetType,
                    newFieldName,
                    topLevelQueryField,
                    noSchemeChangeNeeded,
                    arguments,
                    customTransformation,
                    false,
                    argumentValueProvider
            );
        }
    }
}