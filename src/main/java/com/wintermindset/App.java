package com.wintermindset;

import com.wintermindset.config.ConfigReader;
import com.wintermindset.config.ServerConfig;
import com.wintermindset.core.Server;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

public final class App {
    private static final Logger LOGGER = LogManager.getLogger(App.class);

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        try {
            if (args.length > 1) throw new IllegalArgumentException("Usage: java -jar app.jar [config-file]");
            String path = args.length == 1 ? args[0] : System.getenv("SERVER_CONFIG");
            ServerConfig config = path == null || path.isBlank()
                    ? ConfigReader.loadFromResources("server-config.yaml") : ConfigReader.load(Path.of(path));
            try (Server server = new Server(config)) {
                Thread shutdown = new Thread(server::close, "http-shutdown");
                Runtime.getRuntime().addShutdownHook(shutdown);
                try {
                    server.start();
                } finally {
                    try { Runtime.getRuntime().removeShutdownHook(shutdown); } catch (IllegalStateException ignored) { }
                }
            }
            return 0;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Server startup or execution failed", e);
            return 1;
        }
    }
}
