package com.wintermindset.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wintermindset.config.ServerConfig;
import com.wintermindset.handler.Handler;
import com.wintermindset.http.HttpConnection;


/**
 * Minimal HTTP server implementation.
 *
 * <p>This class is responsible for accepting incoming TCP connections
 * and delegating them to {@link HttpConnection} instances for processing.</p>
 *
 * <p>The server uses Java virtual threads (Project Loom) via
 * {@link Executors#newVirtualThreadPerTaskExecutor()} to handle
 * connections concurrently with a lightweight threading model.</p>
 *
 * <p>Main responsibilities:</p>
 * <ul>
 *     <li>Open a {@link ServerSocket}</li>
 *     <li>Accept incoming client connections</li>
 *     <li>Create a connection handler for each socket</li>
 *     <li>Execute handlers using virtual threads</li>
 * </ul>
 *
 * <p>The actual request processing logic is delegated to a
 * {@link Handler} implementation.</p>
 */
public final class Server {

    private final int port;
    private final Handler handler;
    private final ServerConfig config;
    private static final Logger LOGGER = LogManager.getLogger(Server.class);

    /**
     * Creates a new server instance using the provided configuration.
     *
     * @param config server configuration containing port and handler
     */
    public Server(ServerConfig config) {
        config.validateLimits();
        this.config = config;
        this.port = config.port;
        this.handler = config.handler;
    }

    /**
     * Starts the HTTP server.
     *
     * <p>The method opens a {@link ServerSocket} and continuously accepts
     * incoming connections. Each accepted socket is handled by a
     * {@link HttpConnection} running in its own virtual thread.</p>
     *
     * @throws IOException if the server socket cannot be created or fails
     */
    public void start() throws IOException {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        ScheduledThreadPoolExecutor deadlines = new ScheduledThreadPoolExecutor(1,
                Thread.ofPlatform().daemon().name("http-deadlines").factory());
        deadlines.setRemoveOnCancelPolicy(true);
        Semaphore capacity = new Semaphore(config.maxConnections);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            LOGGER.info("Server started on port {}", port);
            while (true) {
                Socket socket = serverSocket.accept();
                if (!capacity.tryAcquire()) {
                    socket.close();
                    continue;
                }
                try {
                    socket.setTcpNoDelay(true);
                    HttpConnection connection = new HttpConnection(socket, handler, config, deadlines);
                    executor.submit(() -> {
                        try { connection.run(); } finally { capacity.release(); }
                    });
                } catch (IOException | RuntimeException e) {
                    capacity.release();
                    socket.close();
                    LOGGER.warn("Connection setup failed", e);
                }
            }
        } finally {
            executor.shutdownNow();
            deadlines.shutdownNow();
        }
    }
}
