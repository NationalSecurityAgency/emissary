package emissary.util.io;

import emissary.util.web.HtmlEscaper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This buffer implementation attempts to improve file creation performance by preventing conversion from byte array to
 * string and then back to byte array for writing to a stream. The append method here accepts byte arrays and strings.
 * In the case of a byte array it simply copies the bytes. Very fast. In the case of strings it attempts to convert the
 * string to a byte array once and save the results. In this way we avoid converting each time. If many constant string
 * literals are passed to this class, it will be faster. This string buffer will now accept an output stream in addition
 * which allows this string buffer to act as a buffered output stream.
 */
public class FastStringBuffer extends OutputStream {

    protected static final Logger logger = LoggerFactory.getLogger(FastStringBuffer.class);

    public static final int MAX_CACHE_SIZE = 256;
    private static final byte[] CRBYTES = "\n".getBytes();
    private static final byte[] CRLFBYTES = "\r\n".getBytes();

    static final Map<String, byte[]> strings = new HashMap<>(MAX_CACHE_SIZE * 3);

    protected int curPos = 0;
    protected byte[] buffer;
    @Nullable
    protected String myString = null;
    protected OutputStream stream;
    protected int bytesWritten = 0;

    public FastStringBuffer() {
        this(null);
    }

    public FastStringBuffer(@Nullable final OutputStream stream) {
        this(1024, stream);
    }

    public FastStringBuffer(final int initialSize) {
        this(initialSize, null);
    }

    public FastStringBuffer(final int initialSize, @Nullable final OutputStream stream) {
        this.buffer = new byte[initialSize];
        this.stream = stream;
    }

    public FastStringBuffer append(final String s) throws IOException {
        return append(s, StandardCharsets.ISO_8859_1.name());
    }

    public FastStringBuffer append(final int i) throws IOException {
        return append(String.valueOf(i));
    }

    public FastStringBuffer append(final byte[] a) throws IOException {
        write(a);
        return this;
    }

    public FastStringBuffer append(final byte[] a, final int start, final int length) throws IOException {
        write(a, start, length);
        return this;
    }

    public FastStringBuffer append(@Nullable final String s, final String charset) throws IOException {
        if (s == null) {
            return this;
        }

        return append(stringToBytes(s, charset));
    }

    public FastStringBuffer appendEscaped(final String s) throws IOException {
        return appendEscaped(s, StandardCharsets.ISO_8859_1.name());
    }

    public FastStringBuffer appendEscaped(@Nullable final String s, final String charset) throws IOException {
        if (s == null) {
            return this;
        }

        return append(stringToBytes(HtmlEscaper.escapeHtml(s), charset));
    }

    /** Appends constant string literals only!!!!! */
    public FastStringBuffer appendCls(final String s) throws IOException {
        return appendCls(s, StandardCharsets.ISO_8859_1.name());
    }

