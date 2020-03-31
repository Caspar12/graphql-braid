package com.atlassian.braid.source;

import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

import static com.atlassian.braid.source.SchemaUtils.loadSchema;

public class UrlSchemaLoader implements SchemaLoader {

    private final SchemaLoader.Type type;
    private final URL url;

    public UrlSchemaLoader(URL url) {
        if (url.getPath().endsWith(".json")) {
            this.type = SchemaLoader.Type.INTROSPECTION;
        } else {
            this.type = SchemaLoader.Type.IDL;
        }
        this.url = url;
    }

    public UrlSchemaLoader(SchemaLoader.Type type, URL url) {
        this.type = type;
        this.url = url;
    }

    @Override
    public TypeDefinitionRegistry load() {
        return loadSchema(type, readStringFromURL(url));

    }

    private static Reader readStringFromURL(URL url)
    {
        try {
            URLConnection conn = url.openConnection();
            return new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
