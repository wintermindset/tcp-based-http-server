package com.wintermindset.http;

import com.wintermindset.config.ServerConfig;
import com.wintermindset.io.BufferedInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class HttpLimitsTest {
    @Test
    void honorsConfiguredBodyLimit() {
        HttpRequestParser parser = new HttpRequestParser(10, 1024, 2);
        BufferedInput in = new BufferedInput(new ByteArrayInputStream(
                "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 3\r\n\r\nabc"
                        .getBytes(StandardCharsets.US_ASCII)));
        assertEquals(413, assertThrows(HttpException.class, () -> parser.parse(in)).status());
    }

    @Test
    void closesIdleConnection() throws Exception {
        ServerConfig config = new ServerConfig();
        config.idleTimeoutMillis = 100;
        try (ServerSocket listener = new ServerSocket(0);
             Socket client = new Socket("localhost", listener.getLocalPort());
             Socket accepted = listener.accept();
             var deadlines = Executors.newSingleThreadScheduledExecutor();
             var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            client.setSoTimeout(3000);
            var future = workers.submit(new HttpConnection(accepted, req -> HttpResponse.ok("ok"), config, deadlines));
            assertEquals(-1, client.getInputStream().read());
            future.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void deadlineInterruptsHandlerAndClosesSocket() throws Exception {
        ServerConfig config = new ServerConfig();
        config.requestTimeoutMillis = 100;
        try (ServerSocket listener = new ServerSocket(0);
             Socket client = new Socket("localhost", listener.getLocalPort());
             Socket accepted = listener.accept();
             var deadlines = Executors.newSingleThreadScheduledExecutor();
             var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            client.setSoTimeout(3000);
            var future = workers.submit(new HttpConnection(accepted, req -> {
                try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return HttpResponse.ok("late");
            }, config, deadlines));
            client.getOutputStream().write("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            assertEquals(-1, client.getInputStream().read());
            future.get(3, TimeUnit.SECONDS);
        }
    }
}
