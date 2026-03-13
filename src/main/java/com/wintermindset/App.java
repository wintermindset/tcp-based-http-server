package com.wintermindset;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wintermindset.config.ConfigReader;
import com.wintermindset.config.ServerConfig;
import com.wintermindset.core.Server;

public class App {

    private static final Logger LOGGER = LogManager.getLogger(App.class);

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