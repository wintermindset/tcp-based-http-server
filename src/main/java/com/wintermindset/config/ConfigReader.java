package com.wintermindset.config;

import com.wintermindset.handler.HandlerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class ConfigReader {
    private ConfigReader() {}

    public static ServerConfig loadFromResources(String resourceName) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(resourceName)) {
            if (in == null) throw new IllegalArgumentException("Config resource not found: " + resourceName);
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read configuration resource: " + resourceName, e);
        }
    }

    public static ServerConfig load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    public static ServerConfig parse(Reader reader) throws IOException {
        ServerConfig config = new ServerConfig();
        Set<String> keys = new HashSet<>();
        BufferedReader lines = new BufferedReader(reader);
        String line;
        int number = 0;
        while ((line = lines.readLine()) != null) {
            number++;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int colon = line.indexOf(':');
            if (colon < 1) throw new IllegalArgumentException("Expected key: value on line " + number);
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (value.isEmpty() || !keys.add(key)) throw new IllegalArgumentException("Empty or duplicate setting: " + key);
            try {
                switch (key) {
                    case "port" -> config.port = Integer.parseInt(value);
                    case "bindAddress" -> config.bindAddress = value;
                    case "handler" -> config.handler = HandlerFactory.fromClassName(value);
                    case "maxConnections" -> config.maxConnections = Integer.parseInt(value);
                    case "maxRequestsPerConnection" -> config.maxRequestsPerConnection = Integer.parseInt(value);
                    case "idleTimeoutMillis" -> config.idleTimeoutMillis = Integer.parseInt(value);
                    case "readTimeoutMillis" -> config.readTimeoutMillis = Integer.parseInt(value);
                    case "requestTimeoutMillis" -> config.requestTimeoutMillis = Integer.parseInt(value);
                    case "shutdownTimeoutMillis" -> config.shutdownTimeoutMillis = Integer.parseInt(value);
                    case "maxHeaders" -> config.maxHeaders = Integer.parseInt(value);
                    case "maxHeaderBytes" -> config.maxHeaderBytes = Integer.parseInt(value);
                    case "maxBodyBytes" -> config.maxBodyBytes = Integer.parseInt(value);
                    default -> throw new IllegalArgumentException("Unknown setting: " + key);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Expected an integer for " + key, e);
            }
        }
        config.validate();
        return config;
    }
}
