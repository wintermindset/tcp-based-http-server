package com.wintermindset.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.wintermindset.io.BufferedOutput;

/**
 * Represents an HTTP/1.1 response.
 *
 * <p>This class is responsible for building and serializing HTTP responses.
 * It stores the status line, headers and optional response body and provides
 * helper methods for writing the response to a {@link BufferedOutput}.</p>
 *
 * <p>Typical usage:</p>
 *
 * <pre>
 * HttpResponse resp = HttpResponse.ok("Hello world");
 * resp.writeTo(out, true);
 * </pre>
 *
 * <p>The implementation is intentionally minimal and designed for a small
 * custom HTTP server.</p>
 */
public final class HttpResponse {

    private int status = 200;
    private String reason = "OK";
    private byte[] body = new byte[0];
    private final Map<String, String> headers = new LinkedHashMap<>();

    /**
     * Creates a new HTTP response with a default {@code Server} header.
     */
    public HttpResponse() {
        header("Server", "Java-Loom");
    }

    /**
     * Sets the HTTP status line.
     *
     * @param status HTTP status code
     * @param reason reason phrase
     * @return current response instance
     */
    public HttpResponse status(int status, String reason) {
        this.status = status;
        this.reason = reason;
        return this;
    }

    /**
     * Sets a text response body encoded as UTF-8.
     *
     * <p>This method also automatically sets the
     * {@code Content-Type: text/plain; charset=utf-8} header.</p>
     *
     * @param body response body string
     * @return current response instance
     */
    public HttpResponse body(String body) {
        this.body = body.getBytes(StandardCharsets.UTF_8);
        header("Content-Type", "text/plain; charset=utf-8");
        return this;
    }

    /**
     * Sets a binary response body.
     *
     * @param body response body bytes
     * @return current response instance
     */
    public HttpResponse body(byte[] body) {
        this.body = body;
        return this;
    }

    /**
     * Adds or replaces an HTTP header.
     *
     * @param name header name
     * @param value header value
     * @return current response instance
     */
    public HttpResponse header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    /**
     * Writes the HTTP response to the provided output.
     *
     * <p>This method serializes the full HTTP message including:</p>
     * <ul>
     *     <li>status line</li>
     *     <li>headers</li>
     *     <li>blank line</li>
     *     <li>optional body</li>
     * </ul>
     *
     * <p>The method automatically sets:</p>
     * <ul>
     *     <li>{@code Content-Length}</li>
     *     <li>{@code Connection}</li>
     * </ul>
     *
     * @param out output writer
     * @param keepAlive whether the connection should remain open
     * @throws IOException if writing fails
     */
    public void writeTo(BufferedOutput out, boolean keepAlive) throws IOException {
        out.writeAscii("HTTP/1.1 ");
        out.writeAscii(Integer.toString(status));
        out.writeAscii(" ");
        out.writeAscii(reason);
        out.writeCRLF();
        header("Content-Length", Integer.toString(body.length));
        header("Connection", keepAlive ? "keep-alive" : "close");
        for (var entry : headers.entrySet()) {
            out.writeAscii(entry.getKey());
            out.writeAscii(": ");
            out.writeAscii(entry.getValue());
            out.writeCRLF();
        }
        out.writeCRLF();
        if (body.length > 0) {
            out.write(body);
        }
        out.flush();
    }

    /**
     * Creates a standard {@code 200 OK} response with a text body.
     *
     * @param body response text
     * @return HTTP response
     */
    public static HttpResponse ok(String body) {
        return new HttpResponse().status(200, "OK").body(body);
    }

    /**
     * Creates a {@code 400 Bad Request} response.
     *
     * @param msg error message
     * @return HTTP response
     */
    public static HttpResponse badRequest(String msg) {
        return new HttpResponse().status(400, "Bad Request").body(msg);
    }

    /**
     * Creates a {@code 404 Not Found} response.
     *
     * @return HTTP response
     */
    public static HttpResponse notFound() {
        return new HttpResponse().status(404, "Not Found").body("Not Found");
    }

    /**
     * Creates a {@code 500 Internal Server Error} response.
     *
     * @param msg error message
     * @return HTTP response
     */
    public static HttpResponse internalError(String msg) {
        return new HttpResponse().status(500, "Internal Server Error").body(msg);
    }
}