package com.wintermindset;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

import com.wintermindset.factory.HandlerFactory;

public class Server {
    private final int port;
    private AtomicInteger clientsCount = new AtomicInteger(0);
    private final HandlerFactory handlerFactory;

    public Server(int port, HandlerFactory handlerFactory) {
        this.port = port;
        this.handlerFactory = handlerFactory;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                int clientNumber = clientsCount.incrementAndGet();
                System.out.println("Client connected. Total clients: " + clientNumber);
                Runnable handler = handlerFactory.create(clientSocket);
                Thread.startVirtualThread(() -> {
                    try {
                        handler.run();
                    } finally {
                        int remainingClients = clientsCount.decrementAndGet();
                        System.out.println("Client disconnected. Remaining clients: " + remainingClients);
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}