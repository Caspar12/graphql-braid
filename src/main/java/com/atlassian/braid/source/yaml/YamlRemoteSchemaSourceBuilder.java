package com.atlassian.braid.source.yaml;

import com.atlassian.braid.Extension;
import com.atlassian.braid.FieldRename;
import com.atlassian.braid.Link;
import com.atlassian.braid.LinkArgument;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.TypeRename;
import com.atlassian.braid.document.DocumentMapperFactory;
import com.atlassian.braid.document.DocumentMappers;
import com.atlassian.braid.java.util.BraidMaps;
import com.atlassian.braid.java.util.BraidObjects;
import com.atlassian.braid.source.SchemaLoader;
import com.atlassian.braid.source.StringSchemaLoader;

import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;

public class YamlRemoteSchemaSourceBuilder {

    public static SchemaLoader buildSchemaLoader(Map<String, Object> m) {
        return new StringSchemaLoader(SchemaLoader.Type.IDL, (String) m.get("schema"));
    }

    public static SchemaNamespace buildSchemaNamespace(Map<String, Object> m) {
        return SchemaNamespace.of((String) m.get("namespace"));
    }

    public static List<FieldRename> buildQueryFieldRenames(Map<String, Object> m) {
        return Optional.of(BraidMaps.get(m, "queryFields")
                .orElse(BraidMaps.get(m, "topLevelFields")
                        .orElse(emptyList())))
                .map(YamlRemoteSchemaSourceBuilder::getFieldAliasesFromObject)
                .orElseThrow(IllegalStateException::new);
    }

    public static List<FieldRename> buildMutationAliases(Map<String, Object> m) {
        return Optional.of(BraidMaps.get(m, "mutationFields")
                .orElse(emptyList()))
                .map(YamlRemoteSchemaSourceBuilder::getFieldAliasesFromObject)
                .orElseThrow(IllegalStateException::new);
    }

    public static List<TypeRename> buildTypeRenames(Map<String, Object> m) {
        return BraidMaps.get(m, "typeAliases")
                .map(BraidObjects::<Map<String, String>>cast)
                .orElse(emptyMap())
                .entrySet()
                .stream()
                .map(entry -> TypeRename.from(entry.getKey(), entry.getValue()))
                .collect(toList());
    }

    public static List<Link> buildLinks(Map<String, Object> m) {
        final SchemaNamespace fromNamespace = SchemaNamespace.of(getOrThrow(m, "namespace"));

        List<Link> simpleLinks = BraidMaps.get(m, "links")
                .map(BraidObjects::<List<Map<String, Map<String, Object>>>>cast)
                .map(links -> buildSimpleLinks(fromNamespace, links))
                .orElse(emptyList());
        return Stream.concat(simpleLinks.stream(), buildComplexLinks(fromNamespace, m))
                .collect(Collectors.toList());
    }

    public static Stream<Link> buildComplexLinks(SchemaNamespace fromNamespace, Map<String, Object> m) {
        return BraidMaps.get(m, "complexLinks")
                .map(BraidObjects::<List<Map<String, Object>>>cast)
                .orElse(Collections.emptyList())
                .stream()
                .map(link -> buildComplexLink(fromNamespace, link));
    }

    public static List<Extension> buildExtensions(Map<String, Object> m) {
        return BraidMaps.get(m, "extensions")
                .map(BraidObjects::<List<Map<String, Object>>>cast)
                .map(YamlRemoteSchemaSourceBuilder::buildExtensions)
                .orElse(emptyList());
    }

    private static List<Extension> buildExtensions(List<Map<String, Object>> extensions) {
        return extensions.stream().map(YamlRemoteSchemaSourceBuilder::buildExtension).collect(toList());
    }

    private static Extension buildExtension(Map<String, Object> e) {
        return new Extension(
                getOrThrow(e, "type"),
                getOrThrow(e, "field"),
                buildExtensionBy(getOrThrow(e, "by")));
    }

    private static Extension.By buildExtensionBy(Map<String, Object> by) {
        return new Extension.By(
                SchemaNamespace.of(getOrThrow(by, "namespace")),
                getOrThrow(by, "type"),
                getOrThrow(by, "query"),
                getOrThrow(by, "arg"));
    }

    public static DocumentMapperFactory buildDocumentMapperFactory(Map<String, Object> m) {
        return BraidMaps.get(m, "mapper")
                .map(BraidObjects::<List<Map<String, Object>>>cast)
                .map(DocumentMappers::fromYamlList)
                .orElse(DocumentMappers.identity());
    }

