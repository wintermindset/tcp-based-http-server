package com.wintermindset.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Buffered writer optimized for HTTP response serialization.
 *
 * <p>This class wraps an {@link OutputStream} and provides buffered
 * write operations to reduce the number of system calls when sending
 * data over a network connection.</p>
 *
 * <p>The buffer accumulates written bytes and flushes them to the
 * underlying stream when the buffer becomes full or when
 * {@link #flush()} is explicitly called.</p>
 *
 * <p>Typical usage:</p>
 *
 * <pre>
 * BufferedOutput out = new BufferedOutput(socket.getOutputStream());
 * out.writeAscii("HTTP/1.1 200 OK");
 * out.writeCRLF();
 * out.flush();
 * </pre>
 */
public final class BufferedOutput {

    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    private final OutputStream out;
    private final byte[] buffer;
    private int position = 0;

    /**
     * Creates a buffered output wrapper with the default buffer size.
     *
     * @param out underlying output stream
     */
    public BufferedOutput(OutputStream out) {
        this(out, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Creates a buffered output wrapper with a custom buffer size.
     *
     * @param out underlying output stream
     * @param bufferSize size of the internal buffer
     */
    public BufferedOutput(OutputStream out, int bufferSize) {
        this.out = out;
        this.buffer = new byte[bufferSize];
    }

    /**
     * Writes the entire byte array to the buffer.
     *
     * @param data data to write
     * @throws IOException if writing fails
     */
    public void write(byte[] data) throws IOException {
        write(data, 0, data.length);
    }

    /**
     * Writes a portion of a byte array to the buffer.
     *
     * <p>If the buffer becomes full, it is automatically flushed to the
     * underlying stream.</p>
     *
     * @param data source byte array
     * @param off starting offset
     * @param len number of bytes to write
     * @throws IOException if writing fails
     */
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

    /**
     * Writes an ASCII string to the buffer.
     *
     * @param s ASCII string
     * @throws IOException if writing fails
     */
    public void writeAscii(String s) throws IOException {
        write(s.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Writes a CRLF sequence ({@code \r\n}).
     *
     * <p>This sequence is used as a line terminator in HTTP messages.</p>
     *
     * @throws IOException if writing fails
     */
    public void writeCRLF() throws IOException {
        write(new byte[]{'\r', '\n'});
    }

    /**
     * Flushes buffered data to the underlying stream.
     *
     * @throws IOException if flushing fails
     */
    public void flush() throws IOException {
        flushInternal();
        out.flush();
    }

    /**
     * Closes the underlying output stream.
     *
     * @throws IOException if closing fails
     */
    public void close() throws IOException {
        out.close();
    }

    /**
     * Writes buffered data to the underlying stream and
     * resets the buffer position.
     *
     * @throws IOException if writing fails
     */
    private void flushInternal() throws IOException {
        if (position > 0) {
            out.write(buffer, 0, position);
            position = 0;
        }
    }
}