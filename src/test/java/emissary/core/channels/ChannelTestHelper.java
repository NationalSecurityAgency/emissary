package emissary.core.channels;

import emissary.test.core.junit5.UnitTest;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChannelTestHelper extends UnitTest {
    private ChannelTestHelper() {};

    public static void checkByteArrayAgainstSbc(final byte[] bytesToVerify, final SeekableByteChannelFactory sbcf)
            throws IOException {
        try (final SeekableByteChannel sbc = sbcf.create()) {
            int startIndex, length;
            for (startIndex = 0; startIndex < bytesToVerify.length; startIndex++) {
                for (length = bytesToVerify.length - startIndex; length > 0; length--) {
                    checkSegment(sbc, bytesToVerify, startIndex, length);
                }
            }

            // Do the same as above but starting from the end of the string/file
            for (startIndex = (bytesToVerify.length - 1); startIndex > -1; startIndex--) {
                for (length = bytesToVerify.length - startIndex; length > 0; length--) {
                    checkSegment(sbc, bytesToVerify, startIndex, length);
                }
            }
        }
    }

    private static void checkSegment(final SeekableByteChannel sbc, final byte[] bytesToVerify, final int startIndex,
            final int length) throws IOException {
        sbc.position(startIndex);
        assertEquals(startIndex, sbc.position(), "Setting initial position of sbc is incorrect!");

        final ByteBuffer buff = ByteBuffer.allocate(length);
        final int bytesRead = IOUtils.read(sbc, buff);

        assertEquals(length, bytesRead, "bytesRead value is incorrect!");
        assertEquals(startIndex + length, sbc.position(), "Sbc position after read is incorrect!");
        assertArrayEquals(Arrays.copyOfRange(bytesToVerify, startIndex, startIndex + length), buff.array(),
                "Bytes read are incorrect!");
    }
}
