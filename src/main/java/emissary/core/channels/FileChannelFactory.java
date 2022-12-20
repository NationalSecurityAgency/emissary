package emissary.core.channels;

import org.apache.commons.lang3.Validate;

import java.nio.channels.FileChannel;
import java.nio.file.Path;

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
    public static ImmutableChannelFactory<?> createFactory(final Path path) {
        return ImmutableChannelFactory.createFactory(new FileChannelFactoryImpl(path));
    }

    /**
     * Private class to hide implementation details from callers
     */
    private static final class FileChannelFactoryImpl implements SeekableByteChannelFactory<LazyOpeningFileChannel> {
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
        public LazyOpeningFileChannel create() {
            return new LazyOpeningFileChannel(path);
        }
    }

}
