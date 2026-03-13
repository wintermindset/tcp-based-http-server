package com.wintermindset.handler;

import com.wintermindset.http.HttpRequest;
import com.wintermindset.http.HttpResponse;

/**
 * Functional interface representing an HTTP request handler.
 *
 * <p>A {@code Handler} processes an incoming {@link HttpRequest}
 * and produces a corresponding {@link HttpResponse}. Implementations
 * typically contain the application logic of the server.</p>
 *
 * <p>Handlers are invoked by the HTTP server infrastructure
 * (e.g. a connection handler) after a request has been parsed.</p>
 *
 * <p>Example implementation:</p>
 *
 * <pre>
 * public class HelloHandler implements Handler {
 *     public HttpResponse handle(HttpRequest req) {
 *         return HttpResponse.ok("Hello world");
 *     }
 * }
 * </pre>
 */
public interface Handler {

    /**
     * Processes an HTTP request and produces a response.
     *
     * @param req parsed HTTP request
     * @return HTTP response to send back to the client
     */
    HttpResponse handle(HttpRequest req);
}