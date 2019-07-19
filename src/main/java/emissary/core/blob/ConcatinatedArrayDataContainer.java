package emissary.core.blob;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.IOUtils;

/**
 * Data container that stores all data on the heap, but is capable of growing beyond 2G.
 *
 */
public class ConcatinatedArrayDataContainer implements IDataContainer {

    /**
     * Serializable class
     */
    private static final long serialVersionUID = -1708661652088715359L;

    /** When no data is present. */
    private static byte[] NO_DATA = new byte[0];
    /** The maximum size of an individual array, may become configurable in future versions. */
    private static int CHUNK_SIZE = 1024 * 1024 * 16;

    /** The backing for the data. */
    private List<byte[]> data = new ArrayList<>();
    /** The total size of the data being stored. */
    long size = 0;

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] data() {
        if (data.isEmpty()) {
            return NO_DATA;
        }

        int totalLength = (int) Long.min(length(), Integer.MAX_VALUE);
        Iterator<byte[]> iter = data.iterator();
        byte[] first = iter.next();
        byte[] result = Arrays.copyOf(first, totalLength);
        int offset = first.length;
        while (iter.hasNext()) {
            byte[] array = iter.next();
            if (offset + array.length > result.length) {
                System.arraycopy(array, 0, result, offset, (int) (size % CHUNK_SIZE));

            } else {
                System.arraycopy(array, 0, result, offset, array.length);
            }
            offset += array.length;
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setData(byte[] newData) {
        if (newData == null) {
            data.clear();
            size = 0;
            return;
        }
        try (SeekableByteChannel chan = newChannel(newData.length)) {
            chan.write(ByteBuffer.wrap(newData));
        } catch (IOException e) {
            throw new DataException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setData(byte[] newData, int offset, int length) {
        if (newData == null) {
            data.clear();
            size = 0;
            return;
        }
        setData(Arrays.copyOfRange(newData, offset, offset + length));
    }

    @Override
    public ByteBuffer dataBuffer() {
        return ByteBuffer.wrap(data());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConcatinatedArrayDataContainer clone() throws CloneNotSupportedException {
        ConcatinatedArrayDataContainer clone = new ConcatinatedArrayDataContainer();
        try (InputStream in = Channels.newInputStream(channel());
                OutputStream out = Channels.newOutputStream(clone.newChannel(length()))) {
            IOUtils.copyLarge(in, out);
        } catch (IOException e) {
            throw new DataException(e);
        }
        return clone;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SeekableByteChannel channel() throws IOException {
        return new SeekableByteChannel() {
            private boolean open;
            private long position = 0;

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isOpen() {
                return open;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void close() throws IOException {
                open = false;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int write(ByteBuffer src) throws IOException {
                if (position > size()) {
                    long paddingToWrite = position - size();
                    // paddingToWrite --;
                    position = size();
                    while (paddingToWrite > 0) {
                        paddingToWrite -= write(ByteBuffer.allocate((int) Long.min(1024, paddingToWrite)));
                    }
                }
                int written = 0;
                while (src.remaining() > 0) {
                    int chunkIndex = (int) (position / CHUNK_SIZE);
                    int positionInArray = (int) (position % CHUNK_SIZE);
                    // int srcPos = src.position();
                    int bytesToWrite = Integer.min(CHUNK_SIZE - positionInArray, src.remaining());
                    byte[] dest = null;
                    if (chunkIndex < data.size()) {
                        dest = data.get(chunkIndex);
                    }
                    if (dest == null || dest.length < positionInArray + bytesToWrite) {
                        byte[] repl = new byte[positionInArray + bytesToWrite];
                        if (dest != null) {
                            System.arraycopy(dest, 0, repl, 0, positionInArray);
                        }
                        dest = repl;
                        if (chunkIndex < data.size()) {
                            data.set(chunkIndex, dest);
                        } else {
                            data.add(dest);
                        }
                    }
                    src.get(dest, positionInArray, bytesToWrite);
                    size = Long.max(size, position + bytesToWrite);
                    position += bytesToWrite;
                    written += bytesToWrite;
                }
                return written;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public SeekableByteChannel truncate(long size) throws IOException {
                int chunks = (int) (size / CHUNK_SIZE) + 1;
                if (chunks > data.size()) {
                    return this;
                }
                data = data.subList(0, chunks);
                int positionInArray = (int) (size % CHUNK_SIZE);
                if (positionInArray > 0) {
                    byte[] array = data.get(chunks - 1);
                    Arrays.fill(array, positionInArray, Integer.min(array.length, CHUNK_SIZE), (byte) 0);
                }
                ConcatinatedArrayDataContainer.this.size = size;
                position = Long.min(size, position);
                return this;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public long size() throws IOException {
                return length();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int read(ByteBuffer dst) throws IOException {
                int chunkIndex = (int) (position / CHUNK_SIZE);
                int positionInArray = (int) (position % CHUNK_SIZE);
                // int srcPos = src.position();
                int bytesToRead = Integer.min(CHUNK_SIZE - positionInArray, dst.capacity());
                if (position + bytesToRead > size) {
                    bytesToRead = (int) (size - position);
                }
                if (chunkIndex >= data.size() || bytesToRead <= 0) {
                    return -1;
                }
                byte[] src = data.get(chunkIndex);

                dst.put(src, positionInArray, bytesToRead);

                position += bytesToRead;
                return bytesToRead;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public SeekableByteChannel position(long newPosition) throws IOException {
                this.position = newPosition;
                return this;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public long position() throws IOException {
                return position;
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SeekableByteChannel newChannel(long estimatedSize) throws IOException {
        data.clear();
        size = 0;
        return channel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long length() {
        return size;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IFileProvider getFileProvider() {
        return IFileProvider.tempFileProvider(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supportsDirectMutationViaBuffer() {
        return false;
    }
}
