package emissary.core.channels;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class creates a SeekableByteChannelFactory for logging accesses to every method on the SeekableByteChannel. It
 * is intended only for development/debugging purposes.
 */
public final class LoggingChannelFactory {
    private LoggingChannelFactory() {}

    /**
     * Creates a SeekableByteChannelFactory for logging accesses to every method on the SeekableByteChannel.
     * 
     * @param seekableByteChannelFactory that creates the SeekableByteChannel to wrap.
     * @param identifier that is added to the log message.
     * @param logger to use for logging.
     * @param logStackTrace specifies whether a stack trace should be added to the log message.
     * @return the logging SeekableByteChannelFactory
     */
    public static SeekableByteChannelFactory create(final SeekableByteChannelFactory seekableByteChannelFactory,
            final String identifier, final Logger logger, final boolean logStackTrace) {
        return new LoggingChannelFactoryImpl(seekableByteChannelFactory, identifier, logger, logStackTrace);
    }

    /**
     * The SeekableByteChannelFactory for creating logging SeekableByteChannels.
     */
    private static class LoggingChannelFactoryImpl implements SeekableByteChannelFactory {
        /**
         * The SeekableByteChannel that is being wrapped.
         */
        private final SeekableByteChannelFactory seekableByteChannelFactory;
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

        private LoggingChannelFactoryImpl(final SeekableByteChannelFactory seekableByteChannelFactory,
                final String identifier, final Logger logger, final boolean logStackTrace) {
            Validate.notNull(seekableByteChannelFactory, "Required: seekableByteChannelFactory not null!");
            Validate.notNull(identifier, "Required: identifier not null!");
            Validate.notNull(logger, "Required: logger not null!");

            this.seekableByteChannelFactory = seekableByteChannelFactory;
            this.identifier = identifier;
            this.logger = logger;
            this.logStackTrace = logStackTrace;
        }

        @Override
        public SeekableByteChannel create() {
            return new LoggingSeekableByteChannel(seekableByteChannelFactory.create(),
                    identifier + " : " + currentInstance.getAndIncrement(), logger, logStackTrace);
        }
    }

    /**
     * This SeekableByteChannel wraps another SeekableByteChannel and logs all methods calls.
     */
    private static class LoggingSeekableByteChannel implements SeekableByteChannel {
        /**
         * The SeekableByteChannel that is being wrapped.
         */
        private final SeekableByteChannel seekableByteChannel;
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
         * Constructs a SeekableByteChannel that wraps another SeekableByteChannel and logs all methods calls.
         * 
         * @param seekableByteChannel to be wrapped.
         * @param prefix added to the log statement.
         * @param logger to be used for logging.
         * @param logStackTrace determines whether a stack trace should also be logged.
         */
        private LoggingSeekableByteChannel(final SeekableByteChannel seekableByteChannel, final String prefix,
                final Logger logger, final boolean logStackTrace) {
            this.seekableByteChannel = seekableByteChannel;
            this.prefix = prefix;
            this.logger = logger;
            this.logStackTrace = logStackTrace;

            log(logger, logStackTrace, "{} : created : lST={}", prefix, logStackTrace);
        }

        @Override
        public boolean isOpen() {
            final boolean open = seekableByteChannel.isOpen();

            log(logger, logStackTrace, "{} : isOpen : o={}", prefix, open);

            return open;
        }

        @Override
        public void close() throws IOException {
            log(logger, logStackTrace, "{} : close", prefix);

            seekableByteChannel.close();
        }

        @Override
        public int read(final ByteBuffer byteBuffer) throws IOException {
            final int bbP = byteBuffer.position();
            final int bbC = byteBuffer.capacity();
            final long sbcP = seekableByteChannel.position();

            final int bytesRead = seekableByteChannel.read(byteBuffer);

            log(logger, logStackTrace, "{} : read : bbP={} bbC={} sbcP={} : r={}", prefix, bbP, bbC, sbcP, bytesRead);

            return bytesRead;
        }

        @Override
        public int write(final ByteBuffer byteBuffer) throws IOException {
            final int bbP = byteBuffer.position();
            final int bbC = byteBuffer.capacity();
            final long sbcP = seekableByteChannel.position();

            final int bytesWritten = seekableByteChannel.write(byteBuffer);

            log(logger, logStackTrace, "{} : write : bbP={} bbC={} sbcP={} : bW={}", prefix, bbP, bbC, sbcP,
                    bytesWritten);

            return bytesWritten;
        }

        @Override
        public long position() throws IOException {
            final long position = seekableByteChannel.position();

            log(logger, logStackTrace, "{} : position : p={}", prefix, position);

            return position;
        }

        @Override
        public SeekableByteChannel position(final long newPosition) throws IOException {
            log(logger, logStackTrace, "{} : position : nP={}", prefix, newPosition);

            return seekableByteChannel.position(newPosition);
        }

        @Override
        public long size() throws IOException {
            final long size = seekableByteChannel.size();

            log(logger, logStackTrace, "{} : size : s={}", prefix, size);

            return size;
        }

        @Override
        public SeekableByteChannel truncate(final long size) throws IOException {
            log(logger, logStackTrace, "{} : truncate : s={}", prefix, size);

            return seekableByteChannel.truncate(size);
        }

        private static void log(final Logger logger, final boolean logStackTrace, final String format,
                final Object... arguments) {
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
