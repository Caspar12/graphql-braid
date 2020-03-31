package com.atlassian.braid.transformation;

import com.atlassian.braid.Extension;
import com.atlassian.braid.FieldRename;
import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.TypeRename;
import graphql.language.FieldDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static com.atlassian.braid.TypeUtils.DEFAULT_QUERY_TYPE_NAME;
import static com.atlassian.braid.TypeUtils.findMutationType;
import static com.atlassian.braid.TypeUtils.findQueryType;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.toList;

/**
 * This wraps a {@link SchemaSource} to enhance it with helper functions
 */
public final class BraidSchemaSource {
    private static final Logger log = LoggerFactory.getLogger(BraidSchemaSource.class);

    private final SchemaSource schemaSource;

    private final TypeDefinitionRegistry registry;
    private final ObjectTypeDefinition queryType;

    private final ObjectTypeDefinition mutationType;

    public BraidSchemaSource(SchemaSource schemaSource) {
        this.schemaSource = requireNonNull(schemaSource);
        this.registry = schemaSource.getSchema();
        this.queryType = findQueryType(registry).orElse(null);
        this.mutationType = findMutationType(registry).orElse(null);
    }

    public SchemaSource getSchemaSource() {
        return schemaSource;
    }

    public SchemaNamespace getNamespace() {
        return schemaSource.getNamespace();
    }

    List<Extension> getExtensions(String type) {
        return schemaSource.getExtensions().stream().filter(e -> e.getType().equals(type)).collect(toList());
    }

    Optional<TypeRename> getTypeRenameFromSourceName(String type) {
        return schemaSource.getTypeRenames().stream().filter(a -> a.getSourceName().equals(type)).findFirst();
    }

    public Optional<TypeRename> getTypeRenameFromBraidName(String type) {
        return schemaSource.getTypeRenames().stream().filter(a -> a.getBraidName().equals(type)).findFirst();
    }

    public String getBraidTypeName(String sourceTypeName) {
        return getTypeRenameFromSourceName(sourceTypeName)
                .map(TypeRename::getBraidName)
                .orElse(sourceTypeName);
    }

    public String getSourceTypeName(String braidTypeName) {
        return getTypeRenameFromBraidName(braidTypeName)
                .map(TypeRename::getSourceName)
                .orElse(braidTypeName);
    }

    Optional<FieldRename> getQueryFieldRenames(String sourceFieldName) {
        return getFieldRenameBySourceName(schemaSource.getQueryFieldRenames(), sourceFieldName);
    }

    Optional<FieldRename> getMutationFieldRenames(String sourceFieldName) {
        return getFieldRenameBySourceName(schemaSource.getMutationFieldRenames(), sourceFieldName);
    }

    /**
     * Gets the actual source type, accounting for links to query objects that have been renamed when merged into the
     * Braid schema
     */
    public String getLinkBraidSourceType(Link link) {
        return getQueryType()
                .flatMap(maybeGetQueryTypeNameIfLinkSourceIsQueryType(link))
                .orElseGet(link::getSourceType);
    }

    private Function<ObjectTypeDefinition, Optional<String>> maybeGetQueryTypeNameIfLinkSourceIsQueryType(Link link) {
        return originalQueryType ->
                isLinkSourceTypeQueryType(link, originalQueryType) ? Optional.of(DEFAULT_QUERY_TYPE_NAME) : empty();
    }

    private boolean isLinkSourceTypeQueryType(Link link, ObjectTypeDefinition originalQueryType) {
        return originalQueryType.getName().equals(link.getSourceType());
    }

    public Collection<BraidTypeDefinition> getNonOperationTypes() {
        return registry.types()
                .values()
                .stream()
                .filter(this::isNotOperationType)
                .map(td -> new BraidTypeDefinition(this, td))
                .collect(toList());
    }

    boolean hasType(String type) {
        return getType(type).isPresent();
    }

    Optional<TypeDefinition> getType(String type) {
        return registry.getType(type);
    }

