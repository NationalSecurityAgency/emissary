package emissary.core.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

public class ExceptionChannelFactory implements SeekableByteChannelFactory {
    @Override
    public SeekableByteChannel create() {
        return new AbstractSeekableByteChannel() {
            @Override
            protected void closeImpl() throws IOException {
                throw new IOException("Test SBC that only throws IOExceptions!");
            }

            @Override
            protected int readImpl(final ByteBuffer byteBuffer) throws IOException {
                throw new IOException("Test SBC that only throws IOExceptions!");
            }

            @Override
            protected long sizeImpl() throws IOException {
                throw new IOException("Test SBC that only throws IOExceptions!");
            }
        };
    }
}
