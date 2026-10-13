package com.wintermindset.handler;

import com.wintermindset.http.HttpRequest;
import com.wintermindset.http.HttpResponse;

import java.util.HashMap;
import java.util.Map;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * HTTP handler that exposes a simple calculator endpoint.
 *
 * <p>The handler processes {@code GET} requests sent to the
 * {@code /calc} path and performs a basic arithmetic operation
 * using query parameters.</p>
 *
 * <p>Supported query parameters:</p>
 * <ul>
 *     <li>{@code x} – first operand</li>
 *     <li>{@code y} – second operand</li>
 *     <li>{@code op} – arithmetic operator</li>
 * </ul>
 *
 * <p>Supported operators:</p>
 * <ul>
 *     <li>{@code +} addition</li>
 *     <li>{@code -} subtraction</li>
 *     <li>{@code *} multiplication</li>
 *     <li>{@code /} division</li>
 * </ul>
 *
 * <p>Example request:</p>
 *
 * <pre>
 * GET /calc?x=10&y=5&op=+
 * </pre>
 *
 * <p>Response:</p>
 *
 * <pre>
 * 15.0
 * </pre>
 */
public final class CalcHandler implements Handler {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Processes an incoming HTTP request.
     *
     * <p>The handler validates the request method and path,
     * extracts query parameters, performs the requested
     * arithmetic operation, and returns the result.</p>
     *
     * @param req parsed HTTP request
     * @return HTTP response containing the calculation result
     */
    public HttpResponse handle(HttpRequest req) {
        if (!req.path.split("\\?", 2)[0].equals("/calc")) {
            return HttpResponse.notFound();
        }
        if (!"GET".equals(req.method) && !"HEAD".equals(req.method)) {
            return new HttpResponse().status(405, "Method Not Allowed").header("Allow", "GET, HEAD").body("Only GET and HEAD supported");
        }
        try {
            Map<String, String> params = parseQuery(req.path);
            double x = Double.parseDouble(params.get("x"));
            double y = Double.parseDouble(params.get("y"));
            if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("Non-finite operand");
            String op = params.get("op");
            double result = switch (op) {
                case "+" -> x + y;
                case "-" -> x - y;
                case "*" -> x * y;
                case "/" -> {
                    if (y == 0) throw new ArithmeticException("Division by zero");
                    yield x / y;
                }
                default -> throw new IllegalArgumentException("Unknown op");
            };
            if (!Double.isFinite(result)) throw new IllegalArgumentException("Non-finite result");
            LOGGER.trace("{} {} {} = {}", x, op, y, result);
            return HttpResponse.ok(Double.toString(result));
        } catch (Exception e) {
            LOGGER.debug("Invalid calculator parameters");
            return HttpResponse.badRequest("Invalid parameters");
        }
    }

    /**
     * Parses query parameters from the request path.
     *
     * <p>The method extracts the query string portion of the URL
     * and converts it into a map of key-value pairs.</p>
     *
     * <p>Example:</p>
     *
     * <pre>
     * /calc?x=10&y=5&op=+
     * </pre>
     *
     * becomes:
     *
     * <pre>
     * {
     *   x=10,
     *   y=5,
     *   op=+
     * }
     * </pre>
     *
     * @param path request path containing the query string
     * @return map of parsed query parameters
     */
    private Map<String, String> parseQuery(String path) {
        Map<String, String> map = new HashMap<>();
        int q = path.indexOf('?');
        if (q < 0) return map;
        String[] pairs = path.substring(q + 1).split("&");
        for (String p : pairs) {
            int eq = p.indexOf('=');
            if (eq > 0) {
                String name = URLDecoder.decode(p.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(p.substring(eq + 1), StandardCharsets.UTF_8);
                if (map.putIfAbsent(name, value) != null) throw new IllegalArgumentException("Duplicate parameter");
            }
        }
        return map;
    }
}
