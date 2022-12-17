package emissary.core.channels;

import emissary.core.BaseDataObject;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;

/**
 * Helper methods to handle {@link java.nio.channels.SeekableByteChannel} objects
 */
public final class SeekableByteChannelHelper {
    private static final Logger logger = LoggerFactory.getLogger(SeekableByteChannelHelper.class);

    private SeekableByteChannelHelper() {}

    /**
     * Make an existing factory immutable.
     * 
     * @param sbcf to make immutable
     * @return the wrapped factory
     */
    public static SeekableByteChannelFactory immutable(final SeekableByteChannelFactory sbcf) {
        return ImmutableChannelFactory.create(sbcf);
    }

    /**
     * Create an in memory SBC factory which can be used to create any number of channels based on the provided bytes
     * without storing them multiple times.
     * 
     * @param bytes to use with the channel
     * @return the factory
     */
    public static SeekableByteChannelFactory memory(final byte[] bytes) {
        return InMemoryChannelFactory.create(bytes);
    }

    /**
     * Create a file SBC factory.
     * 
     * @param path to the file.
     * @return the factory
     */
    public static SeekableByteChannelFactory file(final Path path) {
        return FileChannelFactory.create(path);
    }

    /**
     * Create a fill SBC factory.
     * 
     * @param size of the SeekableByteChannel
     * @param value of each element in the SeekableByteChannel.
     * @return the factory
     */
    public static SeekableByteChannelFactory fill(final long size, final byte value) {
        return FillChannelFactory.create(size, value);
    }

    /**
     * Create an InputStream SBC factory.
     * 
     * @param size of the SeekableByteChannel
     * @param inputStreamFactory creates the needed InputStreams.
     * @return the factory
     */
    public static SeekableByteChannelFactory inputStream(final long size, final InputStreamFactory inputStreamFactory) {
        return InputStreamChannelFactory.create(size, inputStreamFactory);
    }

    /**
     * Given a BDO, create a byte array with as much data as possible.
     * 
     * @param bdo to get the data from
     * @param maxSize to limit the byte array to
     * @return a byte array of the data from the BDO sized up to maxSize (so could truncate data)
     */
    public static byte[] getByteArrayFromChannel(final BaseDataObject bdo, final int maxSize) {
        try (final SeekableByteChannel sbc = bdo.getChannelFactory().create()) {
            final long truncatedBy = sbc.size() - maxSize;
            if (truncatedBy > 0 && logger.isWarnEnabled()) {
                logger.warn("Returned data for [{}] will be truncated by {} bytes due to size constraints of byte arrays", bdo.shortName(),
                        truncatedBy);
            }
            final ByteBuffer buff = ByteBuffer.allocate((int) Math.min(sbc.size(), maxSize));
            IOUtils.readFully(sbc, buff);
            return buff.array();
        } catch (final IOException ioe) {
            logger.error("Error when fetching from byte channel factory on object {}", bdo.shortName(), ioe);
            bdo.setData(new byte[0]);
            return new byte[0];
        }
    }

    /**
     * Provided with an existing input stream, check how far we can read into it.
     * 
     * Note that the inputStream is read as-is, so if the stream is not at the start, this method won't take that into
     * account. If we can successfully read the stream, the position of the provided stream will of course change.
     * 
     * Don't wrap the provided stream with anything such as BufferedInputStream as this will cause read errors prematurely,
     * unless this is acceptable.
     * 
     * @param inputStream to read - caller must handle closing this object
     * @return position of last successful read (which could be the size of the stream)
     */
    public static long available(final InputStream inputStream) {
        long totalBytesRead = 0;
        try {
            for (; inputStream.read() != -1; totalBytesRead++) {
                // Do nothing.
            }
        } catch (final IOException ioe) {
            // Do nothing.
        }

        return totalBytesRead;
    }

    /**
     * Reads data from an input stream into a buffer
     * 
     * @param inputStream to read from
     * @param byteBuffer to read into
     * @param bytesToSkip within the {@code is} to get to the next read location
     * @param maxBytesToRead to limit the amount of data returned from the inputStream
     * @throws IOException if an error occurs
     */
    public static int getFromInputStream(final InputStream inputStream, final ByteBuffer byteBuffer, final long bytesToSkip,
            final int maxBytesToRead) throws IOException {
        Validate.notNull(inputStream, "Required: inputStream");
        Validate.notNull(byteBuffer, "Required: byteBuffer");
        Validate.isTrue(bytesToSkip > -1, "Required: bytesToSkip > -1");

        // Skip to position if we're not already there
        IOUtils.skipFully(inputStream, bytesToSkip);

        // Read direct into buffer's array if possible, otherwise copy through an internal buffer
        final int bytesToRead = Math.min(maxBytesToRead, byteBuffer.remaining());
        if (byteBuffer.hasArray()) {
            final int bytesRead = inputStream.read(byteBuffer.array(), byteBuffer.position(), bytesToRead);
            if (bytesRead > 0) {
                byteBuffer.position(byteBuffer.position() + bytesRead);
            }
            return bytesRead;
        } else {
            final byte[] internalBuff = new byte[bytesToRead];
            final int bytesRead = inputStream.read(internalBuff);
            if (bytesRead > 0) {
                byteBuffer.put(internalBuff, 0, bytesRead);
            }
            return bytesRead;
        }
    }
}
