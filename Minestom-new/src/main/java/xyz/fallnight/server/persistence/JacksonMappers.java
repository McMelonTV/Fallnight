package xyz.fallnight.server.persistence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class JacksonMappers {
    private JacksonMappers() {
    }

    public static ObjectMapper jsonMapper() {
        return configure(new ObjectMapper());
    }

    public static ObjectMapper yamlMapper() {
        return configure(new ObjectMapper(new YAMLFactory()));
    }

    private static ObjectMapper configure(ObjectMapper mapper) {
        return mapper
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);
    }
}
