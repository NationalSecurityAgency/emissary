package emissary.core.channels;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * This InputStream wraps another InputStream and logs all methods calls.
 */
public class LoggingInputStream extends InputStream {
    /**
     * The InputStream that is being wrapped.
     */
    private final InputStream inputStream;
    /**
     * The prefix added to the log statement.
     */
    private final String prefix;
    /**
     * The logger to be used for logging.
     */
    private final Logger logger;
    /**
     * Determines whether a stack trace should also be logged.
     */
    private final boolean logStackTrace;

    /**
     * Constructs a InputStream that wraps another InputStream and logs all methods calls.
     * 
     * @param inputStream to be wrapped.
     * @param prefix added to the log statement.
     * @param logger to be used for logging.
     * @param logStackTrace determines whether a stack trace should also be logged.
     */
    public LoggingInputStream(final InputStream inputStream, final String prefix, final Logger logger,
            final boolean logStackTrace) {
        this.inputStream = inputStream;
        this.prefix = prefix;
        this.logger = logger;
        this.logStackTrace = logStackTrace;

        log("{} : created : lST={}", prefix, logStackTrace);
    }

    @Override
    public int read() throws IOException {
        final int r = inputStream.read();

        log("{} : read-0 : r={}", prefix, r);

        return r;
    }

    @Override
    public int read(final byte[] b) throws IOException {
        final int r = inputStream.read(b);

        log("{} : read-1 : r={}", prefix, r);

        return r;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        final int r = inputStream.read(b, off, len);

        log("{} : read-3 : off={} len={} : r={}", prefix, off, len, r);

        return r;
    }

    @Override
    public long skip(final long n) throws IOException {
        final long r = inputStream.skip(n);

        log("{} : skip : n={} : r={}", prefix, n, r);

        return r;
    }

    @Override
    public int available() throws IOException {
        final int r = inputStream.available();

        log("{} : available : r={}", prefix, r);

        return r;
    }

    @Override
    public void close() throws IOException {
        log("{} : close", prefix);

        inputStream.close();
    }

    @Override
    public synchronized void mark(final int readlimit) {
        log("{} : mark : rl={}", prefix, readlimit);

        inputStream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        log("{} : reset", prefix);

        inputStream.reset();
    }

    @Override
    public boolean markSupported() {
        final boolean r = inputStream.markSupported();

        log("{} : markSupported : r={}", prefix, r);

        return r;
    }

    private void log(final String format, final Object... arguments) {
        if (logStackTrace) {
            final Object[] newArguments = Arrays.copyOf(arguments, arguments.length + 1);

            // "Throwable" is used since this class should only be used during development.
            newArguments[newArguments.length - 1] = new Throwable("DEBUG");

            logger.info(format, newArguments);
        } else {
            logger.info(format, arguments);
        }
    }
}
