package com.wintermindset.core;

import com.wintermindset.config.ServerConfig;
import com.wintermindset.http.HttpConnection;
import com.wintermindset.http.HttpResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
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
    private final ServerMetrics metrics = new ServerMetrics();
    private Server management;
    private volatile boolean running;
    private boolean startCalled;
    private ServerSocket listener;
    private ExecutorService executor;
    private ScheduledThreadPoolExecutor deadlines;

    public Server(ServerConfig config) {
        config.validate();
        this.config = config.copy();
        this.capacity = new Semaphore(config.maxConnections);
    }

    public void start() throws IOException {
        try {
            synchronized (lifecycle) {
                if (startCalled || closing.get()) throw new IllegalStateException("Server cannot be restarted");
                startCalled = true;
                listener = new ServerSocket();
                listener.bind(new InetSocketAddress(config.bindAddress, config.port));
                executor = Executors.newVirtualThreadPerTaskExecutor();
                deadlines = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("http-deadlines").factory());
                deadlines.setRemoveOnCancelPolicy(true);
                running = true;
                startManagement();
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
                metrics.rejected.increment();
                socket.close();
                return;
            }
            connections.add(socket);
            metrics.accepted.increment();
            try {
                socket.setTcpNoDelay(true);
                HttpConnection connection = new HttpConnection(socket, config.handler, config, deadlines, this::isRunning, metrics);
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

    public int managementPort() {
        synchronized (lifecycle) {
            return management == null ? -1 : management.port();
        }
    }

    private void startManagement() throws IOException {
        if (config.managementPort == -1) return;
        ServerConfig admin = new ServerConfig();
        admin.port = config.managementPort;
        admin.bindAddress = config.managementBindAddress;
        admin.maxConnections = 4;
        admin.maxRequestsPerConnection = 10;
        admin.maxBodyBytes = 0;
        admin.idleTimeoutMillis = 2000;
        admin.requestTimeoutMillis = 2000;
        admin.shutdownTimeoutMillis = 0;
        admin.logRequests = false;
        admin.handler = request -> {
            if (!request.method.equals("GET") && !request.method.equals("HEAD")) {
                return new HttpResponse().status(405, "Method Not Allowed").header("Allow", "GET, HEAD");
            }
            return switch (request.path) {
                case "/healthz" -> HttpResponse.ok("ok\n");
                case "/readyz" -> running ? HttpResponse.ok("ready\n")
                        : new HttpResponse().status(503, "Service Unavailable").body("draining\n");
                case "/metrics" -> HttpResponse.ok(metrics.render(activeConnections()))
                        .header("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
                default -> HttpResponse.notFound();
            };
        };
        management = new Server(admin);
        Thread.startVirtualThread(() -> {
            try { management.start(); } catch (IOException e) { LOGGER.error("Management listener failed", e); }
        });
        try {
            if (!management.awaitStarted(Duration.ofSeconds(5))) throw new IOException("Management listener failed to start");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted starting management listener", e);
        }
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
            if (management != null) management.close();
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
