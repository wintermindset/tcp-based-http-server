package com.wintermindset.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.wintermindset.handler.HandlerFactory;

public final class ConfigReader {

    public static ServerConfig loadFromResources(String resourceName) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalArgumentException(
                        "Config resource not found: " + resourceName
                );
            }
            return parse(new InputStreamReader(in));
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load config from resources: " + resourceName, e
            );
        }
    }

    private static ServerConfig parse(InputStreamReader in) throws IOException {
        ServerConfig cfg = new ServerConfig();
        try (BufferedReader r = new BufferedReader(in)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                apply(cfg, key, value);
            }
        }
        return cfg;
    }

    private static void apply(ServerConfig cfg, String key, String value) {
        switch (key) {
            case "port" -> cfg.port = Integer.parseInt(value);
            case "handler" -> cfg.handler = HandlerFactory.fromClassName(value);
        }
    }
}
