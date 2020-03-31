package com.atlassian.braid.source.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.function.Function;


/**
 * Loads a json document into a map
 */
public class JacksonJsonToMapParser implements Function<Reader, Map> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map apply(Reader s) {
        try {
            return objectMapper.readValue(s, Map.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
