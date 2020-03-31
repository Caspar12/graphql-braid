package com.atlassian.braid.source.yaml;

import com.atlassian.braid.FieldRename;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.java.util.BraidMaps;
import com.atlassian.braid.java.util.BraidObjects;
import com.atlassian.braid.mapper.Mapper;
import com.atlassian.braid.source.GraphQLRemoteRetriever;
import com.atlassian.braid.source.QueryExecutorSchemaSource;
import com.atlassian.braid.source.SchemaLoader;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.util.Map;
import java.util.function.Supplier;

import static com.atlassian.braid.java.util.BraidObjects.cast;
import static com.atlassian.braid.mapper.Mappers.fromYamlList;
import static com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceBuilder.buildDocumentMapperFactory;
import static com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceBuilder.buildExtensions;
import static com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceBuilder.buildLinks;
import static com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceBuilder.buildMutationAliases;
import static com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceBuilder.buildQueryFieldRenames;
import static com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceBuilder.buildSchemaNamespace;
import static com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceBuilder.buildSchemaLoader;
import static com.atlassian.braid.source.yaml.YamlRemoteSchemaSourceBuilder.buildTypeRenames;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;

/**
 * Builds a YAML-defined schema source either for REST or GraphQL endpoints
 */
public class YamlRemoteSchemaSourceFactory {

    public static <C> RestRemoteSchemaSource<C> createRestSource(Reader source,
                                                                 RestRemoteRetriever<C> restRemoteRetriever) {
        final Map<String, Object> m = loadYamlMap(source);

        SchemaNamespace namespace = buildSchemaNamespace(m);
        SchemaLoader schemaLoader = buildSchemaLoader(m);

        Map<String, RestRemoteSchemaSource.RootField> rootFields = BraidMaps.get(m, "rootFields")
                .map(BraidObjects::<Map<String, Map<String, Object>>>cast)
                .orElse(emptyMap())
                .entrySet().stream()
                .map(e -> {
                    String fieldName = e.getKey();
                    Map<String, Object> params = e.getValue();

                    Mapper mapping = fromYamlList(BraidObjects.cast(params.get("responseMapping")));
                    return new RestRemoteSchemaSource.RootField(fieldName, cast(params.get("uri")), mapping);
                })
                .collect(toMap(f -> f.name, f -> f));

        return new RestRemoteSchemaSource<>(
                namespace,
                schemaLoader,
                restRemoteRetriever,
                rootFields,
                buildLinks(m),
                buildExtensions(m),
                buildQueryFieldRenames(m).stream().map(FieldRename::getSourceName).toArray(String[]::new));
    }

    public static <C> SchemaSource createGraphQLSource(Reader source, GraphQLRemoteRetriever<C> graphQLRemoteRetriever) {
        Map<String, Object> m = loadYamlMap(source);

        return QueryExecutorSchemaSource.<C>builder()
                .namespace(buildSchemaNamespace(m))
                .schemaLoader(buildSchemaLoader(m))
                .remoteRetriever(graphQLRemoteRetriever)
                .links(buildLinks(m))
                .queryFieldRenames(buildQueryFieldRenames(m))
                .mutationFieldRenames(buildMutationAliases(m))
                .typeRenames(buildTypeRenames(m))
                .documentMapperFactory(buildDocumentMapperFactory(m))
                .build();
    }

    private static Map<String, Object> loadYamlMap(Reader source) {
        return cast(new Yaml().load(source));
    }


}
