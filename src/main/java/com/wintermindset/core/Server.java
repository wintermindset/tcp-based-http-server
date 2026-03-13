package com.wintermindset.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wintermindset.config.ServerConfig;
import com.wintermindset.handler.Handler;
import com.wintermindset.http.HttpConnection;

public final class Server {

    private final int port;
    private final Handler handler;
    private static final Logger LOGGER = LogManager.getLogger(Server.class);

    public Server(ServerConfig config) {
        this.port = config.port;
        this.handler = config.handler;
    }

    public void start() throws IOException {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            LOGGER.info("Server started on port {}", port);
            while (true) {
                Socket socket = serverSocket.accept();
                LOGGER.info("Client {} is accepted", socket);
                socket.setTcpNoDelay(true);
                executor.submit(new HttpConnection(socket, handler));
            }
        }
    }
}