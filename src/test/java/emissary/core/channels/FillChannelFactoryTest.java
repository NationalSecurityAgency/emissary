package emissary.core.channels;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FillChannelFactoryTest {
    @Test
    void testCreate() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> FillChannelFactory.create(-1, (byte) 0));
    }

    @Test
    void testExhaustively() throws IOException {
        final byte[] bytes = new byte[15];

        for (byte b = -100; b < 100; b++) {
            Arrays.fill(bytes, b);

            ChannelTestHelper.checkByteArrayAgainstSbc(bytes, FillChannelFactory.create(bytes.length, b));
        }
    }

    @Test
    void testDirectReadImpl() throws IOException {
        final byte[] bytes = new byte[15];

        Arrays.fill(bytes, (byte) 9);

        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bytes.length);
        final byte[] byteBufferBytes = new byte[bytes.length];

        try (SeekableByteChannel sbc = FillChannelFactory.create(bytes.length, bytes[0]).create()) {
            assertEquals(15, IOUtils.read(sbc, byteBuffer));

            byteBuffer.position(0);
            byteBuffer.get(byteBufferBytes);

            assertArrayEquals(bytes, byteBufferBytes);
        }
    }
}
