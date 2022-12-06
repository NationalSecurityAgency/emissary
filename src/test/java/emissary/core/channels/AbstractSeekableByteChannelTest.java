package emissary.core.channels;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

class AbstractSeekableByteChannelTest {
    private static class TestSeekableByteChannel extends AbstractSeekableByteChannel {
        private final long size;

        public TestSeekableByteChannel(final long size) {
            this.size = size;
        }

        @Override
        protected long sizeImpl() throws IOException {
            return size;
        }

        @Override
        protected void closeImpl() throws IOException {}

        @Override
        protected int readImpl(final ByteBuffer byteBuffer) throws IOException {
            final int remaining = byteBuffer.remaining();

            byteBuffer.position(byteBuffer.capacity());

            return remaining;
        }
    }

    @Test
    void testConstrutorValidSize() throws IOException {
        final long testSize = 1234567890;

        try (SeekableByteChannel sbc = new TestSeekableByteChannel(testSize)) {
            Assertions.assertEquals(testSize, sbc.size());
        }
    }

    @Test
    void testOpenClose() throws IOException {
        try (SeekableByteChannel sbc = new TestSeekableByteChannel(10)) {
            Assertions.assertTrue(sbc.isOpen());

            sbc.close();

            Assertions.assertFalse(sbc.isOpen());

            sbc.close();

            Assertions.assertFalse(sbc.isOpen());
            Assertions.assertThrows(ClosedChannelException.class, () -> sbc.size());
            Assertions.assertThrows(ClosedChannelException.class, () -> sbc.read(ByteBuffer.allocate(5)));
            Assertions.assertThrows(ClosedChannelException.class, () -> sbc.position());
            Assertions.assertThrows(ClosedChannelException.class, () -> sbc.position(5));
        }
    }

    @Test
    void testImmutable() throws IOException {
        try (SeekableByteChannel sbc = new TestSeekableByteChannel(10)) {
            Assertions.assertThrows(NonWritableChannelException.class, () -> sbc.truncate(5));
            Assertions.assertThrows(NonWritableChannelException.class, () -> sbc.write(ByteBuffer.allocate(5)));
        }
    }

    @Test
    void testPosition() throws IOException {
        try (SeekableByteChannel sbc = new TestSeekableByteChannel(10)) {
            Assertions.assertEquals(0, sbc.position());
            Assertions.assertThrows(IllegalArgumentException.class, () -> sbc.position(-1));
            Assertions.assertEquals(sbc, sbc.position(0));
            Assertions.assertEquals(0, sbc.position());
            Assertions.assertEquals(sbc, sbc.position(5));
            Assertions.assertEquals(5, sbc.position());
            Assertions.assertEquals(sbc, sbc.position(15));
            Assertions.assertEquals(15, sbc.position());
        }
    }

    @Test
    void testRead() throws IOException {
        try (SeekableByteChannel sbc = new TestSeekableByteChannel(10)) {
            Assertions.assertThrows(NullPointerException.class, () -> sbc.read(null));

            final ByteBuffer byteBuffer = ByteBuffer.allocate(4);

            Assertions.assertEquals(0, sbc.position());
            Assertions.assertEquals(4, sbc.read(byteBuffer));

            byteBuffer.position(0);
            sbc.position(15);

            Assertions.assertEquals(-1, sbc.read(byteBuffer));
        }
    }
}