    Optional<ObjectTypeDefinition> getQueryType() {
        return Optional.ofNullable(queryType);
    }

    Optional<ObjectTypeDefinition> getMutationType() {
        return Optional.ofNullable(mutationType);
    }

    public TypeDefinitionRegistry getTypeRegistry() {
        return registry;
    }

    Type renameTypeToBraidName(Type type) {
        if (type instanceof TypeName) {
            final String typeName = ((TypeName) type).getName();
            TypeRename typeRename = getTypeRenameFromSourceName(typeName).orElse(TypeRename.from(typeName, typeName));
            return new TypeName(typeRename.getBraidName());
        } else if (type instanceof NonNullType) {
            return new NonNullType(renameTypeToBraidName(((NonNullType) type).getType()));
        } else if (type instanceof ListType) {
            return new ListType(renameTypeToBraidName(((ListType) type).getType()));
        } else {
            // TODO handle all definition types (in a generic enough manner)
            log.error("Definition type : " + type + " not handled correctly for aliases.  Please raise an issue.");
            return type;
        }
    }

    public Type renameTypeToSourceName(Type type) {
        if (type instanceof TypeName) {
            final String typeName = ((TypeName) type).getName();
            TypeRename alias = getTypeRenameFromBraidName(typeName).orElse(TypeRename.from(typeName, typeName));
            return new TypeName(alias.getSourceName());
        } else if (type instanceof NonNullType) {
            return new NonNullType(renameTypeToSourceName(((NonNullType) type).getType()));
        } else if (type instanceof ListType) {
            return new ListType(renameTypeToSourceName(((ListType) type).getType()));
        } else {
            // TODO handle all definition types (in a generic enough manner)
            log.error("Definition type : " + type + " not handled correctly for aliases.  Please raise an issue.");
            return type;
        }
    }

    List<InputValueDefinition> renameInputValueDefinitionsToBraidTypes(List<InputValueDefinition> inputValueDefinitions) {
        return inputValueDefinitions.stream()
                .map(input ->
                        InputValueDefinition.newInputValueDefinition()
                                .name(input.getName())
                                .type(renameTypeToBraidName(input.getType()))
                                .defaultValue(input.getDefaultValue())
                                .directives(input.getDirectives())
                                .build())
                .collect(toList());
    }

    private Optional<FieldRename> getFieldRenameBySourceName(List<FieldRename> fieldRenames, String sourceName) {
        return fieldRenames.stream().filter(a -> a.getSourceName().equals(sourceName)).findFirst();
    }

    private Optional<FieldRename> getFieldRenameByBraidName(List<FieldRename> fieldRenames, String braidFieldName) {
        return fieldRenames.stream().filter(a -> a.getBraidName().equals(braidFieldName)).findFirst();
    }

    private boolean isNotOperationType(TypeDefinition typeDefinition) {
        return !isOperationType(typeDefinition);
    }

    private boolean isOperationType(TypeDefinition typeDefinition) {
        requireNonNull(typeDefinition);
        return Objects.equals(queryType, typeDefinition) || Objects.equals(mutationType, typeDefinition);
    }

    boolean hasTypeAndField(TypeDefinitionRegistry registry, TypeDefinition typeDef, FieldDefinition fieldDef) {
        if (findQueryType(registry).map(qType -> qType == typeDef).orElse(false)) {
            if (schemaSource.getQueryFieldRenames().isEmpty()) {
                if (queryType == null) {
                    return false;
                }
                return queryType.getFieldDefinitions().stream().anyMatch(fieldDefinition -> fieldDefinition.getName().equals(fieldDef.getName()));
            }
            return getFieldRenameByBraidName(schemaSource.getQueryFieldRenames(), fieldDef.getName())
                    .map(alias -> alias.getBraidName().equals(fieldDef.getName()))
                    .orElse(false);
        } else {
            return getType(getSourceTypeName(typeDef.getName())).isPresent();
        }
    }
}
