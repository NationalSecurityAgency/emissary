package emissary.core.channels;

import emissary.test.core.junit5.UnitTest;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImmutableChannelFactoryTest extends UnitTest {
    private static final String TEST_STRING = "Test data";

    @Test
    void testNormalPath() throws IOException {
        final SeekableByteChannelFactory simbcf = new SeekableByteChannelFactory() {
            @Override
            public SeekableByteChannel create() {
                return new SeekableInMemoryByteChannel(TEST_STRING.getBytes());
            }
        };
        final SeekableByteChannelFactory sbcf = ImmutableChannelFactory.create(simbcf);
        final ByteBuffer buff = ByteBuffer.wrap(TEST_STRING.concat(TEST_STRING).getBytes());
        try (final SeekableByteChannel sbc = sbcf.create()) {
            assertThrows(NonWritableChannelException.class, () -> sbc.write(buff), "Writes aren't allowed to immutable channels");
        }
    }

    @Test
    void testCanCreateAndRetrieveEmptyByteArray() throws IOException {
        assertThrows(NullPointerException.class, () -> ImmutableChannelFactory.create(null),
                "Must provide a channel to make unmodifiable");
        SeekableByteChannelFactory sbcf = InMemoryChannelFactory.create(new byte[0]);
        SeekableByteChannel sbc = ImmutableChannelFactory.create(sbcf).create();
        assertEquals(0, sbc.size());
    }

    @Test
    void testOverrides() throws IOException {
        final SeekableByteChannelFactory sbcf = InMemoryChannelFactory.create(TEST_STRING.getBytes());
        final SeekableByteChannel sbc = ImmutableChannelFactory.create(sbcf).create();
        assertTrue(sbc.isOpen());
        sbc.position(3);
        assertEquals(3, sbc.position());
        sbc.close();
        assertFalse(sbc.isOpen());
    }
}
