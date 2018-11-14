/*
 * ReadOutputBuffer.java
 *
 * Created on November 19, 2001, 10:44 AM
 */

package emissary.util.shell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ce
 * @version 1.0
 */
public class ReadOutputBuffer extends ProcessReader {

    private static final Logger logger = LoggerFactory.getLogger(ReadOutputBuffer.class);

    private BufferedReader br;
    private StringBuffer buf = null;
    private StringBuilder bld = null;
    private long maxSize = -1;
    public boolean finished = false;

    public ReadOutputBuffer(final InputStream is, final long maxSize) {
        this.maxSize = maxSize;
        this.br = new BufferedReader(new InputStreamReader(is));
    }

    public ReadOutputBuffer(final InputStream is, final long maxSize, final String charset) {
        this.maxSize = maxSize;
        try {
            this.br = new BufferedReader(new InputStreamReader(is, charset));
        } catch (UnsupportedEncodingException e) {
            logger.error("Cannot read output using charset " + charset + ", reverting to JVM default");
            this.br = new BufferedReader(new InputStreamReader(is));
        }
    }

    public ReadOutputBuffer(final InputStream is, final StringBuffer buf) {
        this.buf = buf;
        this.maxSize = -1;
        this.br = new BufferedReader(new InputStreamReader(is));
    }

    public ReadOutputBuffer(final InputStream is, final StringBuilder bld) {
        this.bld = bld;
        this.maxSize = -1;
        this.br = new BufferedReader(new InputStreamReader(is));
    }

    public ReadOutputBuffer(final InputStream is, final StringBuffer buf, final String charset) {
        this.buf = buf;
        this.maxSize = -1;
        try {
            this.br = new BufferedReader(new InputStreamReader(is, charset));
        } catch (UnsupportedEncodingException e) {
            logger.error("Cannot read output using charset " + charset + ", reverting to JVM default");
            this.br = new BufferedReader(new InputStreamReader(is));
        }
    }

    public ReadOutputBuffer(final InputStream is, final StringBuilder bld, final String charset) {
        this.bld = bld;
        this.maxSize = -1;
        try {
            this.br = new BufferedReader(new InputStreamReader(is, charset));
        } catch (UnsupportedEncodingException e) {
            logger.error("Cannot read output using charset " + charset + ", reverting to JVM default");
            this.br = new BufferedReader(new InputStreamReader(is));
        }
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
    public void run() {
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
    public void finish() {
        this.finished = true;
        this.interrupt();
    }

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
