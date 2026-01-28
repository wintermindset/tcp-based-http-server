package com.wintermindset.io;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BufferedInputTest {

    @Test
    void readLineSimple() throws Exception {
        byte[] data = "GET / HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII);
        BufferedInput in = new BufferedInput(new ByteArrayInputStream(data));
        String line = in.readLine();
        assertEquals("GET / HTTP/1.1", line);
    }

    @Test
    void readLineMultiple() throws Exception {
        byte[] data = (
                "line1\r\n" +
                "line2\r\n"
        ).getBytes(StandardCharsets.US_ASCII);
        BufferedInput in = new BufferedInput(new ByteArrayInputStream(data));
        assertEquals("line1", in.readLine());
        assertEquals("line2", in.readLine());
    }
}
