package com.wintermindset.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.wintermindset.handler.HandlerFactory;

/**
 * Utility class responsible for loading and parsing server configuration
 * from resource files.
 *
 * <p>The configuration file is expected to be located on the application
 * classpath and follow a simple {@code key: value} format.</p>
 *
 * <p>Example configuration file:</p>
 *
 * <pre>
 * # Server configuration
 * port: 8080
 * handler: com.example.MyHandler
 * </pre>
 *
 * <p>Supported configuration keys:</p>
 * <ul>
 *     <li>{@code port} – server listening port</li>
 *     <li>{@code handler} – fully qualified class name of the request handler</li>
 * </ul>
 *
 * <p>Lines starting with {@code #} and empty lines are ignored.</p>
 */
public final class ConfigReader {

    /**
     * Loads a {@link ServerConfig} instance from a configuration file
     * located in the application resources.
     *
     * @param resourceName resource file name (e.g. {@code server.conf})
     * @return parsed server configuration
     *
     * @throws IllegalArgumentException if the resource cannot be found
     * @throws RuntimeException if parsing fails
     */
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


    /**
     * Parses the configuration content from a reader.
     *
     * <p>The method reads the configuration line by line and applies
     * recognized keys to the {@link ServerConfig} object.</p>
     *
     * @param in input reader
     * @return parsed configuration
     * @throws IOException if reading fails
     */
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

    /**
     * Applies a configuration key-value pair to the {@link ServerConfig}.
     *
     * @param cfg configuration object
     * @param key configuration key
     * @param value configuration value
     */
    private static void apply(ServerConfig cfg, String key, String value) {
        switch (key) {
            case "port" -> cfg.port = Integer.parseInt(value);
            case "handler" -> cfg.handler = HandlerFactory.fromClassName(value);
        }
    }
}