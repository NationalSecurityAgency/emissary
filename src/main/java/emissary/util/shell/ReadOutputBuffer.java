package emissary.util.shell;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import javax.annotation.Nullable;

public class ReadOutputBuffer extends ProcessReader {

    private static final Logger logger = LoggerFactory.getLogger(ReadOutputBuffer.class);

    private BufferedReader br;
    @Nullable
    private StringBuffer buf = null;
    @Nullable
    private StringBuilder bld = null;
    private long maxSize = -1;
    public boolean finished = false;

    public ReadOutputBuffer(final InputStream is, final long maxSize) {
        this(is, maxSize, null);
    }

    public ReadOutputBuffer(final InputStream is, final long maxSize, @Nullable final String charset) {
        this.maxSize = maxSize;
        try {
            this.br = new BufferedReader(StringUtils.isBlank(charset) ? new InputStreamReader(is) : new InputStreamReader(is, charset));
        } catch (UnsupportedEncodingException e) {
            logger.error("Cannot read output using charset {}, reverting to JVM default", charset);
            this.br = new BufferedReader(new InputStreamReader(is));
        }
    }

    public ReadOutputBuffer(final InputStream is, @Nullable final StringBuffer buf) {
        this(is, buf, null);
    }

    public ReadOutputBuffer(final InputStream is, @Nullable final StringBuffer buf, @Nullable final String charset) {
        this(is, -1, charset);
        this.buf = buf;
    }

    public ReadOutputBuffer(final InputStream is, @Nullable final StringBuilder bld) {
        this(is, bld, null);
    }

    public ReadOutputBuffer(final InputStream is, @Nullable final StringBuilder bld, @Nullable final String charset) {
        this(is, -1, charset);
        this.bld = bld;
    }

    private int getOutputLength() {
        if (this.buf != null) {
            return this.buf.length();
        }
        if (this.bld != null) {
            return this.bld.length();
        }
        return 0;
    }

    private void append(final String s) {
        if (this.buf != null) {
            this.buf.append(s);
        }
        if (this.bld != null) {
            this.bld.append(s);
        }
    }

    @Override
    void runImpl() {
        String aLine = "";
        this.finished = false;
        try {
            while ((this.br != null) && ((aLine = this.br.readLine()) != null) && !this.finished) {
                if ((this.maxSize == -1) || ((getOutputLength() + aLine.length()) < this.maxSize)) {
                    append(aLine);
                    append("\r\n");
                }
            }
        } catch (Exception ex) {
            //
        } finally {
            if (this.br != null) {
                try {
                    this.br.close();
                } catch (IOException ioxjunk) {
                    // empty catch block
                }
            }
        }
    }

    @Override
    @SuppressWarnings("Interruption")
    public void finish() {
        this.finished = true;
        this.interrupt();
    }

    @Nullable
    public String getString() {
        if (this.buf != null) {
            return this.buf.toString();
        }
        if (this.bld != null) {
            return this.bld.toString();
        }
        return null;
    }

    public StringBuffer getBuffer() {
        return this.buf;
    }

    public StringBuilder getBuilder() {
        return this.bld;
    }
}
