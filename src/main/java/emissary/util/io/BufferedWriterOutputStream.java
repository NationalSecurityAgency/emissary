package emissary.util.io;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

/**
 * BufferedWriterOutputStream: a subclass to support writing bytes to a Writer. This implementation also buffers the
 * output to avoid multiple array allocations. Each byte is written as a seperate character (ISO8859-1 encoding per se).
 */
public class BufferedWriterOutputStream extends OutputStream {
    /** the writer to write to */
    private Writer writer = null;
    private char[] buffer = null;
    private int bufferLength = 0;

    public BufferedWriterOutputStream(Writer writer, int blockSize) {
        this.writer = writer;
        buffer = new char[blockSize];
    }

    public BufferedWriterOutputStream(Writer writer) {
        this(writer, 4096);
    }

    @Override
    public void write(byte[] bytes, int start, int length) throws IOException {
        // while we have bytes to write
        while (length > 0) {
            // write up to buffer.length bytes (minus what we already have)
            int chunkSize = buffer.length - bufferLength;
            if (length < chunkSize) {
                chunkSize = length;
            }
            // convert this set of bytes to a character array
            for (int i = 0; i < chunkSize; i++) {
                // copy in byte as a character (ensure we do not get negative)
                buffer[bufferLength++] = (char) (0xFF & (int) (bytes[i + start]));
            }

            // and write it if full
            if (bufferLength == buffer.length) {
                writer.write(buffer);
                bufferLength = 0;
            }

            // reset out pointers
            start += chunkSize;
            length -= chunkSize;
        }
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        write(bytes, 0, bytes.length);
    }

    @Override
    public void write(int b) throws IOException {
        buffer[bufferLength++] = ((char) (0xFF & b));
        // and write it if full
        if (bufferLength == buffer.length) {
            writer.write(buffer);
            bufferLength = 0;
        }
    }

    @Override
    public void flush() throws IOException {
        if (bufferLength > 0) {
            writer.write(buffer, 0, bufferLength);
            bufferLength = 0;
        }
    }
}
