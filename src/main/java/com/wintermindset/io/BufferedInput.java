package com.wintermindset.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Buffered reader optimized for HTTP protocol parsing.
 *
 * <p>This class wraps an {@link InputStream} and provides efficient methods
 * for reading HTTP request components such as CRLF-terminated lines and
 * fixed-length bodies.</p>
 *
 * <p>The implementation minimizes object allocations by returning lightweight
 * {@link ByteSlice} views over the internal buffer instead of copying data
 * when reading header lines.</p>
 *
 * <p>Main features:</p>
 * <ul>
 *     <li>CRLF-based line reading (HTTP header format)</li>
 *     <li>Zero-copy line access via {@link ByteSlice}</li>
 *     <li>Buffered body reading</li>
 *     <li>Size limits for headers and bodies to prevent abuse</li>
 * </ul>
 */
public final class BufferedInput {

    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024;
    private static final int MAX_HEADER_SIZE = 8 * 1024;
    private static final int MAX_BODY_SIZE = 10 * 1024 * 1024;

    private final InputStream in;
    private final byte[] buffer;
    private int position = 0;
    private int limit = 0;
    private int scanPos = 0;

    /**
     * Creates a buffered reader with the default buffer size.
     *
     * @param in underlying input stream
     */
    public BufferedInput(InputStream in) {
        this(in, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Creates a buffered reader with a custom buffer size.
     *
     * @param in underlying input stream
     * @param bufferSize buffer size in bytes
     */
    public BufferedInput(InputStream in, int bufferSize) {
        this.in = in;
        this.buffer = new byte[bufferSize];
    }

    /**
     * Reads a CRLF-terminated line from the input and returns it as a
     * {@link ByteSlice}.
     *
     * <p>The returned slice references the internal buffer and does not
     * allocate additional memory.</p>
     *
     * @return slice representing the line (without CRLF)
     * @throws IOException if the line exceeds {@link #MAX_HEADER_SIZE}
     *                     or the connection is closed
     */
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

    /**
     * Reads a CRLF-terminated line and converts it to an ASCII string.
     *
     * @return decoded string
     * @throws IOException if reading fails
     */
    public String readLine() throws IOException {
        ByteSlice slice = readLineSlice();
        return slice.toStringAscii();
    }

    /**
     * Reads a fixed-length HTTP body.
     *
     * @param length number of bytes to read
     * @return body bytes
     * @throws IOException if the body exceeds {@link #MAX_BODY_SIZE}
     *                     or the connection is closed
     */
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

    /**
     * Fills the internal buffer with additional data from the stream.
     *
     * @throws IOException if the client closes the connection or the request
     *                     exceeds allowed size limits
     */
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

    /**
     * Compacts the buffer by moving unread data to the beginning.
     * This frees space for additional reads.
     */
    private void compact() {
        int remaining = limit - position;
        System.arraycopy(buffer, position, buffer, 0, remaining);
        position = 0;
        limit = remaining;
        scanPos = 0;
    }

    /**
     * Closes the underlying input stream.
     *
     * @throws IOException if closing fails
     */
    public void close() throws IOException {
        in.close();
    }

    /**
     * Lightweight immutable view over a portion of a byte array.
     *
     * <p>This class avoids copying data by referencing the original buffer
     * along with an offset and length.</p>
     */
    public static final class ByteSlice {
        public final byte[] data;
        public final int offset;
        public final int length;

        ByteSlice(byte[] data, int offset, int length) {
            this.data = data;
            this.offset = offset;
            this.length = length;
        }

        /**
         * Converts the slice to an ASCII string.
         *
         * @return decoded string
         */
        public String toStringAscii() {
            return new String(data, offset, length, StandardCharsets.US_ASCII);
        }
    }
}