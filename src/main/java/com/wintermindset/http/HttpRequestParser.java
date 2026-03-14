package com.wintermindset.http;

import java.io.IOException;

import com.wintermindset.io.BufferedInput;
import com.wintermindset.io.BufferedInput.ByteSlice;

/**
 * Parser for HTTP/1.1 requests.
 *
 * <p>This class reads raw bytes from {@link BufferedInput} and converts them
 * into a {@link HttpRequest} object. The parser processes:</p>
 *
 * <ul>
 *     <li>request line (method, path, version)</li>
 *     <li>HTTP headers</li>
 *     <li>optional message body</li>
 * </ul>
 *
 * <p>The implementation is intentionally minimal and intended for educational
 * purposes (e.g. implementing a simple HTTP server).</p>
 *
 * <p>Limitations:</p>
 * <ul>
 *     <li>Only supports bodies with <code>Content-Length</code></li>
 *     <li>No chunked encoding support</li>
 *     <li>No validation of HTTP version</li>
 * </ul>
 */
public final class HttpRequestParser {

    /**
     * Parses an HTTP request from the given buffered input stream.
     *
     * <p>The method reads:</p>
     * <ol>
     *     <li>The request line</li>
     *     <li>All HTTP headers</li>
     *     <li>The request body (if {@code Content-Length} is present)</li>
     * </ol>
     *
     * @param in buffered input source containing the raw HTTP request
     * @return parsed {@link HttpRequest}
     * @throws IOException if the request cannot be read or the request line is empty
     */
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

    /**
     * Parses the HTTP request line.
     *
     * <p>Example request line:</p>
     *
     * <pre>
     * GET /index.html HTTP/1.1
     * </pre>
     *
     * @param slice byte slice containing the request line
     * @param req request object to populate
     * @throws IllegalArgumentException if the request line format is invalid
     */
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

    /**
     * Parses a single HTTP header line and stores it in the request.
     *
     * <p>Example header:</p>
     *
     * <pre>
     * Content-Type: application/json
     * </pre>
     *
     * @param slice byte slice containing the header line
     * @param req request object to update
     */
    private void parseHeader(ByteSlice slice, HttpRequest req) {
        int colon = indexOf(slice, (byte) ':');
        if (colon <= 0) {
            return;
        }
        String name = ascii(slice, 0, colon).toLowerCase();
        String value = ascii(slice, colon + 1, slice.length - colon - 1).trim();
        req.headers.put(name, value);
    }

    /**
     * Extracts the {@code Content-Length} header value.
     *
     * @param req parsed request
     * @return body length in bytes, or {@code 0} if the header is not present
     */
    private int getContentLength(HttpRequest req) {
        String v = req.headers.get("content-length");
        return v == null ? 0 : Integer.parseInt(v);
    }

    /**
     * Finds the first occurrence of a byte in the slice.
     *
     * @param slice source byte slice
     * @param b byte to search for
     * @return index of the byte or {@code -1} if not found
     */
    private int indexOf(ByteSlice slice, byte b) {
        return indexOf(slice, b, 0);
    }

    /**
     * Finds the first occurrence of a byte in the slice starting
     * from the specified position.
     *
     * @param slice source byte slice
     * @param b byte to search for
     * @param from starting position
     * @return index of the byte or {@code -1} if not found
     */
    private int indexOf(ByteSlice slice, byte b, int from) {
        for (int i = from; i < slice.length; i++) {
            if (slice.data[slice.offset + i] == b) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Converts a region of a byte slice to a {@link String}.
     *
     * <p>Used for decoding ASCII parts of the HTTP request such as
     * method, path, version and header fields.</p>
     *
     * @param s source slice
     * @param off offset inside the slice
     * @param len number of bytes
     * @return decoded string
     */
    private String ascii(ByteSlice s, int off, int len) {
        return new String(s.data, s.offset + off, len);
    }
}