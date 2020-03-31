package com.atlassian.braid.source;

import com.atlassian.braid.Link;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.Document;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.errors.SchemaProblem;

import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.atlassian.braid.TypeUtils.filterQueryType;

public final class SchemaUtils {

    private static final Function<Reader, Map> readerToMap;

    static {
        readerToMap = createReaderToMapInstance();
    }
    private SchemaUtils() {
    }

    public static TypeDefinitionRegistry loadPublicSchema(SchemaLoader schemaLoader, List<Link> links,
                                                          String... topLevelFields) {
        return filterQueryType(schemaLoader.load(), links, topLevelFields);
    }

    public static TypeDefinitionRegistry loadSchema(SchemaLoader.Type type, Reader reader) {
        switch (type) {
            case IDL:
                return loadSchemaFromIdl(reader);
            case INTROSPECTION:
                return loadSchemaFromIntrospection(reader);
            default:
                throw new IllegalArgumentException("Type not supported: " + type);
        }
    }

    private static TypeDefinitionRegistry loadSchemaFromIdl(Reader schemaReader) {
        SchemaParser parser = new SchemaParser();
        return parser.parse(schemaReader);
    }

    private static TypeDefinitionRegistry loadSchemaFromIntrospection(Reader schemaReader) {
        SchemaParser parser = new SchemaParser();
        Map result = readerToMap.apply(schemaReader);
        Document schemaDoc = new IntrospectionResultToSchema().createSchemaDefinition(result);
        return parser.buildRegistry(schemaDoc);
    }

    private static Function<Reader, Map> createReaderToMapInstance() {
        try {
            Class<? extends Function<Reader, Map>> clazz = (Class<? extends Function<Reader, Map>>)
                    SchemaUtils.class.getClassLoader().loadClass(
                            "com.atlassian.braid.source.jackson.JacksonJsonToMapParser");
            return clazz.newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            return reader -> {
                throw new RuntimeException("Jackson not found, so loading introspection docs not supported");
            };
        }
    }
}
