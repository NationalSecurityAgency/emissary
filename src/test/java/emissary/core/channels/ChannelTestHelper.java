package emissary.core.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChannelTestHelper {
    private ChannelTestHelper() {};

    public static void checkByteArrayAgainstSbc(final byte[] bytesToVerify, final SeekableByteChannelFactory<?> sbcf)
            throws IOException {
        try (final SeekableByteChannel sbc = sbcf.create()) {
            int startIndex, length;
            for (startIndex = 0; startIndex < bytesToVerify.length; startIndex++) { // Check position
                for (length = bytesToVerify.length - startIndex; length > 0; length--) { // Check length
                    // Seek to starting point
                    sbc.position(startIndex);

                    // Confirm the 'requested' position is the 'current' position
                    // Because of lazy evaluation, we're just storing that the consumer wants to seek to the requested
                    // position - the channel hasn't actually changed position yet
                    assertEquals(startIndex, sbc.position());

                    // Get ready to read
                    final ByteBuffer buff = ByteBuffer.allocate(length);

                    // Actually read (at this point, the channel/input streams are created and updated as required)
                    sbc.read(buff);

                    // Confirm that the current position is where we expect - the point in the file that we read to.
                    assertEquals(startIndex + length, sbc.position());

                    // Check we actually got the right stuff
                    assertArrayEquals(Arrays.copyOfRange(bytesToVerify, startIndex, startIndex + length), buff.array());
                }
            }

            // Do the same as above but starting from the end of the string/file
            for (startIndex = (bytesToVerify.length - 1); startIndex > -1; startIndex--) { // Check position
                for (length = bytesToVerify.length - startIndex; length > 0; length--) { // Check length
                    sbc.position(startIndex);
                    assertEquals(startIndex, sbc.position());
                    final ByteBuffer buff = ByteBuffer.allocate(length);
                    sbc.read(buff);
                    assertEquals(startIndex + length, sbc.position());
                    assertArrayEquals(Arrays.copyOfRange(bytesToVerify, startIndex, startIndex + length), buff.array());
                }
            }
        }
    }
}
