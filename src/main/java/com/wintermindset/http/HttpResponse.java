package com.wintermindset.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.wintermindset.io.BufferedOutput;

public final class HttpResponse {

    private int status = 200;
    private String reason = "OK";
    private byte[] body = new byte[0];
    private final Map<String, String> headers = new LinkedHashMap<>();

    public HttpResponse() {
        header("Server", "Java-Loom");
    }

    public HttpResponse status(int status, String reason) {
        this.status = status;
        this.reason = reason;
        return this;
    }

    public HttpResponse body(String body) {
        this.body = body.getBytes(StandardCharsets.UTF_8);
        header("Content-Type", "text/plain; charset=utf-8");
        return this;
    }

    public HttpResponse body(byte[] body) {
        this.body = body;
        return this;
    }

    public HttpResponse header(String name, String value) {
        headers.put(name, value);
        return this;
    }

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

    public static HttpResponse ok(String body) {
        return new HttpResponse().status(200, "OK").body(body);
    }

    public static HttpResponse badRequest(String msg) {
        return new HttpResponse().status(400, "Bad Request").body(msg);
    }

    public static HttpResponse notFound() {
        return new HttpResponse().status(404, "Not Found").body("Not Found");
    }

    public static HttpResponse internalError(String msg) {
        return new HttpResponse().status(500, "Internal Server Error").body(msg);
    }
}
