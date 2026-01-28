package com.wintermindset.io;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BufferedOutputTest {

    @Test
    void writeAsciiAndFlush() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferedOutput bo = new BufferedOutput(out);
        bo.writeAscii("HTTP/1.1 200 OK");
        bo.writeCRLF();
        bo.flush();
        String result = out.toString(StandardCharsets.US_ASCII);
        assertEquals("HTTP/1.1 200 OK\r\n", result);
    }
}
