package emissary.core.channels;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            byteBuffer.position(byteBuffer.limit());

            return remaining;
        }
    }

    @Test
    void testConstrutorValidSize() throws IOException {
        final long testSize = 1234567890;

        try (SeekableByteChannel sbc = new TestSeekableByteChannel(testSize)) {
            assertEquals(testSize, sbc.size());
        }
    }

    @Test
    void testOpenClose() throws IOException {
        try (SeekableByteChannel sbc = new TestSeekableByteChannel(10)) {
            assertTrue(sbc.isOpen());

            sbc.close();

            assertFalse(sbc.isOpen());

            sbc.close();

            assertFalse(sbc.isOpen());
            assertThrows(ClosedChannelException.class, () -> sbc.size());
            assertThrows(ClosedChannelException.class, () -> sbc.read(ByteBuffer.allocate(5)));
            assertThrows(ClosedChannelException.class, () -> sbc.position());
            assertThrows(ClosedChannelException.class, () -> sbc.position(5));
        }
    }

    @Test
    void testImmutable() throws IOException {
        try (SeekableByteChannel sbc = new TestSeekableByteChannel(10)) {
            assertThrows(NonWritableChannelException.class, () -> sbc.truncate(5));
            ByteBuffer bb = ByteBuffer.allocate(5);
            assertThrows(NonWritableChannelException.class, () -> sbc.write(bb));
        }
    }

    @Test
    void testPosition() throws IOException {
        try (SeekableByteChannel sbc = new TestSeekableByteChannel(10)) {
            assertEquals(0, sbc.position());
            assertThrows(IllegalArgumentException.class, () -> sbc.position(-1));
            assertEquals(sbc, sbc.position(0));
            assertEquals(0, sbc.position());
            assertEquals(sbc, sbc.position(5));
            assertEquals(5, sbc.position());
            assertEquals(sbc, sbc.position(15));
            assertEquals(15, sbc.position());
        }
    }

    @Test
    void testRead() throws IOException {
        try (SeekableByteChannel sbc = new TestSeekableByteChannel(10)) {
            assertThrows(NullPointerException.class, () -> sbc.read(null));

            final ByteBuffer byteBuffer = ByteBuffer.allocate(4);

            assertEquals(0, sbc.position());
            assertEquals(4, sbc.read(byteBuffer));

            byteBuffer.position(0);
            sbc.position(15);

            assertEquals(-1, sbc.read(byteBuffer));
        }
    }

    @Test
    void testReadBeyondSize() throws IOException {
        try (SeekableByteChannel sbc = new TestSeekableByteChannel(4)) {
            final ByteBuffer buff = ByteBuffer.allocate(8);
            sbc.position(2);
            sbc.read(buff);
            assertEquals(4, sbc.position());
        }
    }

    @Test
    void testMaxBytesReadIsAccurate() throws IOException {
        try (SeekableByteChannel sbc = new TestSeekableByteChannel(4)) {
            final ByteBuffer buff = ByteBuffer.allocate(8);
            sbc.position(sbc.size() - 2);
            assertEquals(2, sbc.read(buff));
            assertEquals(4, sbc.position());
        }
    }
}
