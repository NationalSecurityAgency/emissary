package emissary.core.channels;

import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

public class InputStreamChannelFactory {
    private InputStreamChannelFactory() {}

    /**
     * Creates a factory implementation based on an {@link InputStreamFactory}
     * 
     * @param size if known, else provide a negative value to allow the factory to work out the size upon first create
     * @param inputStreamFactory for the data
     * @return an InputStreamChannelFactory instance of the data
     */
    public static SeekableByteChannelFactory create(final long size, final InputStreamFactory inputStreamFactory) {
        return new InputStreamChannelFactoryImpl(size, inputStreamFactory);
    }

    private static class InputStreamChannelFactoryImpl implements SeekableByteChannelFactory {
        private long size;
        private final InputStreamFactory inputStreamFactory;

        public InputStreamChannelFactoryImpl(final long size, final InputStreamFactory inputStreamFactory) {
            Validate.notNull(inputStreamFactory, "Required: inputStream not null");

            this.size = size;
            this.inputStreamFactory = inputStreamFactory;
        }

        @Override
        public SeekableByteChannel create() {
            return new InputStreamChannel(size, inputStreamFactory);
        }
    }

    private static class InputStreamChannel extends AbstractSeekableByteChannel {
        /**
         * The InputStreamFactory used to get InputStream instances.
         */
        private final InputStreamFactory inputStreamFactory;

        /**
         * The current InputStream instance.
         */
        private CountingInputStream inputStream;

        private long size;

        /**
         * Create a new InputStreamChannel instance with a fixed size and data source
         * 
         * @param size of the InputStreamChannel
         * @param inputStreamFactory data source
         */
        public InputStreamChannel(final long size, final InputStreamFactory inputStreamFactory) {
            Validate.notNull(inputStreamFactory, "Required: inputStreamFactory not null!");
            this.size = size;
            this.inputStreamFactory = inputStreamFactory;
        }

        @Override
        protected final int readImpl(final ByteBuffer byteBuffer, final int maxBytesToRead) throws IOException {
            if (inputStream != null && position() < inputStream.getByteCount()) {
                inputStream.close();
                inputStream = null;
            }

            if (inputStream == null) {
                inputStream = new CountingInputStream(inputStreamFactory.create());
            }

            // Actually perform the read
            return SeekableByteChannelHelper.getFromInputStream(inputStream, byteBuffer, position() - inputStream.getByteCount(),
                    maxBytesToRead);
        }


        @Override
        protected long sizeImpl() throws IOException {
            if (size < 0) {
                try (final InputStream is = inputStreamFactory.create()) {
                    size = SeekableByteChannelHelper.available(is);
                }
            }
            return size;
        }

        @Override
        protected final void closeImpl() throws IOException {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
        }
    }
}
