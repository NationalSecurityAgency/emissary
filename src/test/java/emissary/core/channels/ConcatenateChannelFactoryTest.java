package emissary.core.channels;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
            assertEquals(Long.MAX_VALUE, sbc.size());
        }
        try (SeekableByteChannel sbc = ConcatenateChannelFactory.create(sbcf0, sbcf0).create()) {
            assertThrows(IOException.class, () -> sbc.size());
        }
    }

    @Test
    void testClose() throws IOException {
        final CheckCloseChannelFactory leftSbcf = new CheckCloseChannelFactory();
        final CheckCloseChannelFactory rightSbcf = new CheckCloseChannelFactory();
        final SeekableByteChannel sbc = ConcatenateChannelFactory.create(leftSbcf, rightSbcf).create();

        sbc.close();

        assertEquals(List.of(true), leftSbcf.isClosedList);
        assertEquals(List.of(true), rightSbcf.isClosedList);
    }

    @Test
    void testRead() throws IOException {
        final byte[] expectedBytes = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        final SeekableByteChannelFactory leftSbcf = InMemoryChannelFactory.create(new byte[] {0, 1, 2, 3, 4, 5});
        final SeekableByteChannelFactory rightSbcf = InMemoryChannelFactory.create(new byte[] {6, 7, 8, 9, 10});
        final SeekableByteChannelFactory sbcf = ConcatenateChannelFactory.create(leftSbcf, rightSbcf);

        ChannelTestHelper.checkByteArrayAgainstSbc(expectedBytes, sbcf);
    }

    private static class ExceptionChannelFactory implements SeekableByteChannelFactory {
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

    private static class CheckCloseChannelFactory implements SeekableByteChannelFactory {
        private final AtomicInteger instanceNumber = new AtomicInteger(0);

        public final List<Boolean> isClosedList = Collections.synchronizedList(new ArrayList<>());

        @Override
        public SeekableByteChannel create() {
            LoggerFactory.getLogger(SegmentChannelFactoryTest.class).info("CHECKCLOSE", new Throwable("CHECKCLOSE"));
            isClosedList.add(false);

            return new AbstractSeekableByteChannel() {
                final int myInstanceNumber = instanceNumber.getAndIncrement();

                @Override
                protected void closeImpl() {
                    isClosedList.set(myInstanceNumber, true);
                }

                @Override
                protected int readImpl(ByteBuffer byteBuffer) {
                    return 0;
                }

                @Override
                protected long sizeImpl() {
                    return 1;
                }
            };
        }
    }
}
