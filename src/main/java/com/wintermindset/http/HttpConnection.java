package com.wintermindset.http;

import com.wintermindset.handler.Handler;
import com.wintermindset.config.ServerConfig;
import com.wintermindset.io.BufferedInput;
import com.wintermindset.io.BufferedOutput;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Represents a single HTTP connection with a client.
 *
 * <p>This class is responsible for the full lifecycle of request handling
 * over a TCP socket. It reads incoming HTTP requests, delegates processing
 * to a {@link Handler}, and writes HTTP responses back to the client.</p>
 *
 * <p>The connection supports persistent HTTP/1.1 connections (keep-alive)
 * and may process multiple requests sequentially over the same socket.</p>
 *
 * <p>Typical workflow:</p>
 * <ol>
 *     <li>Read and parse an HTTP request</li>
 *     <li>Pass the request to a {@link Handler}</li>
 *     <li>Write the generated {@link HttpResponse}</li>
 *     <li>Repeat while the connection remains keep-alive</li>
 * </ol>
 *
 * <p>If a parsing error occurs, a {@code 400 Bad Request} response is sent.
 * If the handler throws an exception, a {@code 500 Internal Server Error}
 * response is returned.</p>
 */
public final class HttpConnection implements Runnable {

    private final Socket socket;
    private final Handler handler;
    private final BufferedInput in;
    private final BufferedOutput out;
    private final HttpRequestParser parser;
    private final ServerConfig config;
    private final ScheduledExecutorService deadlines;
    private final BooleanSupplier accepting;

    /**
     * Creates a new HTTP connection handler.
     *
     * @param socket client socket
     * @param handler request handler
     * @throws IOException if socket streams cannot be initialized
     */
    public HttpConnection(Socket socket, Handler handler, ServerConfig config, ScheduledExecutorService deadlines) throws IOException {
        this(socket, handler, config, deadlines, () -> true);
    }

    public HttpConnection(Socket socket, Handler handler, ServerConfig config, ScheduledExecutorService deadlines,
                          BooleanSupplier accepting) throws IOException {
        this.socket = socket;
        this.handler = handler;
        config.validateLimits();
        this.config = config.copy();
        this.deadlines = deadlines;
        this.accepting = accepting;
        this.parser = new HttpRequestParser(config.maxHeaders, config.maxHeaderBytes, config.maxBodyBytes);
        this.in = new BufferedInput(socket.getInputStream());
        this.out = new BufferedOutput(socket.getOutputStream());
    }

    /**
     * Main connection loop.
     *
     * <p>This method processes HTTP requests sequentially until the
     * connection is closed or the client disables keep-alive.</p>
     *
     * <p>Execution steps:</p>
     * <ul>
     *     <li>Parse an incoming HTTP request</li>
     *     <li>Delegate processing to the application handler</li>
     *     <li>Send the generated HTTP response</li>
     *     <li>Continue if the connection is persistent</li>
     * </ul>
     */
    @Override
    public void run() {
        ScheduledFuture<?> deadline = null;
        try (socket) {
            boolean keepAlive = true;
            int requests = 0;
            while (keepAlive && accepting.getAsBoolean() && !Thread.currentThread().isInterrupted()) {
                socket.setSoTimeout(config.idleTimeoutMillis);
                if (!in.hasData()) break;
                Thread worker = Thread.currentThread();
                deadline = deadlines.schedule(() -> {
                    try { socket.close(); } catch (IOException ignored) { }
                    worker.interrupt();
                }, config.requestTimeoutMillis, TimeUnit.MILLISECONDS);
                socket.setSoTimeout(config.readTimeoutMillis);
                HttpRequest req;
                try {
                    req = parser.parse(in);
                    if (req == null) break;
                } catch (SocketTimeoutException e) {
                    new HttpResponse().status(408, "Request Timeout").body("Request Timeout").writeTo(out, false);
                    break;
                } catch (HttpException e) {
                    new HttpResponse().status(e.status(), e.getMessage()).body(e.getMessage()).writeTo(out, false);
                    break;
                } catch (Exception e) {
                    HttpResponse.badRequest("Bad Request")
                            .writeTo(out, false);
                    break;
                }
                HttpResponse resp;
                try {
                    resp = java.util.Objects.requireNonNull(handler.handle(req), "Handler returned null");
                } catch (Exception e) {
                    resp = HttpResponse.internalError("Internal Error");
                }
                keepAlive = req.keepAlive() && resp.allowsKeepAlive() && accepting.getAsBoolean()
                        && ++requests < config.maxRequestsPerConnection;
                resp.writeTo(out, keepAlive, req);
                deadline.cancel(false);
                deadline = null;
                if (!keepAlive) {
                    break;
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (deadline != null) deadline.cancel(false);
        }
    }
}
