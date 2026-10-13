package com.wintermindset.io;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BufferedOutputTest {

    @Test
    void writesAcrossBoundariesAndFlushesOnClose() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BufferedOutput out = new BufferedOutput(bytes, 2);
        out.write("abcdef".getBytes(StandardCharsets.US_ASCII), 1, 4);
        out.close();
        out.close();
        assertEquals("bcde", bytes.toString(StandardCharsets.US_ASCII));
        assertThrows(IOException.class, () -> out.writeAscii("x"));
        assertThrows(IOException.class, out::flush);
    }

    @Test
    void validatesBufferSizeAndWriteRange() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThrows(IllegalArgumentException.class, () -> new BufferedOutput(bytes, 0));
        BufferedOutput out = new BufferedOutput(bytes);
        assertThrows(IndexOutOfBoundsException.class, () -> out.write(new byte[2], 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> out.write(new byte[2], 1, 2));
    }

    @Test
    void closesUnderlyingStreamEvenWhenFlushFails() {
        boolean[] closed = {false};
        OutputStream stream = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("write failed");
            }

            @Override
            public void close() {
                closed[0] = true;
            }
        };
        BufferedOutput out = new BufferedOutput(stream);
        assertDoesNotThrow(() -> out.writeAscii("x"));
        assertThrows(IOException.class, out::close);
        assertTrue(closed[0]);
    }

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
