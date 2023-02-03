package emissary.core.channels;

import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Set;

/**
 * Provide a file-backed implementation for streaming data to a consumer
 */
public final class FileChannelFactory {

    private FileChannelFactory() {}

    /**
     * <p>
     * Create a new instance of the factory using the provided file reference. Ultimately, wraps a standard FileChannel with
     * immutability
     * </p>
     * 
     * @param path containing a reference to the file
     * @return a new instance
     * @see SeekableByteChannelHelper#file(Path)
     */
    public static SeekableByteChannelFactory create(final Path path) {
        return ImmutableChannelFactory.create(new FileChannelFactoryImpl(path));
    }

    /**
     * Private class to hide implementation details from callers
     */
    private static final class FileChannelFactoryImpl implements SeekableByteChannelFactory {
        private final Path path;

        private FileChannelFactoryImpl(final Path path) {
            Validate.notNull(path, "Required: path not null");
            this.path = path;
        }

        /**
         * Creates a {@link FileChannel} instance with the configured options to the configured file.
         * 
         * @return the new channel instance
         */
        @Override
        public SeekableByteChannel create() {
            return new LazyFileChannelImpl(path);
        }
    }

    private static final class LazyFileChannelImpl extends AbstractSeekableByteChannel {

        private static final Set<StandardOpenOption> OPTIONS = Collections.singleton(StandardOpenOption.READ);

        private final Path path;

        private FileChannel channel;

        private LazyFileChannelImpl(final Path path) {
            this.path = path;
        }

        protected void initialiseChannel() throws IOException {
            if (channel == null) {
                channel = FileChannel.open(path, OPTIONS);
            }
        }

        @Override
        protected void closeImpl() throws IOException {
            if (channel != null) {
                channel.close();
            }
        }

        @Override
        protected int readImpl(ByteBuffer byteBuffer, int maxBytesToRead) throws IOException {
            initialiseChannel();
            // Set position for underlying channel, otherwise this won't match the outer channel position
            channel.position(position());
            return channel.read(byteBuffer);
        }

        @Override
        protected long sizeImpl() throws IOException {
            initialiseChannel();
            return channel.size();
        }

    }
}
