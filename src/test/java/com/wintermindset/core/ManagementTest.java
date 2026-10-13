package com.wintermindset.core;

import com.wintermindset.config.ServerConfig;
import com.wintermindset.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class ManagementTest {
    private String get(int port, String path) throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void servesHealthAndMetricsOnSeparatePort() throws Exception {
        ServerConfig cfg = new ServerConfig();
        cfg.port = 0;
        cfg.managementPort = 0;
        cfg.handler = req -> HttpResponse.ok("application");
        try (var workers = Executors.newVirtualThreadPerTaskExecutor(); Server server = new Server(cfg)) {
            var serving = workers.submit(() -> { server.start(); return null; });
            assertTrue(server.awaitStarted(Duration.ofSeconds(3)));
            assertNotEquals(server.port(), server.managementPort());
            String response = get(server.port(), "/");
            assertTrue(response.contains("X-Request-Id: "));
            assertTrue(get(server.managementPort(), "/healthz").endsWith("ok\n"));
            assertTrue(get(server.managementPort(), "/readyz").endsWith("ready\n"));
            String metrics = get(server.managementPort(), "/metrics");
            assertTrue(metrics.contains("http_requests_total{status_class=\"2xx\"} 1"));
            assertTrue(metrics.contains("http_request_duration_seconds_count 1"));
            assertTrue(get(server.managementPort(), "/missing").startsWith("HTTP/1.1 404"));
            server.close();
            serving.get(3, TimeUnit.SECONDS);
        }
    }
}
