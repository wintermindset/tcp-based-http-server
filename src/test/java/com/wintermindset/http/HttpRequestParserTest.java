package com.wintermindset.http;

import com.wintermindset.io.BufferedInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HttpRequestParserTest {
    private BufferedInput input(String wire) {
        return new BufferedInput(new ByteArrayInputStream(wire.getBytes(StandardCharsets.US_ASCII)) {
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                return super.read(b, off, Math.min(len, 1));
            }
        }, 8);
    }

    private HttpRequest parse(String wire) throws IOException {
        return new HttpRequestParser().parse(input(wire));
    }

    @Test
    void parsesBodyAndNextRequestWithFragmentedReads() throws Exception {
        BufferedInput in = input("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 4\r\n\r\na\r\nb"
                + "GET /next HTTP/1.1\r\nHost: localhost\r\nConnection: keep-alive, close\r\n\r\n");
        HttpRequestParser parser = new HttpRequestParser();
        assertArrayEquals("a\r\nb".getBytes(StandardCharsets.US_ASCII), parser.parse(in).body);
        HttpRequest next = parser.parse(in);
        assertEquals("/next", next.path);
        assertFalse(next.keepAlive());
        assertNull(parser.parse(in));
    }

    @Test
    void decodesChunksExtensionsAndTrailers() throws Exception {
        HttpRequest req = parse("POST / HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n"
                + "2;name=\"a;b\"\r\nab\r\n2\r\ncd\r\n0\r\nX-Checksum: ignored\r\n\r\n");
        assertEquals("abcd", new String(req.body, StandardCharsets.US_ASCII));
        assertTrue(req.keepAlive());
    }

    @Test
    void acceptsHttp10AndAbsoluteTarget() throws Exception {
        assertFalse(parse("GET / HTTP/1.0\r\n\r\n").keepAlive());
        HttpRequest req = parse("GET http://example.com/a?b=c HTTP/1.1\r\nHost: localhost\r\n\r\n");
        assertEquals("/a?b=c", req.path);
        assertEquals("example.com", req.headers.get("host"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "GET  / HTTP/1.1\r\nHost: localhost\r\n\r\n",
            "GET / HTTP/1.1\r\n\r\n",
            "GET / HTTP/1.1\r\nHost: localhost\r\nHost: other\r\n\r\n",
            "GET / HTTP/1.1\r\nHost : localhost\r\n\r\n",
            "GET / HTTP/1.1\r\nHost: localhost\r\nBroken\r\n\r\n",
            "GET / HTTP/1.1\r\nHost: localhost\r\n folded: no\r\n\r\n",
            "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: -1\r\n\r\n",
            "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: +1\r\n\r\nx",
            "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 1\r\nContent-Length: 2\r\n\r\nxx",
            "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 1,2\r\n\r\nxx",
            "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 0\r\nTransfer-Encoding: chunked\r\n\r\n",
            "POST / HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n1\r\nx!!",
            "POST / HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nContent-Length: 1\r\n\r\n"
    })
    void rejectsMalformedRequests(String wire) {
        assertEquals(400, assertThrows(HttpException.class, () -> parse(wire)).status());
    }

    @Test
    void reportsIncompleteRequestAndOversizedBody() {
        assertThrows(IOException.class, () -> parse("GET / HTTP/1.1\r\nHost: localhost\r\n"));
        assertThrows(IOException.class, () -> parse("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 2\r\n\r\nx"));
        assertEquals(413, assertThrows(HttpException.class, () -> parse(
                "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 999999999999999999\r\n\r\n")).status());
        assertEquals(431, assertThrows(HttpException.class, () -> parse(
                "GET / HTTP/1.1\r\nHost: localhost\r\n" + "X: a\r\n".repeat(100) + "\r\n")).status());
    }
}
