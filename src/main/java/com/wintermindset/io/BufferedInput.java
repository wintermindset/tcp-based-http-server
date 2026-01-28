package com.wintermindset.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class BufferedInput {

    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024;
    private static final int MAX_HEADER_SIZE = 8 * 1024;
    private static final int MAX_BODY_SIZE = 10 * 1024 * 1024;

    private final InputStream in;
    private final byte[] buffer;
    private int position = 0;
    private int limit = 0;
    private int scanPos = 0;

    public BufferedInput(InputStream in) {
        this(in, DEFAULT_BUFFER_SIZE);
    }

    public BufferedInput(InputStream in, int bufferSize) {
        this.in = in;
        this.buffer = new byte[bufferSize];
    }

    public ByteSlice readLineSlice() throws IOException {
        while (true) {
            for (int i = scanPos; i + 1 < limit; i++) {
                if (buffer[i] == '\r' && buffer[i + 1] == '\n') {
                    int start = position;
                    int length = i - position;
                    position = i + 2;
                    scanPos = position;
                    if (length > MAX_HEADER_SIZE) {
                        throw new IOException("Header line too large");
                    }
                    return new ByteSlice(buffer, start, length);
                }
            }
            fill();
        }
    }

    public String readLine() throws IOException {
        ByteSlice slice = readLineSlice();
        return slice.toStringAscii();
    }

    public byte[] readBody(int length) throws IOException {
        if (length > MAX_BODY_SIZE) {
            throw new IOException("HTTP body too large");
        }
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            if (position == limit) {
                fill();
            }
            int toCopy = Math.min(length - read, limit - position);
            System.arraycopy(buffer, position, body, read, toCopy);
            position += toCopy;
            read += toCopy;
        }
        return body;
    }

    private void fill() throws IOException {
        if (position > 0) {
            compact();
        }
        int read = in.read(buffer, limit, buffer.length - limit);
        if (read == -1) {
            throw new IOException("Client closed connection");
        }
        limit += read;
        if (limit > MAX_HEADER_SIZE + MAX_BODY_SIZE) {
            throw new IOException("Request too large");
        }
    }

    private void compact() {
        int remaining = limit - position;
        System.arraycopy(buffer, position, buffer, 0, remaining);
        position = 0;
        limit = remaining;
        scanPos = 0;
    }

    public static final class ByteSlice {
        public final byte[] data;
        public final int offset;
        public final int length;

        ByteSlice(byte[] data, int offset, int length) {
            this.data = data;
            this.offset = offset;
            this.length = length;
        }

        public String toStringAscii() {
            return new String(data, offset, length, StandardCharsets.US_ASCII);
        }
    }
}
