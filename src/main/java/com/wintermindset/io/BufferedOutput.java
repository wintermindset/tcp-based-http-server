package com.wintermindset.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class BufferedOutput {

    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    private final OutputStream out;
    private final byte[] buffer;
    private int position = 0;

    public BufferedOutput(OutputStream out) {
        this(out, DEFAULT_BUFFER_SIZE);
    }

    public BufferedOutput(OutputStream out, int bufferSize) {
        this.out = out;
        this.buffer = new byte[bufferSize];
    }

    public void write(byte[] data) throws IOException {
        write(data, 0, data.length);
    }

    public void write(byte[] data, int off, int len) throws IOException {
        int offset = off;
        while (len > 0) {
            int space = buffer.length - position;
            if (space == 0) {
                flushInternal();
                space = buffer.length;
            }
            int toCopy = Math.min(space, len);
            System.arraycopy(data, offset, buffer, position, toCopy);
            position += toCopy;
            offset += toCopy;
            len -= toCopy;
        }
    }

    public void writeAscii(String s) throws IOException {
        write(s.getBytes(StandardCharsets.US_ASCII));
    }

    public void writeCRLF() throws IOException {
        write(new byte[]{'\r', '\n'});
    }

    public void flush() throws IOException {
        flushInternal();
        out.flush();
    }

    public void close() throws IOException {
        out.close();
    }

    private void flushInternal() throws IOException {
        if (position > 0) {
            out.write(buffer, 0, position);
            position = 0;
        }
    }
}
