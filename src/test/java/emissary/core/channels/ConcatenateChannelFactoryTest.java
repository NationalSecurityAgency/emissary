package emissary.core.channels;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConcatenateChannelFactoryTest extends UnitTest {
    @Test
    void testParameters() {
        final SeekableByteChannelFactory sbcf = InMemoryChannelFactory.create(new byte[10]);

        assertThrows(IllegalArgumentException.class, () -> ConcatenateChannelFactory.create(null, sbcf));
        assertThrows(IllegalArgumentException.class, () -> ConcatenateChannelFactory.create(sbcf, null));
    }

    @Test
    void testExceptionChannelFactory() {
        final SeekableByteChannelFactory sbcf = ConcatenateChannelFactory.create(new ExceptionChannelFactory(), new ExceptionChannelFactory());

        SeekableByteChannel sbc = sbcf.create();
        assertThrows(IOException.class, () -> sbc.size());
        assertThrows(IOException.class, () -> sbc.read(ByteBuffer.allocate(10)));
        assertThrows(IOException.class, () -> sbc.close());
    }

    @Test
    void testSize() throws IOException {
        final SeekableByteChannelFactory sbcf0 = FillChannelFactory.create(1L << 62, (byte) 0);
        final SeekableByteChannelFactory sbcf1 = FillChannelFactory.create((1L << 62) - 1, (byte) 0);

        try (SeekableByteChannel sbc = ConcatenateChannelFactory.create(sbcf0, sbcf1).create()) {
            assertEquals(Long.MAX_VALUE, sbc.size());
            assertEquals(Long.MAX_VALUE, sbc.size()); // Call a second time to ensure the same value is returned.
        }
        try (SeekableByteChannel sbc = ConcatenateChannelFactory.create(sbcf0, sbcf0).create()) {
            assertThrows(IOException.class, () -> sbc.size());
        }
    }

    @Test
    void testClose() throws IOException {
        final CheckCloseChannelFactory firstSbcf = new CheckCloseChannelFactory();
        final CheckCloseChannelFactory secondSbcf = new CheckCloseChannelFactory();
        final SeekableByteChannel sbc = ConcatenateChannelFactory.create(firstSbcf, secondSbcf).create();

        sbc.close();

        assertEquals(List.of(true), firstSbcf.isClosedList);
        assertEquals(List.of(true), secondSbcf.isClosedList);
    }

    @Test
    void testRead() throws IOException {
        final byte[] expectedBytes = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        final SeekableByteChannelFactory firstSbcf = InMemoryChannelFactory.create(new byte[] {0, 1, 2, 3, 4, 5});
        final SeekableByteChannelFactory secondSbcf = InMemoryChannelFactory.create(new byte[] {6, 7, 8, 9, 10});
        final SeekableByteChannelFactory sbcf = ConcatenateChannelFactory.create(firstSbcf, secondSbcf);

        ChannelTestHelper.checkByteArrayAgainstSbc(expectedBytes, sbcf);
    }
}
