package emissary.core.channels;

import emissary.test.core.junit5.UnitTest;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;

import static emissary.core.channels.InputStreamChannelFactory.SIZE_IS_UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputStreamChannelFactoryTest extends UnitTest {
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

    private static final byte[] testBytes = "0123456789".getBytes(StandardCharsets.UTF_8);
    private static final byte[] emojiBytes = "😃0123456789".getBytes(StandardCharsets.UTF_16);


    @Test
    void testReadNonUtf8() throws IOException {
        try (SeekableByteChannel sbc = InputStreamChannelFactory.create(emojiBytes.length, new TestInputStreamFactory(emojiBytes)).create()) {
            final ByteBuffer byteBuffer = ByteBuffer.allocate(8);

            sbc.position(8);
            assertEquals(8, IOUtils.read(sbc, byteBuffer));
            assertEquals("1234", new String(byteBuffer.array(), StandardCharsets.UTF_16));

            byteBuffer.position(0);
            sbc.position(0);
            assertEquals(8, IOUtils.read(sbc, byteBuffer));
            assertEquals("😃0", new String(byteBuffer.array(), StandardCharsets.UTF_16));
        }
    }

    @Test
    void testExhaustively() throws IOException {
        ChannelTestHelper.checkByteArrayAgainstSbc(testBytes,
                InputStreamChannelFactory.create(testBytes.length, new TestInputStreamFactory(testBytes)));
    }

    @Test
    void testClose() throws IOException {
        SeekableByteChannel sbc = InputStreamChannelFactory.create(testBytes.length, new TestInputStreamFactory(testBytes)).create();
        // Call close twice - should not do or throw anything beyond the first time.
        sbc.close();
        assertFalse(sbc.isOpen());

        assertDoesNotThrow(() -> sbc.close());
        assertFalse(sbc.isOpen());
    }

    @Test
    void testRead() throws IOException {
        try (SeekableByteChannel sbc = InputStreamChannelFactory.create(testBytes.length, new TestInputStreamFactory(testBytes)).create()) {
            final ByteBuffer byteBuffer = ByteBuffer.allocate(1);

            // Read an arbitrary position
            sbc.position(5);
            assertEquals(1, IOUtils.read(sbc, byteBuffer));
            assertEquals('5', byteBuffer.get(0));

            byteBuffer.position(0);
            sbc.position(0);
            assertEquals(1, IOUtils.read(sbc, byteBuffer));
            assertEquals('0', byteBuffer.get(0));
        }
    }

    @Test
    void testSize() throws IOException {
        // Normal path
        try (SeekableByteChannel sbc = InputStreamChannelFactory.create(testBytes.length, new TestInputStreamFactory(testBytes)).create()) {
            ByteBuffer buff = ByteBuffer.allocate(32);
            assertEquals(testBytes.length, sbc.size());
            assertEquals(testBytes.length, sbc.read(buff));
            assertEquals(testBytes.length, buff.position());
        }
    }

    @Test
    void testCanWorkOutSize() throws IOException {
        // Make the factory work it out
        try (SeekableByteChannel sbc = InputStreamChannelFactory.create(-1, new TestInputStreamFactory(testBytes)).create()) {
            ByteBuffer buff = ByteBuffer.allocate(32);
            assertEquals(testBytes.length, sbc.size());
            assertEquals(testBytes.length, sbc.read(buff));
            assertEquals(testBytes.length, buff.position());
        }
    }

    @Test
    void testReadWithIncorrectSize() throws IOException {
        // You can set the size incorrectly, but this is still 'valid'
        final int sbcLength = testBytes.length + 8;
        try (SeekableByteChannel sbc = InputStreamChannelFactory.create(sbcLength, new TestInputStreamFactory(testBytes)).create()) {
            ByteBuffer buff = ByteBuffer.allocate(32);
            assertEquals(sbcLength, sbc.size());
            assertEquals(testBytes.length, sbc.read(buff));
            assertEquals(testBytes.length, buff.position());
        }
    }

    @Test
    void testReadWithZeroSize() throws IOException {
        // Zero length channel - will always be EOS, not reading anything into the buffer
        try (SeekableByteChannel sbc = InputStreamChannelFactory.create(0, new TestInputStreamFactory(testBytes)).create()) {
            ByteBuffer buff = ByteBuffer.allocate(1);
            assertEquals(0, sbc.size());
            assertEquals(-1, sbc.read(buff));
            assertEquals(0, buff.position());
        }
    }

    @Test
    void testStartReadBeyondActualData() throws IOException {
        // Set an SBC that is larger than the data we have, ensure an IOException occurs when reading
        final int sbcLength = testBytes.length + 8;
        try (SeekableByteChannel sbc = InputStreamChannelFactory.create(sbcLength, new TestInputStreamFactory(testBytes)).create()) {
            ByteBuffer buff = ByteBuffer.allocate(32);
            assertEquals(sbcLength, sbc.size());
            // Set position beyond length
            sbc.position(testBytes.length + 2);
            assertThrows(IOException.class, () -> sbc.read(buff));
            assertEquals(0, buff.position());
        }
    }

    @Test
    void testReadWithLargerThanDefinedSize() throws IOException {
        final int sbcLength = testBytes.length / 2;
        // Set an SBC that is smaller than the amount of data we have, ensure that we can't read more than the defined size
        try (SeekableByteChannel sbc = InputStreamChannelFactory.create(sbcLength, new TestInputStreamFactory(testBytes)).create()) {
            ByteBuffer buff = ByteBuffer.allocate(32);
            assertEquals(sbcLength, sbc.size());
            sbc.read(buff);
            assertEquals(sbcLength, buff.position());
        }
    }

    @Test
    void testExceptionInputStreamFactory() throws Exception {
        final InputStreamFactory isf = new InputStreamFactory() {
            @Override
            public InputStream create() throws IOException {
                throw new IOException("This InputStreamFactory only throws IOExceptions!");
            }
        };
        final SeekableByteChannelFactory sbcf = InputStreamChannelFactory.create(SIZE_IS_UNKNOWN, isf);
        final ByteBuffer byteBuffer = ByteBuffer.allocate(100);

        try (SeekableByteChannel sbc = sbcf.create()) {
            assertThrows(IOException.class, sbc::size);
            assertThrows(IOException.class, () -> sbc.read(byteBuffer));
        }
    }
}
