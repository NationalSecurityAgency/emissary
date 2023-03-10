package emissary.core.channels;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class creates a InputStreamFactory for logging accesses to every method on the InputStream. It is intended only
 * for development/debugging purposes.
 */
public final class LoggingInputStreamFactory {
    private LoggingInputStreamFactory() {}

    /**
     * Creates an InputStreamFactory for logging accesses to every method on the InputStream.
     * 
     * @param inputStreamFactory that creates the InputStream to wrap.
     * @param identifier that is added to the log message.
     * @param logger to use for logging.
     * @param logStackTrace specifies whether a stack trace should be added to the log message.
     * @return the logging InputStreamFactory
     */
    public static InputStreamFactory create(final InputStreamFactory inputStreamFactory, final String identifier,
            final Logger logger, final boolean logStackTrace) {
        return new LoggingInputStreamFactoryImpl(inputStreamFactory, identifier, logger, logStackTrace);
    }

    /**
     * The InputStreamFactory for creating logging InputStreams.
     */
    private static class LoggingInputStreamFactoryImpl implements InputStreamFactory {
        /**
         * The InputStream that is being wrapped.
         */
        private final InputStreamFactory inputStreamFactory;
        /**
         * An identifier add to the log message.
         */
        private final String identifier;
        /**
         * The logger to be used for logging.
         */
        private final Logger logger;
        /**
         * Determines whether a stack trace should also be logged.
         */
        private final boolean logStackTrace;
        /**
         * A one-up counter for identifying each instance.
         */
        private final AtomicLong currentInstance = new AtomicLong(0);

        private LoggingInputStreamFactoryImpl(final InputStreamFactory inputStreamFactory, final String identifier,
                final Logger logger, final boolean logStackTrace) {
            Validate.notNull(inputStreamFactory, "Required: inputStreamFactory not null!");
            Validate.notNull(identifier, "Required: identifier not null!");
            Validate.notNull(logger, "Required: logger not null!");

            this.inputStreamFactory = inputStreamFactory;
            this.identifier = identifier;
            this.logger = logger;
            this.logStackTrace = logStackTrace;
        }

        @Override
        public InputStream create() throws IOException {
            return new LoggingInputStream(inputStreamFactory.create(),
                    identifier + " : " + currentInstance.getAndIncrement(), logger, logStackTrace);
        }
    }

    /**
     * This InputStream wraps another InputStream and logs all methods calls.
     */
    private static class LoggingInputStream extends InputStream {
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
        private LoggingInputStream(final InputStream inputStream, final String prefix, final Logger logger,
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
}
