package emissary.core.channels;

import emissary.util.io.LoggingInputStream;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
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
}
