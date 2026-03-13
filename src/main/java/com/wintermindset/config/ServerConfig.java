package com.wintermindset.config;

import com.wintermindset.handler.Handler;

/**
 * Configuration object for the HTTP server.
 *
 * <p>This class contains basic settings required to start the server,
 * such as the listening port and the request {@link Handler} that
 * processes incoming HTTP requests.</p>
 *
 * <p>The configuration is typically created during application startup
 * and passed to the server instance.</p>
 *
 * <p>Example usage:</p>
 *
 * <pre>
 * ServerConfig config = new ServerConfig();
 * config.port = 8080;
 * config.handler = new HelloHandler();
 * </pre>
 */
public final class ServerConfig {

    /**
     * Port on which the HTTP server will listen.
     *
     * <p>Default value: {@code 8080}.</p>
     */
    public int port = 8080;

    /**
     * Application-level request handler responsible for
     * processing incoming HTTP requests.
     */
    public Handler handler;
}