    /** Appends constant string literals only!!!!! */
    public FastStringBuffer appendCls(@Nullable final String s, final String charset) throws IOException {
        if (s == null) {
            return this;
        }

        byte[] tmp = strings.get(s);
        if (tmp == null) {
            tmp = stringToBytes(s, charset);
            if (strings.size() < MAX_CACHE_SIZE) {
                strings.put(s, tmp);
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Dropping literal from cache:{}", s.replace('\n', '~'));
                }
            }
        }
        return append(tmp);
    }

    public FastStringBuffer appendCr() throws IOException {
        return append(CRBYTES);
    }

    public FastStringBuffer appendCrLf() throws IOException {
        return append(CRLFBYTES);
    }

    public byte[] getBytes() {
        return this.buffer;
    }

    public int getSize() {
        return this.curPos;
    }

    public void setLength(final int len) {
        this.curPos = len;
        this.myString = null;
    }

    @Override
    public void write(final int b) throws IOException {
        write(new byte[] {(byte) b});
    }

    @Override
    public void write(@Nullable final byte[] a) throws IOException {
        if (a != null) {
            write(a, 0, a.length);
        }
    }

    @Override
    public void write(final byte[] a, final int start, final int length) throws IOException {
        int lengthVal = length;
        try {
            if (lengthVal > (this.buffer.length - this.curPos)) {
                // if we have an output stream, this is an opportune time to write
                if (this.stream != null) {
                    if (this.curPos > 0) {
                        this.stream.write(this.buffer, 0, this.curPos);
                        this.bytesWritten += this.curPos;
                        this.curPos = 0;
                    }
                    // if the new data is larger than half our buffer, then write it too
                    if (lengthVal > this.buffer.length / 2) {
                        this.stream.write(a, start, lengthVal);
                        this.bytesWritten += lengthVal;
                        lengthVal = 0;
                    }
                } else {
                    // if not writing to stream, then increase the buffer appropriately
                    final int newSize = Math.max(this.buffer.length + this.buffer.length / 2, this.curPos + lengthVal + 1024);
                    final byte[] newArray = new byte[newSize];
                    System.arraycopy(this.buffer, 0, newArray, 0, this.curPos);// ??
                    this.buffer = newArray;
                }
            }

            if (lengthVal >= 0) {
                System.arraycopy(a, start, this.buffer, this.curPos, lengthVal);
                this.curPos += lengthVal;
            }
        } catch (Exception ex) {
            logger.warn("Exception in append", ex);
            logger.warn("a.length={}", a.length);// 1
            logger.warn("start={}", start);// 0
            logger.warn("length={}", lengthVal);// 1
            logger.warn("curPos={}", this.curPos);// 1
            logger.warn("buffer.length={}", this.buffer.length);// 1
            logger.warn("newSize={}", Math.max(this.buffer.length + this.buffer.length / 2, this.curPos + lengthVal + 1024));
        }
    }

    @Override
    public void flush() throws IOException {
        if (this.stream != null) {
            if (this.curPos > 0) {
                this.stream.write(this.buffer, 0, this.curPos);
                this.bytesWritten += this.curPos;
                this.curPos = 0;
            }
            this.stream.flush();
        }
    }

    public int getBytesWritten() {
        return this.bytesWritten;
    }

    @Override
    public void close() throws IOException {
        if (this.stream != null) {
            flush();
            this.stream.close();
        }
    }

    @Override
    public String toString() {
        if ((this.myString == null) || (this.myString.length() != this.buffer.length)) {
            this.myString = new String(this.buffer, 0, this.curPos);
        }
        return this.myString;
    }

    /**
     * Write UTF8 data to the output page buffer without specifying a start or end position, defaults to 0,-1
     */
    public FastStringBuffer appendUtf8(final byte[] data, final String charset) throws IOException {
        return appendUtf8(data, charset, 0, -1);
    }

    /**
     * Write UTF8 data to the output page buffer without specifying a start position, defaults to 0
     */
    public FastStringBuffer appendUtf8(final byte[] data, final String charset, final int end) throws IOException {
        return appendUtf8(data, charset, 0, end);
    }

    /**
     * Write UTF8 data to the output page buffer Pass in 0 and -1 for start and end to do the whole thing
     */
    @SuppressWarnings("InconsistentOverloads")
    public FastStringBuffer appendUtf8(final byte[] data, @Nullable final String charset, final int start, final int end) throws IOException {
        final int actualEnd;
        if (end < 0) {
            actualEnd = data.length;
        } else {
            actualEnd = end;
        }

        final int actualStart;
        if ((start < 0) || (start > actualEnd)) {
            actualStart = 0;
        } else {
            actualStart = start;
        }

        String converted = null;
        if (charset != null) {
            try {
                converted = new String(data, actualStart, actualEnd - actualStart, charset);
                logger.debug("Converted data from {} to utf-8", charset);
            } catch (UnsupportedEncodingException uee) {
                logger.warn("Unable to convert from {}", charset);
                converted = null; // make sure we write something below
            }
        } else {
            logger.debug("Not converting data because charset is null");
        }

        if (converted != null) {
            return append(converted, "UTF8");
        }
        return append(data, actualStart, actualEnd - actualStart);
    }

    public FastStringBuffer appendEscapedUtf8(final byte[] data, @Nullable final String charset, final int start, final int end) throws IOException {
        final int actualEnd;
        if (end < 0) {
            actualEnd = data.length;
        } else {
            actualEnd = end;
        }

        final int actualStart;
        if ((start < 0) || (start > actualEnd)) {
            actualStart = 0;
        } else {
            actualStart = start;
        }

        String converted;
        if (charset != null) {
            try {
                converted = new String(data, actualStart, actualEnd - actualStart, charset);
                logger.debug("Converted data from {} to utf-8", charset);
            } catch (UnsupportedEncodingException uee) {
                logger.warn("Unable to convert from {}", charset);
                converted = new String(data, actualStart, actualEnd - actualStart);
            }
        } else {
            converted = new String(data, actualStart, actualEnd - actualStart);
        }

        // Escape the HTML in the converted string
        converted = HtmlEscaper.escapeHtml(converted);
        return append(converted, "UTF8");
    }

    protected byte[] stringToBytes(String s, String charset) {
        byte[] tmp;
        try {
            tmp = s.getBytes(charset);
        } catch (UnsupportedEncodingException e) {
            logger.warn("Unsupported encoding:{}", e.getMessage());
            tmp = s.getBytes();
        }
        return tmp;
    }
}
