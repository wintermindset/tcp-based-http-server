package com.wintermindset.core;

import com.wintermindset.config.ServerConfig;
import com.wintermindset.handler.CalcHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class ServerIntegrationTest {
    private ExecutorService worker;
    private Server server;
    private Future<?> serving;

    @BeforeEach
    void start() throws Exception {
        ServerConfig config = new ServerConfig();
        config.port = 0;
        config.maxConnections = 1;
        config.maxRequestsPerConnection = 2;
        config.shutdownTimeoutMillis = 100;
        config.handler = req -> {
            if (req.path.equals("/throw")) throw new IllegalStateException("test failure");
            return new CalcHandler().handle(req);
        };
        worker = Executors.newVirtualThreadPerTaskExecutor();
        server = new Server(config);
        serving = worker.submit(() -> { server.start(); return null; });
        assertTrue(server.awaitStarted(Duration.ofSeconds(3)));
    }

    @AfterEach
    void stop() throws Exception {
        server.close();
        try { serving.get(3, TimeUnit.SECONDS); } finally { worker.shutdownNow(); }
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            while (server.activeConnections() != 0) Thread.sleep(5);
        });
    }

    private String exchange(String wire) throws Exception {
        try (Socket socket = new Socket("localhost", server.port())) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(wire.getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String request(String path) {
        return "GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
    }

    @Test
    void servesCalculatorOverTcp() throws Exception {
        String response = exchange(request("/calc?x=3&y=4&op=*"));
        assertTrue(response.startsWith("HTTP/1.1 200 OK\r\n"));
        assertTrue(response.endsWith("12.0"));
    }

    @Test
    void pipelinesRequestsAndEnforcesRequestLimit() throws Exception {
        String first = "GET /calc?x=3&y=4&op=* HTTP/1.1\r\nHost: localhost\r\n\r\n";
        String response = exchange(first + first);
        assertEquals(2, response.split("HTTP/1.1 200 OK", -1).length - 1);
        assertTrue(response.contains("Connection: keep-alive"));
        assertTrue(response.contains("Connection: close"));
    }

    @Test
    void serializesHeadWithoutBody() throws Exception {
        String response = exchange("HEAD /calc?x=3&y=4&op=* HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.1 200"));
        assertTrue(response.contains("Content-Length: 4\r\n"));
        assertTrue(response.endsWith("\r\n\r\n"));
    }

    @Test
    void reportsParserErrors() throws Exception {
        assertTrue(exchange("GET / HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 400"));
    }

    @Test
    void reportsHandlerErrors() throws Exception {
        String response = exchange(request("/throw"));
        assertTrue(response.startsWith("HTTP/1.1 500"));
        assertFalse(response.contains("test failure"));
    }

    @Test
    void rejectsExcessConnectionsAndRecovers() throws Exception {
        try (Socket occupied = new Socket("localhost", server.port())) {
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                while (server.activeConnections() != 1) Thread.sleep(5);
            });
            try (Socket rejected = new Socket("localhost", server.port())) {
                rejected.setSoTimeout(3000);
                assertEquals(-1, rejected.getInputStream().read());
            }
        }
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            while (server.activeConnections() != 0) Thread.sleep(5);
        });
        assertTrue(exchange(request("/calc?x=1&y=2&op=*" )).startsWith("HTTP/1.1 200"));
    }
}
