package emissary.core.channels;

import emissary.test.core.junit5.UnitTest;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryChannelFactoryTest extends UnitTest {

    @Test
    void testCannotCreateFactoryWithNullByteArray() {
        assertThrows(NullPointerException.class, () -> InMemoryChannelFactory.create(null),
                "Factory allowed null to be set, which would fail when getting an instance later");
    }

    @Test
    void testNormalPath() throws IOException {
        final String testString = "Test data";
        final SeekableByteChannelFactory sbcf = InMemoryChannelFactory.create(testString.getBytes());
        final ByteBuffer buff = ByteBuffer.allocate(testString.length());
        sbcf.create().read(buff);
        assertEquals(testString, new String(buff.array()));
    }

    @Test
    void testImmutability() throws IOException {
        final SeekableByteChannelFactory sbcf = InMemoryChannelFactory.create("Test data".getBytes());
        final SeekableByteChannel sbc = sbcf.create();
        final ByteBuffer buff = ByteBuffer.wrap("New data".getBytes());
        assertThrows(NonWritableChannelException.class, () -> sbc.write(buff), "Can't write to byte channel as it's immutable");
        assertThrows(NonWritableChannelException.class, () -> sbc.truncate(5L), "Can't truncate byte channel as it's immutable");
        assertThrows(ClassCastException.class, () -> ((SeekableInMemoryByteChannel) sbc).array(),
                "Can't get different variant of SBC as we've abstracted it away");
    }

    @Test
    void testCanCreateAndRetrieveEmptyByteArray() throws IOException {
        final SeekableByteChannelFactory simbcf = InMemoryChannelFactory.create(new byte[0]);
        assertEquals(0L, simbcf.create().size());
    }
}
