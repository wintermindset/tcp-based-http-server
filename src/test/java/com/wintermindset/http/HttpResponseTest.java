package com.wintermindset.http;

import com.wintermindset.io.BufferedOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HttpResponseTest {
    private String wire(HttpResponse response, String method) throws IOException {
        HttpRequest req = new HttpRequest();
        req.method = method;
        req.version = "HTTP/1.1";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        response.writeTo(new BufferedOutput(bytes, 3), true, req);
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void countsUtf8BytesAndReplacesFramingCaseInsensitively() throws Exception {
        String wire = wire(HttpResponse.ok("я").header("content-length", "999"), "GET");
        assertTrue(wire.toLowerCase().contains("content-length: 2\r\n"));
        assertFalse(wire.contains("999"));
        assertEquals(1, wire.toLowerCase().split("content-length:", -1).length - 1);
        assertTrue(wire.endsWith("\r\n\r\nя"));
        assertTrue(wire.contains("Date: "));
    }

    @Test
    void headKeepsRepresentationLengthWithoutBody() throws Exception {
        String wire = wire(HttpResponse.ok("hello"), "HEAD");
        assertTrue(wire.contains("Content-Length: 5\r\n"));
        assertTrue(wire.endsWith("\r\n\r\n"));
        assertFalse(wire.contains("hello"));
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 204, 304})
    void bodylessStatusesHaveNoBodyOrContentLength(int status) throws Exception {
        String wire = wire(new HttpResponse().status(status, "Test").body("hidden"), "GET");
        assertFalse(wire.contains("Content-Length"));
        assertFalse(wire.contains("hidden"));
        assertTrue(wire.endsWith("\r\n\r\n"));
    }

    @Test
    void resetContentHasZeroLength() throws Exception {
        String wire = wire(new HttpResponse().status(205, "Reset Content").body("hidden"), "GET");
        assertTrue(wire.contains("Content-Length: 0\r\n"));
        assertTrue(wire.endsWith("\r\n\r\n"));
    }

    @Test
    void rejectsInjectionAndUnsupportedTransferCoding() {
        HttpResponse response = new HttpResponse();
        assertThrows(IllegalArgumentException.class, () -> response.header("X\r\nY", "z"));
        assertThrows(IllegalArgumentException.class, () -> response.header("X", "a\r\nY: z"));
        assertThrows(IllegalArgumentException.class, () -> response.status(200, "OK\r\nX: y"));
        assertThrows(IllegalArgumentException.class, () -> response.status(999, "Invalid"));
        assertThrows(IllegalArgumentException.class, () -> response.header("transfer-encoding", "chunked"));
    }

    @Test
    void honorsHandlerConnectionClose() throws Exception {
        HttpResponse response = HttpResponse.ok("ok").header("connection", "keep-alive, close");
        assertFalse(response.allowsKeepAlive());
        assertTrue(wire(response, "GET").toLowerCase().contains("connection: close\r\n"));
    }
}