    private static List<FieldRename> getFieldAliasesFromObject(Object fields) {
        if (fields instanceof List) {
            return ((List<String>) fields).stream()
                    .map(f -> FieldRename.from(f, f))
                    .collect(toList());
        } else if (fields instanceof Map) {
            return ((Map<String, String>) fields).entrySet().stream()
                    .map(entry -> FieldRename.from(entry.getKey(), entry.getValue()))
                    .collect(toList());
        } else {
            throw new IllegalArgumentException("Unexpected field type");
        }
    }

    private static List<Link> buildSimpleLinks(SchemaNamespace fromNamespace, List<Map<String, Map<String, Object>>> links) {
        return links.stream().map(l -> buildLink(fromNamespace, l)).collect(toList());
    }

    private static Link buildLink(SchemaNamespace fromNamespace, Map<String, Map<String, Object>> linkMap) {

        final Map<String, String> from = getOrThrow(linkMap, "from");
        final Map<String, Object> to = getOrThrow(linkMap, "to");

        Link.LinkBuilder linkBuilder = buildFrom(fromNamespace, from);
        linkBuilder = buildTo(linkBuilder, to);

        if (getReplaceFromField(linkMap)) {
            linkBuilder.replaceFromField();
        }

        BraidMaps.get(to, "argument").map(Object::toString).ifPresent(linkBuilder::argument);
        BraidMaps.get(to, "nullable").map(val -> {
            if (val instanceof Boolean) {
                return (Boolean) val;
            } else {
                return Boolean.valueOf((String) val);
            }
        }).ifPresent(linkBuilder::setNullable);

        return linkBuilder.build();
    }

    private static Link buildComplexLink(SchemaNamespace fromNamespace, Map<String, Object> linkMap) {
        Link.ComplexLinkBuilder linkBuilder = Link.newComplexLink();
        linkBuilder.sourceNamespace(fromNamespace)
                .sourceType(getOrThrow(linkMap, "sourceType"))
                .targetType(getOrThrow(linkMap, "targetType"))
                .targetNamespace(SchemaNamespace.of(getOrThrow(linkMap, "targetNamespace")))
                .topLevelQueryField(getOrThrow(linkMap, "topLevelQueryField"))
                .newFieldName(getOrThrow(linkMap, "field"));

        BraidMaps.get(linkMap, "arguments")
                .map(BraidObjects::<List<Map<String, Object>>>cast)
                .orElse(Collections.emptyList())
                .stream()
                .map(YamlRemoteSchemaSourceBuilder::buildLinkArgument)
                .forEach(linkBuilder::linkArgument);

        return linkBuilder.build();
    }

    private static LinkArgument buildLinkArgument(Map<String, Object> argumentMap) {
        return LinkArgument.newLinkArgument()
                .nullable(getOrDefault(argumentMap, "nullable", true))
                .removeInputField(getOrDefault(argumentMap, "removeInputField", false))
                .sourceName(getOrThrow(argumentMap, "sourceName"))
                .targetFieldMatchingArgument(getOrDefault(argumentMap, "targetFieldMatchingArgument", null))
                .queryArgumentName(getOrThrow(argumentMap, "queryArgumentName"))
                .argumentSource(LinkArgument.ArgumentSource.valueOf(getOrThrow(argumentMap, "argumentSource")))
                .build();
    }

    private static Link.LinkBuilder buildFrom(SchemaNamespace fromNamespace, Map<String, String> from) {
        final String fromField = getOrThrow(from, "field");
        return Link.from(
                fromNamespace,
                getOrThrow(from, "type"),
                fromField,
                BraidMaps.get(from, "fromField").orElse(fromField));
    }

    private static Link.LinkBuilder buildTo(Link.LinkBuilder builder, Map<String, Object> to) {
        return builder.to(
                SchemaNamespace.of(getOrThrow(to, "namespace")),
                getOrThrow(to, "type"),
                BraidMaps.get(to, "field").map(Objects::toString).orElse(null),
                BraidMaps.get(to, "variableField").map(Objects::toString).orElse(null));
    }

    private static <T> T getOrThrow(Map<String, ?> map, String key) {
        return BraidMaps.get(map, key).map(BraidObjects::<T>cast).orElseThrow(IllegalStateException::new);
    }

    private static <T> T getOrDefault(Map<String, ?> map, String key, T defaultValue) {
        return BraidMaps.get(map, key).map(BraidObjects::<T>cast).orElse(defaultValue);
    }

    private static boolean getReplaceFromField(Map<String, Map<String, Object>> link) {
        return BraidMaps.get(link.get("from"), "replaceFromField")
                .map(BraidObjects::<Boolean>cast)
                .orElse(false);
    }
}
