package com.wintermindset.io;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BufferedInputTest {

    private BufferedInput input(String text, int size) {
        return new BufferedInput(new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII)), size);
    }

    @Test
    void growsForLinesAndPreservesSplitCrLf() throws Exception {
        BufferedInput in = input("abcd\r\nnext\r\n", 1);
        assertEquals("abcd", in.readLine());
        assertEquals("next", in.readLine());
    }

    @Test
    void acceptsMaximumLineAndRejectsLongerLine() throws Exception {
        assertEquals(8192, input("a".repeat(8192) + "\r\n", 8192).readLine().length());
        assertThrows(IOException.class, () -> input("a".repeat(8193) + "\r\n", 8192).readLine());
        assertThrows(IOException.class, () -> input("a".repeat(9000), 8192).readLine());
    }

    @Test
    void reportsIncompleteLinesAndBodies() {
        assertThrows(EOFException.class, () -> input("abc\r", 2).readLine());
        assertThrows(EOFException.class, () -> input("abc", 2).readBody(4));
    }

    @Test
    void bodyDoesNotLeaveLineScannerBehind() throws Exception {
        BufferedInput in = input("header\r\na\r\nbnext\r\n", 64);
        assertEquals("header", in.readLine());
        assertArrayEquals("a\r\nb".getBytes(StandardCharsets.US_ASCII), in.readBody(4));
        assertEquals("next", in.readLine());
    }

    @Test
    void readsBodyAcrossBufferBoundaries() throws Exception {
        BufferedInput in = input("header\r\nabcdefnext\r\n", 3);
        assertEquals("header", in.readLine());
        assertArrayEquals("abcdef".getBytes(StandardCharsets.US_ASCII), in.readBody(6));
        assertEquals("next", in.readLine());
    }

    @Test
    void rejectsInvalidSizesAndReadsAfterClose() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> input("", 0));
        BufferedInput in = input("abc", 2);
        assertThrows(IOException.class, () -> in.readBody(-1));
        assertThrows(IOException.class, () -> in.readBody(10 * 1024 * 1024 + 1));
        in.close();
        in.close();
        assertThrows(IOException.class, in::readLine);
        assertThrows(IOException.class, () -> in.readBody(0));
    }

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
