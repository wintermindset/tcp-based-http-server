package com.wintermindset;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wintermindset.config.ConfigReader;
import com.wintermindset.config.ServerConfig;
import com.wintermindset.core.Server;

/**
 * Application entry point.
 *
 * <p>This class is responsible for bootstrapping the HTTP server.
 * It performs the following steps:</p>
 *
 * <ol>
 *     <li>Load server configuration from resources</li>
 *     <li>Create the {@link Server} instance</li>
 *     <li>Start the server</li>
 * </ol>
 *
 * <p>The configuration file is expected to be located in the application
 * resources and defines settings such as the server port and the request
 * handler implementation.</p>
 */
public class App {

    private static final Logger LOGGER = LogManager.getLogger(App.class);

    /**
     * Main application entry point.
     *
     * <p>The method loads the server configuration, initializes the server,
     * and starts accepting HTTP connections.</p>
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        try {
            LOGGER.info("Loading server config");
            ServerConfig config = ConfigReader.loadFromResources("server-config.yaml");
            LOGGER.info("Config is loaded");
            LOGGER.info("Creating server");
            Server server = new Server(config);
            LOGGER.info("Server is created");
            LOGGER.info("Starting server");
            server.start();
        } catch (IOException e) {
            LOGGER.error(e);
        }
    }
}