package com.wintermindset.core;

import com.wintermindset.config.ServerConfig;
import com.wintermindset.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class ServerLifecycleTest {
    @Test
    void drainsActiveResponseAndStopsAccepting() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ServerConfig config = new ServerConfig();
        config.port = 0;
        config.handler = req -> {
            entered.countDown();
            try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return HttpResponse.ok("finished");
        };
        try (var workers = Executors.newVirtualThreadPerTaskExecutor(); Server server = new Server(config)) {
            var serving = workers.submit(() -> { server.start(); return null; });
            assertTrue(server.awaitStarted(Duration.ofSeconds(3)));
            try (Socket client = new Socket("localhost", server.port())) {
                client.setSoTimeout(3000);
                client.getOutputStream().write("GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                var stopping = workers.submit(server::close);
                assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                    while (server.isRunning()) Thread.sleep(5);
                });
                release.countDown();
                assertTrue(new String(client.getInputStream().readAllBytes(), StandardCharsets.UTF_8).endsWith("finished"));
                stopping.get(3, TimeUnit.SECONDS);
                serving.get(3, TimeUnit.SECONDS);
                assertFalse(server.isRunning());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void boundsShutdownOfIdleConnections() throws Exception {
        ServerConfig config = new ServerConfig();
        config.port = 0;
        config.shutdownTimeoutMillis = 100;
        config.handler = req -> HttpResponse.ok("ok");
        try (var workers = Executors.newVirtualThreadPerTaskExecutor(); Server server = new Server(config)) {
            var serving = workers.submit(() -> { server.start(); return null; });
            assertTrue(server.awaitStarted(Duration.ofSeconds(3)));
            try (Socket client = new Socket("localhost", server.port())) {
                client.setSoTimeout(3000);
                assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                    while (server.activeConnections() == 0) Thread.sleep(5);
                });
                assertTimeoutPreemptively(Duration.ofSeconds(3), server::close);
                assertEquals(-1, client.getInputStream().read());
                serving.get(3, TimeUnit.SECONDS);
            }
            server.close();
        }
    }
}
