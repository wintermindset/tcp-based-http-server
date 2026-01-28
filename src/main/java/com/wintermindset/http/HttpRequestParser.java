package com.wintermindset.http;

import java.io.IOException;

import com.wintermindset.io.BufferedInput;
import com.wintermindset.io.BufferedInput.ByteSlice;

public final class HttpRequestParser {

    public HttpRequest parse(BufferedInput in) throws IOException {
        HttpRequest req = new HttpRequest();
        ByteSlice requestLine = in.readLineSlice();
        if (requestLine.length == 0) {
            throw new IOException("Empty request line");
        }
        parseRequestLine(requestLine, req);
        while (true) {
            ByteSlice line = in.readLineSlice();
            if (line.length == 0) {
                break; // end of headers
            }
            parseHeader(line, req);
        }
        // Body
        int contentLength = getContentLength(req);
        if (contentLength > 0) {
            req.body = in.readBody(contentLength);
        } else {
            req.body = new byte[0];
        }
        return req;
    }

    private void parseRequestLine(ByteSlice slice, HttpRequest req) {
        int p1 = indexOf(slice, (byte) ' ');
        int p2 = indexOf(slice, (byte) ' ', p1 + 1);
        if (p1 < 0 || p2 < 0) {
            throw new IllegalArgumentException("Invalid request line");
        }
        req.method = ascii(slice, 0, p1);
        req.path = ascii(slice, p1 + 1, p2 - p1 - 1);
        req.version = ascii(slice, p2 + 1, slice.length - p2 - 1);
    }

    private void parseHeader(ByteSlice slice, HttpRequest req) {
        int colon = indexOf(slice, (byte) ':');
        if (colon <= 0) {
            return;
        }
        String name = ascii(slice, 0, colon).toLowerCase();
        String value = ascii(slice, colon + 1, slice.length - colon - 1).trim();
        req.headers.put(name, value);
    }

    private int getContentLength(HttpRequest req) {
        String v = req.headers.get("content-length");
        return v == null ? 0 : Integer.parseInt(v);
    }

    private int indexOf(ByteSlice slice, byte b) {
        return indexOf(slice, b, 0);
    }

    private int indexOf(ByteSlice slice, byte b, int from) {
        for (int i = from; i < slice.length; i++) {
            if (slice.data[slice.offset + i] == b) {
                return i;
            }
        }
        return -1;
    }

    private String ascii(ByteSlice s, int off, int len) {
        return new String(s.data, s.offset + off, len);
    }
}
