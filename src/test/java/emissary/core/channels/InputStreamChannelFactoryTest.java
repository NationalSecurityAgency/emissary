package emissary.core.channels;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InputStreamChannelFactoryTest {
    private static class TestInputStreamFactory implements InputStreamFactory {
        private final byte[] bytes;

        public TestInputStreamFactory(final byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public InputStream create() {
            return new ByteArrayInputStream(bytes);
        }
    }

    private static final byte[] testBytes = "0123456789".getBytes(StandardCharsets.US_ASCII);

    @Test
    void testExhaustively() throws IOException {
        ChannelTestHelper.checkByteArrayAgainstSbc(testBytes,
                InputStreamChannelFactory.create(testBytes.length, new TestInputStreamFactory(testBytes)));
    }

    @Test
    void testClose() throws IOException {
        try (SeekableByteChannel sbc = InputStreamChannelFactory.create(testBytes.length, new TestInputStreamFactory(testBytes)).create()) {
            sbc.close();
            sbc.close();
            Assertions.assertFalse(sbc.isOpen());
        }
    }

    @Test
    void testRead() throws IOException {
        try (SeekableByteChannel sbc = InputStreamChannelFactory.create(testBytes.length, new TestInputStreamFactory(testBytes)).create()) {
            final ByteBuffer byteBuffer = ByteBuffer.allocate(1);

            sbc.position(5);
            Assertions.assertEquals(1, IOUtils.read(sbc, byteBuffer));
            Assertions.assertEquals('5', byteBuffer.get(0));

            byteBuffer.position(0);
            sbc.position(0);
            Assertions.assertEquals(1, IOUtils.read(sbc, byteBuffer));
            Assertions.assertEquals('0', byteBuffer.get(0));
        }
    }

    @Test
    void testSize() throws IOException {
        // Normal path
        try (final SeekableByteChannel sbc = InputStreamChannelFactory.create(testBytes.length, new TestInputStreamFactory(testBytes)).create()) {
            ByteBuffer buff = ByteBuffer.allocate(32);
            assertEquals(testBytes.length, sbc.size());
            assertEquals(testBytes.length, sbc.read(buff));
            assertEquals(testBytes.length, buff.position());
        }
    }

    @Test
    void testCanWorkOutSize() throws IOException {
        // Make the factory work it out
        try (final SeekableByteChannel sbc = InputStreamChannelFactory.create(-1, new TestInputStreamFactory(testBytes)).create()) {
            ByteBuffer buff = ByteBuffer.allocate(32);
            assertEquals(testBytes.length, sbc.size());
            assertEquals(testBytes.length, sbc.read(buff));
            assertEquals(testBytes.length, buff.position());
        }
    }

    @Test
    void testReadWithIncorrectSize() throws IOException {
        // You can set the size incorrectly, but this is still 'valid'
        try (final SeekableByteChannel sbc = InputStreamChannelFactory.create(20, new TestInputStreamFactory(testBytes)).create()) {
            ByteBuffer buff = ByteBuffer.allocate(32);
            assertEquals(20, sbc.size());
            assertEquals(testBytes.length, sbc.read(buff));
            assertEquals(testBytes.length, buff.position());
        }
    }

    @Test
    void testReadWithZeroSize() throws IOException {
        // Zero length channel - will always be EOS, not reading anything into the buffer
        try (final SeekableByteChannel sbc = InputStreamChannelFactory.create(0, new TestInputStreamFactory(testBytes)).create()) {
            assertEquals(0, sbc.size());
            ByteBuffer buff = ByteBuffer.allocate(1);
            assertEquals(0, sbc.size());
            assertEquals(-1, sbc.read(buff));
            assertEquals(0, buff.position());
        }
    }
}
