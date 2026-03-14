package com.wintermindset.http;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a parsed HTTP request.
 *
 * <p>This class stores the basic components of an HTTP request:
 * the request line, headers, and optional message body.</p>
 *
 * <p>The request is typically created by {@link HttpRequestParser}
 * after parsing raw bytes received from a client connection.</p>
 *
 * <p>Example HTTP request:</p>
 *
 * <pre>
 * GET /index.html HTTP/1.1
 * Host: example.com
 * Connection: keep-alive
 * </pre>
 */
public final class HttpRequest {

    /** HTTP method (e.g. GET, POST, PUT, DELETE). */
    public String method;

    /** Request path or URI (e.g. "/index.html"). */
    public String path;

    /** HTTP protocol version (e.g. "HTTP/1.1"). */
    public String version;

    /** Map of HTTP headers. */
    public final Map<String, String> headers = new HashMap<>();

    /** Optional request body. Empty for most GET requests. */
    public byte[] body;

    /**
     * Determines whether the connection should remain open
     * after this request according to HTTP semantics.
     *
     * <p>Rules implemented:</p>
     * <ul>
     *     <li>HTTP/1.1 uses persistent connections by default</li>
     *     <li>{@code Connection: close} disables keep-alive</li>
     *     <li>HTTP/1.0 requires {@code Connection: keep-alive}</li>
     * </ul>
     *
     * @return {@code true} if the connection should be kept alive,
     * otherwise {@code false}
     */
    public boolean keepAlive() {
        String connection = headers.getOrDefault("connection", "");
        if ("HTTP/1.1".equals(version)) {
            return !"close".equalsIgnoreCase(connection);
        }
        return "keep-alive".equalsIgnoreCase(connection);
    }
}