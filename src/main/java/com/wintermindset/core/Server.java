package com.wintermindset.core;

import com.wintermindset.config.ServerConfig;
import com.wintermindset.http.HttpConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Server implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger(Server.class);
    private final ServerConfig config;
    private final Object lifecycle = new Object();
    private final CountDownLatch started = new CountDownLatch(1);
    private final AtomicBoolean closing = new AtomicBoolean();
    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
    private final Semaphore capacity;
    private volatile boolean running;
    private boolean startCalled;
    private ServerSocket listener;
    private ExecutorService executor;
    private ScheduledThreadPoolExecutor deadlines;

    public Server(ServerConfig config) {
        config.validateLimits();
        this.config = config;
        this.capacity = new Semaphore(config.maxConnections);
    }

    public void start() throws IOException {
        try {
            synchronized (lifecycle) {
                if (startCalled || closing.get()) throw new IllegalStateException("Server cannot be restarted");
                startCalled = true;
                listener = new ServerSocket(config.port);
                executor = Executors.newVirtualThreadPerTaskExecutor();
                deadlines = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("http-deadlines").factory());
                deadlines.setRemoveOnCancelPolicy(true);
                running = true;
                started.countDown();
                LOGGER.info("Server started on port {}", listener.getLocalPort());
            }
            while (running) {
                try {
                    accept(listener.accept());
                } catch (SocketException e) {
                    if (running) throw e;
                }
            }
        } finally {
            started.countDown();
            close();
        }
    }

    private void accept(Socket socket) throws IOException {
        synchronized (lifecycle) {
            if (!running || !capacity.tryAcquire()) {
                socket.close();
                return;
            }
            connections.add(socket);
            try {
                socket.setTcpNoDelay(true);
                HttpConnection connection = new HttpConnection(socket, config.handler, config, deadlines, this::isRunning);
                executor.execute(() -> {
                    try {
                        connection.run();
                    } finally {
                        connections.remove(socket);
                        capacity.release();
                    }
                });
            } catch (IOException | RuntimeException e) {
                connections.remove(socket);
                capacity.release();
                socket.close();
                LOGGER.warn("Connection setup failed", e);
            }
        }
    }

    public boolean awaitStarted(Duration timeout) throws InterruptedException {
        return started.await(timeout.toMillis(), TimeUnit.MILLISECONDS) && running;
    }

    public boolean isRunning() {
        return running;
    }

    public int port() {
        synchronized (lifecycle) {
            if (listener == null) throw new IllegalStateException("Server has not bound a port");
            return listener.getLocalPort();
        }
    }

    public int activeConnections() {
        return connections.size();
    }

    @Override
    public void close() {
        synchronized (lifecycle) {
            if (!closing.compareAndSet(false, true)) return;
            running = false;
            if (listener != null) {
                try { listener.close(); } catch (IOException e) { LOGGER.warn("Listener close failed", e); }
            }
            if (executor != null) executor.shutdown();
        }
        try {
            if (executor != null && !executor.awaitTermination(config.shutdownTimeoutMillis, TimeUnit.MILLISECONDS)) {
                forceClose();
            }
        } catch (InterruptedException e) {
            forceClose();
            Thread.currentThread().interrupt();
        } finally {
            if (deadlines != null) deadlines.shutdownNow();
            LOGGER.info("Server stopped");
        }
    }

    private void forceClose() {
        for (Socket socket : connections) {
            try { socket.close(); } catch (IOException e) { LOGGER.debug("Socket close failed", e); }
        }
        if (executor != null) executor.shutdownNow();
    }
}
