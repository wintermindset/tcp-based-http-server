package com.wintermindset.config;

import com.wintermindset.handler.CalcHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ConfigReaderTest {
    @Test
    void loadsBundledConfig() {
        ServerConfig config = ConfigReader.loadFromResources("server-config.yaml");
        assertEquals(8080, config.port);
        assertInstanceOf(CalcHandler.class, config.handler);
    }

    @Test
    void rejectsMissingResource() {
        assertThrows(RuntimeException.class, () -> ConfigReader.loadFromResources("missing.yaml"));
    }

    @Test
    void loadsConfiguredValues() {
        ServerConfig config = load("# test\n\nport: 9090\nhandler: com.wintermindset.handler.CalcHandler\n");
        assertEquals(9090, config.port);
        assertInstanceOf(CalcHandler.class, config.handler);
    }

    @ParameterizedTest
    @ValueSource(strings = {"port: invalid", "handler: missing.Handler", "handler: java.lang.String"})
    void rejectsInvalidConfiguration(String config) {
        assertThrows(RuntimeException.class, () -> load(config));
    }

    private ServerConfig load(String config) {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(original) {
                @Override
                public InputStream getResourceAsStream(String name) {
                    return new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8));
                }
            });
            return ConfigReader.loadFromResources("test.yaml");
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
}
