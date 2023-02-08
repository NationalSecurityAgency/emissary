package emissary.core.channels;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertThrows;

class BufferedChannelFactoryTest {
    @Test
    void testCache() throws IOException {

        final byte[] bytes = new byte[67];
        final SeekableByteChannelFactory bytesSbcf = InMemoryChannelFactory.create(bytes);

        new Random(0).nextBytes(bytes);

        assertThrows(NullPointerException.class, () -> BufferedChannelFactory.create(null, 10));
        assertThrows(IllegalArgumentException.class, () -> BufferedChannelFactory.create(bytesSbcf, -1));

        for (int bufferSize = 1; bufferSize < bytes.length * 3; bufferSize++) {
            final SeekableByteChannelFactory bufferedSbcf = BufferedChannelFactory.create(bytesSbcf, bufferSize);

            ChannelTestHelper.checkByteArrayAgainstSbc(bytes, bufferedSbcf);
        }
    }
}
