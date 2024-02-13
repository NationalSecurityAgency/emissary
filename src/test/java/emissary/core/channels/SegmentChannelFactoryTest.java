package emissary.core.channels;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SegmentChannelFactoryTest extends UnitTest {
    @Test
    void testCreate() throws IOException {
        final byte[] bytes = new byte[10];

        new Random(0).nextBytes(bytes);

        final SeekableByteChannelFactory sbcf = InMemoryChannelFactory.create(bytes);
        final ExceptionChannelFactory ecf = new ExceptionChannelFactory();

        assertThrows(IllegalArgumentException.class, () -> SegmentChannelFactory.create(null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> SegmentChannelFactory.create(sbcf, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> SegmentChannelFactory.create(sbcf, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> SegmentChannelFactory.create(sbcf, bytes.length + 1, 0));
        assertThrows(IllegalArgumentException.class, () -> SegmentChannelFactory.create(sbcf, 0, bytes.length + 1));
        assertThrows(IllegalArgumentException.class, () -> SegmentChannelFactory.create(ecf, 0, 0));

        final CheckCloseChannelFactory cccf = new CheckCloseChannelFactory();

        try (SeekableByteChannel sbc = SegmentChannelFactory.create(cccf, 0, 0).create()) {
            assert sbc != null;
        }

        assertEquals(List.of(true, true), cccf.isClosedList);

        // Test all start and length combinations.
        for (int start = 0; start <= bytes.length; start++) {
            for (int length = 0; length <= bytes.length - start; length++) {
                final byte[] segmentBytes = Arrays.copyOfRange(bytes, start, start + length);
                final SeekableByteChannelFactory segmentSbcf = SegmentChannelFactory.create(sbcf, start, length);

                ChannelTestHelper.checkByteArrayAgainstSbc(segmentBytes, segmentSbcf);
            }
        }
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
                protected void closeImpl() throws IOException {
                    isClosedList.set(myInstanceNumber, true);
                }

                @Override
                protected int readImpl(ByteBuffer byteBuffer) throws IOException {
                    return 0;
                }

                @Override
                protected long sizeImpl() throws IOException {
                    return 1;
                }
            };
        }
    }
}
