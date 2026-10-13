package com.wintermindset.http;

import com.wintermindset.config.ServerConfig;
import com.wintermindset.core.ServerMetrics;
import com.wintermindset.handler.Handler;
import com.wintermindset.io.BufferedInput;
import com.wintermindset.io.BufferedOutput;
import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class HttpConnection implements Runnable {
    private static final Logger LOGGER = LogManager.getLogger(HttpConnection.class);
    private final Socket socket;
    private final Handler handler;
    private final BufferedInput in;
    private final BufferedOutput out;
    private final HttpRequestParser parser;
    private final ServerConfig config;
    private final ScheduledExecutorService deadlines;
    private final BooleanSupplier accepting;
    private final ServerMetrics metrics;

    public HttpConnection(Socket socket, Handler handler, ServerConfig config, ScheduledExecutorService deadlines) throws IOException {
        this(socket, handler, config, deadlines, () -> true, new ServerMetrics());
    }

    public HttpConnection(Socket socket, Handler handler, ServerConfig config, ScheduledExecutorService deadlines,
                          BooleanSupplier accepting, ServerMetrics metrics) throws IOException {
        config.validateLimits();
        this.socket = socket;
        this.handler = handler;
        this.config = config.copy();
        this.deadlines = deadlines;
        this.accepting = accepting;
        this.metrics = metrics;
        this.in = new BufferedInput(socket.getInputStream());
        this.out = new BufferedOutput(socket.getOutputStream());
        this.parser = new HttpRequestParser(config.maxHeaders, config.maxHeaderBytes, config.maxBodyBytes);
    }

    @Override
    public void run() {
        try (socket) {
            for (int count = 1; count <= config.maxRequestsPerConnection && accepting.getAsBoolean()
                    && !Thread.currentThread().isInterrupted(); count++) {
                socket.setSoTimeout(config.idleTimeoutMillis);
                if (!in.hasData() || !accepting.getAsBoolean()) break;
                if (!request(count)) break;
            }
        } catch (SocketTimeoutException e) {
            metrics.timeouts.increment();
            LOGGER.debug("Idle connection timed out");
        } catch (IOException e) {
            LOGGER.debug("Connection closed during I/O", e);
        }
    }

    private boolean request(int count) throws IOException {
        long start = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        Thread worker = Thread.currentThread();
        var deadline = deadlines.schedule(() -> {
            metrics.timeouts.increment();
            LOGGER.warn("Request deadline exceeded: {}", requestId);
            try { socket.close(); } catch (IOException ignored) { }
            worker.interrupt();
        }, config.requestTimeoutMillis, TimeUnit.MILLISECONDS);
        int status = 0;
        try (var context = CloseableThreadContext.put("request.id", requestId)) {
            socket.setSoTimeout(config.readTimeoutMillis);
            HttpRequest req = null;
            HttpResponse response;
            boolean keepAlive = false;
            try {
                req = parser.parse(in);
                if (req == null) return false;
                ThreadContext.put("http.method", req.method);
                try {
                    response = Objects.requireNonNull(handler.handle(req), "Handler returned null");
                    if (response.status() < 200) throw new IllegalArgumentException("Handler must return a final response");
                } catch (Exception e) {
                    LOGGER.error("Request handler failed", e);
                    response = HttpResponse.internalError("Internal Error");
                }
                keepAlive = req.keepAlive() && response.allowsKeepAlive() && accepting.getAsBoolean()
                        && count < config.maxRequestsPerConnection;
            } catch (SocketTimeoutException e) {
                metrics.timeouts.increment();
                response = new HttpResponse().status(408, "Request Timeout").body("Request Timeout");
            } catch (HttpException e) {
                response = new HttpResponse().status(e.status(), e.getMessage()).body(e.getMessage());
            } catch (IOException | IllegalArgumentException e) {
                LOGGER.debug("Incomplete or malformed request", e);
                response = HttpResponse.badRequest("Bad Request");
            }
            response.header("X-Request-Id", requestId);
            response.writeTo(out, keepAlive, req);
            status = response.status();
            return keepAlive;
        } finally {
            deadline.cancel(false);
            long elapsed = System.nanoTime() - start;
            metrics.record(status, elapsed);
            if (config.logRequests) {
                try (var context = CloseableThreadContext.put("request.id", requestId)
                        .put("http.status_code", Integer.toString(status)).put("duration_ns", Long.toString(elapsed))) {
                    LOGGER.info("Request completed");
                }
            }
            ThreadContext.remove("http.method");
        }
    }
}
