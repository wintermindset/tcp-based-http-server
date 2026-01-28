package com.wintermindset.http;

import java.util.HashMap;
import java.util.Map;

public final class HttpRequest {

    public String method;
    public String path;
    public String version;
    public final Map<String, String> headers = new HashMap<>();
    public byte[] body;

    public boolean keepAlive() {
        String connection = headers.getOrDefault("connection", "");
        if ("HTTP/1.1".equals(version)) {
            return !"close".equalsIgnoreCase(connection);
        }
        return "keep-alive".equalsIgnoreCase(connection);
    }
}
