package com.wintermindset.handler;

import com.wintermindset.http.HttpRequest;
import com.wintermindset.http.HttpResponse;

import java.util.HashMap;
import java.util.Map;

public final class CalcHandler implements Handler {

    public HttpResponse handle(HttpRequest req) {
        if (!"GET".equals(req.method)) {
            return HttpResponse.badRequest("Only GET supported");
        }
        if (!req.path.startsWith("/calc")) {
            return HttpResponse.notFound();
        }
        Map<String, String> params = parseQuery(req.path);
        try {
            double x = Double.parseDouble(params.get("x"));
            double y = Double.parseDouble(params.get("y"));
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
            return HttpResponse.ok(Double.toString(result));
        } catch (Exception e) {
            return HttpResponse.badRequest("Invalid parameters");
        }
    }

    private Map<String, String> parseQuery(String path) {
        Map<String, String> map = new HashMap<>();
        int q = path.indexOf('?');
        if (q < 0) return map;
        String[] pairs = path.substring(q + 1).split("&");
        for (String p : pairs) {
            int eq = p.indexOf('=');
            if (eq > 0) {
                map.put(p.substring(0, eq), p.substring(eq + 1));
            }
        }
        return map;
    }
}
