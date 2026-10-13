package com.wintermindset.http;

import com.wintermindset.io.BufferedInput;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HttpRequestParser {
    private final int maxHeaders;
    private final int maxHeaderBytes;
    private final int maxBodyBytes;
    private static final Set<String> SINGLE_HEADERS = Set.of("host", "content-length", "transfer-encoding");

    public HttpRequestParser() {
        this(100, 32768, 10 * 1024 * 1024);
    }

    public HttpRequestParser(int maxHeaders, int maxHeaderBytes, int maxBodyBytes) {
        if (maxHeaders <= 0 || maxHeaderBytes <= 0 || maxBodyBytes < 0 || maxBodyBytes > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid parser limits");
        }
        this.maxHeaders = maxHeaders;
        this.maxHeaderBytes = maxHeaderBytes;
        this.maxBodyBytes = maxBodyBytes;
    }

    public HttpRequest parse(BufferedInput in) throws IOException {
        if (!in.hasData()) return null;
        HttpRequest req = new HttpRequest();
        String[] parts = line(in).split(" ", -1);
        if (parts.length != 3 || !HttpSyntax.token(parts[0]) || parts[1].isEmpty()) throw badRequest();
        req.method = parts[0];
        req.version = parts[2];
        if (!req.version.equals("HTTP/1.1") && !req.version.equals("HTTP/1.0")) {
            throw new HttpException(505, "HTTP Version Not Supported");
        }
        req.path = target(parts[1], req.method);
        readHeaders(in, req.headers);
        if (req.version.equals("HTTP/1.1") && !req.headers.containsKey("host")) throw badRequest();
        if (req.headers.containsKey("host")) validateHost(req.headers.get("host"));
        if (parts[1].startsWith("http://") || parts[1].startsWith("https://")) {
            req.headers.put("host", URI.create(parts[1]).getRawAuthority());
        }
        if (req.headers.containsKey("expect")) throw new HttpException(417, "Expectation Failed");
        String transfer = req.headers.get("transfer-encoding");
        String length = req.headers.get("content-length");
        if (transfer != null) {
            if (length != null || !req.version.equals("HTTP/1.1")) throw badRequest();
            if (!transfer.equalsIgnoreCase("chunked")) throw new HttpException(501, "Not Implemented");
            req.body = chunks(in);
        } else {
            req.body = in.readBody(length == null ? 0 : bodyLength(length, 10));
        }
        return req;
    }

    private static String target(String value, String method) throws HttpException {
        if (value.chars().anyMatch(c -> c <= 32 || c >= 127)) throw badRequest();
        if (value.equals("*") && method.equals("OPTIONS")) return value;
        try {
            URI uri = URI.create(value);
            if (uri.getRawFragment() != null || uri.getRawUserInfo() != null) throw badRequest();
            if (value.startsWith("/") && !value.startsWith("//")) return value;
            if (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null) {
                validateHost(uri.getRawAuthority());
                String path = uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
                return path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            }
        } catch (IllegalArgumentException e) {
            throw badRequest();
        }
        throw badRequest();
    }

    private static void validateHost(String host) throws HttpException {
        try {
            URI uri = URI.create("http://" + host);
            if (host.isEmpty() || uri.getHost() == null || uri.getRawUserInfo() != null
                    || !uri.getRawPath().isEmpty() || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getPort() > 65535 || host.endsWith(":")) {
                throw badRequest();
            }
        } catch (IllegalArgumentException e) {
            throw badRequest();
        }
    }

    private void readHeaders(BufferedInput in, Map<String, String> headers) throws IOException {
        int count = 0;
        int bytes = 0;
        while (true) {
            String line = line(in);
            bytes += line.length() + 2;
            if (bytes > maxHeaderBytes) throw new HttpException(431, "Request Header Fields Too Large");
            if (line.isEmpty()) return;
            if (++count > maxHeaders) throw new HttpException(431, "Request Header Fields Too Large");
            int colon = line.indexOf(':');
            if (colon <= 0 || !HttpSyntax.token(line.substring(0, colon))
                    || !HttpSyntax.fieldValue(line.substring(colon + 1))) throw badRequest();
            String name = line.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (SINGLE_HEADERS.contains(name) && headers.containsKey(name)) throw badRequest();
            headers.merge(name, value, (a, b) -> a + "," + b);
        }
    }

    private int bodyLength(String value, int radix) throws HttpException {
        if (value.isEmpty() || !value.chars().allMatch(c ->
                c >= '0' && c <= '9' || radix == 16 && (c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F'))) {
            throw badRequest();
        }
        long size = 0;
        for (int i = 0; i < value.length(); i++) {
            size = size * radix + Character.digit(value.charAt(i), radix);
            if (size > maxBodyBytes) throw new HttpException(413, "Content Too Large");
        }
        return (int) size;
    }

    private byte[] chunks(BufferedInput in) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int metadataBytes = 0;
        while (true) {
            String line = line(in);
            metadataBytes += line.length() + 2;
            if (metadataBytes > maxHeaderBytes) throw new HttpException(413, "Content Too Large");
            int semicolon = line.indexOf(';');
            if (semicolon >= 0) validateExtensions(line.substring(semicolon));
            int size = bodyLength(semicolon < 0 ? line : line.substring(0, semicolon).stripTrailing(), 16);
            if (size == 0) {
                Map<String, String> trailers = new HashMap<>();
                readHeaders(in, trailers);
                if (trailers.keySet().stream().anyMatch(name -> SINGLE_HEADERS.contains(name)
                        || Set.of("connection", "expect", "trailer", "authorization", "content-type").contains(name))) {
                    throw badRequest();
                }
                return body.toByteArray();
            }
            if (size > maxBodyBytes - body.size()) throw new HttpException(413, "Content Too Large");
            body.writeBytes(in.readBody(size));
            byte[] crlf = in.readBody(2);
            if (crlf[0] != '\r' || crlf[1] != '\n') throw badRequest();
        }
    }

    private static void validateExtensions(String value) throws HttpException {
        int i = 0;
        while (i < value.length()) {
            if (value.charAt(i++) != ';') throw badRequest();
            while (i < value.length() && (value.charAt(i) == ' ' || value.charAt(i) == '\t')) i++;
            int start = i;
            while (i < value.length() && HttpSyntax.token(value.substring(i, i + 1))) i++;
            if (i == start) throw badRequest();
            while (i < value.length() && (value.charAt(i) == ' ' || value.charAt(i) == '\t')) i++;
            if (i < value.length() && value.charAt(i) == '=') {
                i++;
                while (i < value.length() && (value.charAt(i) == ' ' || value.charAt(i) == '\t')) i++;
                if (i < value.length() && value.charAt(i) == '"') {
                    i++;
                    boolean closed = false;
                    while (i < value.length()) {
                        char c = value.charAt(i++);
                        if (c == '"') { closed = true; break; }
                        if (c == '\\') {
                            if (i == value.length()) throw badRequest();
                            c = value.charAt(i++);
                        }
                        if (c != '\t' && (c < 32 || c >= 127)) throw badRequest();
                    }
                    if (!closed) throw badRequest();
                } else {
                    start = i;
                    while (i < value.length() && HttpSyntax.token(value.substring(i, i + 1))) i++;
                    if (i == start) throw badRequest();
                }
                while (i < value.length() && (value.charAt(i) == ' ' || value.charAt(i) == '\t')) i++;
            }
        }
    }

    private static String line(BufferedInput in) throws IOException {
        BufferedInput.ByteSlice slice = in.readLineSlice();
        return new String(slice.data, slice.offset, slice.length, StandardCharsets.ISO_8859_1);
    }

    private static HttpException badRequest() {
        return new HttpException(400, "Bad Request");
    }
}
