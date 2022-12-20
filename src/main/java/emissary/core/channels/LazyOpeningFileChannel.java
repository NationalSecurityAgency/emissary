package emissary.core.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Set;

/**
 * {@link SeekableByteChannel} implementation that provides immutable access to a {@link FileChannel} instance. The
 * FileChannel instance is only created upon the first attempt to access the file content
 */
final class LazyOpeningFileChannel implements SeekableByteChannel {

    private static final Set<StandardOpenOption> OPTIONS = Collections.singleton(StandardOpenOption.READ);

    private final Path path;

    // leverage the defined immutability support
    private ImmutableChannel<FileChannel> channel;

    LazyOpeningFileChannel(final Path path) {
        this.path = path;
    }

    protected void initialiseChannel() throws IOException {
        if (channel == null) {
            channel = new ImmutableChannel<>(FileChannel.open(path, OPTIONS));
        }
    }

    @Override
    public void close() throws IOException {
        if (channel != null) {
            channel.close();
        }
    }

    @Override
    public int read(ByteBuffer byteBuffer) throws IOException {
        initialiseChannel();
        return channel.read(byteBuffer);
    }

    @Override
    public int write(ByteBuffer byteBuffer) throws IOException {
        initialiseChannel();
        // delegate immutability to the channel instance
        return channel.write(byteBuffer);
    }

    @Override
    public long position() throws IOException {
        initialiseChannel();
        return channel.position();
    }

    @Override
    public SeekableByteChannel position(long l) throws IOException {
        return channel.position(l);
    }

    @Override
    public long size() throws IOException {
        initialiseChannel();
        return channel.size();
    }

    @Override
    public SeekableByteChannel truncate(long l) throws IOException {
        initialiseChannel();
        // delegate immutability to the instance
        channel.truncate(l);
        return null;
    }

    @Override
    public boolean isOpen() {
        try {
            initialiseChannel();
        } catch (IOException e) {
            return false;
        }
        return channel.isOpen();
    }
}
