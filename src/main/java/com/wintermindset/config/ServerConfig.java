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

    public String bindAddress = "0.0.0.0";
    public int maxConnections = 64;
    public int maxRequestsPerConnection = 100;
    public int idleTimeoutMillis = 15000;
    public int readTimeoutMillis = 5000;
    public int requestTimeoutMillis = 30000;
    public int shutdownTimeoutMillis = 5000;
    public int maxHeaders = 100;
    public int maxHeaderBytes = 32768;
    public int maxBodyBytes = 1024 * 1024;

    public void validate() {
        validateLimits();
        if (port < 0 || port > 65535 || bindAddress == null || bindAddress.isBlank() || handler == null) {
            throw new IllegalArgumentException("Invalid port, bind address, or missing handler");
        }
    }

    public ServerConfig copy() {
        ServerConfig copy = new ServerConfig();
        copy.port = port;
        copy.bindAddress = bindAddress;
        copy.handler = handler;
        copy.maxConnections = maxConnections;
        copy.maxRequestsPerConnection = maxRequestsPerConnection;
        copy.idleTimeoutMillis = idleTimeoutMillis;
        copy.readTimeoutMillis = readTimeoutMillis;
        copy.requestTimeoutMillis = requestTimeoutMillis;
        copy.shutdownTimeoutMillis = shutdownTimeoutMillis;
        copy.maxHeaders = maxHeaders;
        copy.maxHeaderBytes = maxHeaderBytes;
        copy.maxBodyBytes = maxBodyBytes;
        return copy;
    }

    public void validateLimits() {
        if (maxConnections <= 0 || maxRequestsPerConnection <= 0 || idleTimeoutMillis <= 0
                || readTimeoutMillis <= 0 || requestTimeoutMillis <= 0 || shutdownTimeoutMillis < 0 || maxHeaders <= 0
                || maxHeaderBytes <= 0 || maxBodyBytes < 0 || maxBodyBytes > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid server resource limits");
        }
    }
}
