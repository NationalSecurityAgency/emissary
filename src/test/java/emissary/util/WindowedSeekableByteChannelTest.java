package emissary.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;

import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.Test;

/**
 *
 */
class WindowedSeekableByteChannelTest extends UnitTest {

    // lower case 'a'
    static final byte LCA = 97;
    static final byte NULL = 0x00;
    static final int I_ALPHABET = 26;

    public WindowedSeekableByteChannelTest() {}

    // build array containing sequence(s) of a-z
    private static byte[] buildArry(final int size) {
        final byte[] b = new byte[size];
        for (int i = 0; i < b.length;) {
            final int remaining = (b.length - i);
            final int letterCount = remaining > 25 ? I_ALPHABET : remaining;
            for (int j = 0; j < letterCount; j++) {
                b[i++] = (byte) (LCA + j);
            }
        }
        return b;
    }

    private static ReadableByteChannel getChannel(final int size) {
        return Channels.newChannel(new ByteArrayInputStream(buildArry(size)));
    }

    @Test
    void testConstruction() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new WindowedSeekableByteChannel(null, 1));
        @SuppressWarnings({"resource"})
        final WindowedSeekableByteChannel instance = new WindowedSeekableByteChannel(getChannel(10), 30);
        assertEquals(10L, instance.size());
        assertEquals(10L, instance.getMaxPosition());
    }

    /**
     * Test of close method, of class WindowedSeekableByteChannel.
     */
    @Test
    void testClose() throws Exception {
        final WindowedSeekableByteChannel instance = new WindowedSeekableByteChannel(getChannel(1), 1);
        instance.close();
        assertFalse(instance.isOpen(), "Instance should be closed");
        boolean ex = false;
        try {
            final ByteBuffer dst = ByteBuffer.allocate(1);
            instance.read(dst);
        } catch (ClosedChannelException c) {
            ex = true;
        }
        assertTrue(ex, "ClosedChannelException after close");
    }

    /**
     * Test of read method, of class WindowedSeekableByteChannel.
     */
    @Test
    void testRead() throws Exception {
        final byte[] buff = new byte[I_ALPHABET];
        final byte[] alphabet = buildArry(I_ALPHABET);
        final ByteBuffer destination = ByteBuffer.wrap(buff);
        @SuppressWarnings({"resource"})
        final WindowedSeekableByteChannel instance = new WindowedSeekableByteChannel(getChannel(I_ALPHABET * 2), 10);
        // 2 full reads will get us to the end
        instance.read(destination);
        assertArrayEquals(alphabet, destination.array());
        destination.put(0, NULL);
        destination.position(0);

        instance.read(destination);
        assertArrayEquals(alphabet, destination.array());
        destination.put(0, NULL);
        destination.position(0);

        final int result = instance.read(destination);
        assertEquals(-1, result);
    }

    /**
     * Test using odd values for all buffer sizes.
     */
    @Test
    void testReadOdd() throws Exception {
        final byte[] buff = new byte[7];
        final int ODD = 53;
        final ByteBuffer destination = ByteBuffer.wrap(buff);
        @SuppressWarnings("resource")
        final WindowedSeekableByteChannel instance = new WindowedSeekableByteChannel(getChannel(ODD), 19);
        int result = 0;
        int read = instance.read(destination);
        while (read != -1) {
            result += read;
            destination.position(0);
            read = instance.read(destination);
        }

        assertEquals(ODD, result, "Read " + ODD + " bytes");
        assertEquals(LCA, buff[(ODD % buff.length) - 1], "Last byte was 'a'");
    }

    /**
     * Test of read into odd dest Buff with even window buffer size.
     */
    @Test
    void testReadOddEven() throws Exception {

        final byte[] buff = new byte[17];
        final ByteBuffer destination = ByteBuffer.wrap(buff);
        final int EVEN = 1024;
        final int fourKB = EVEN * 4;

        final int FINAL_ALPHABET_LEN = fourKB % I_ALPHABET;
        final int LAST_READ_LENGTH = fourKB % buff.length;

        @SuppressWarnings("resource")
        final WindowedSeekableByteChannel instance = new WindowedSeekableByteChannel(getChannel(fourKB), EVEN);
        int result = 0;

        int read = instance.read(destination);
        while (read != -1) {
            result += read;
            destination.position(0);
            read = instance.read(destination);
        }
        assertEquals(fourKB, result, "Expected to read 4KB");
        assertEquals(buildArry(I_ALPHABET)[FINAL_ALPHABET_LEN - 1], buff[LAST_READ_LENGTH - 1], "Last byte matches");
    }

    /**
     * Test reading into even size dst with odd size window.
     */
    @Test
    void testReadEvenOdd() throws Exception {
        final byte[] buff = new byte[20];
        final ByteBuffer destination = ByteBuffer.wrap(buff);
        final int ODD = 47;
        final int oddTimes3 = ODD * 3;

        final int FINAL_ALPHABET_LEN = oddTimes3 % I_ALPHABET;
        final int LAST_READ_LENGTH = oddTimes3 % buff.length;

        @SuppressWarnings("resource")
        final WindowedSeekableByteChannel instance = new WindowedSeekableByteChannel(getChannel(oddTimes3), ODD);
        int result = 0;

        while (true) {
            final int read = instance.read(destination);
            if (read == -1) {
                break;
            }
            result += read;
            destination.position(0);
        }
        assertEquals(oddTimes3, result, "Expected to read " + oddTimes3 + " bytes");
        assertEquals(buildArry(I_ALPHABET)[FINAL_ALPHABET_LEN - 1], buff[LAST_READ_LENGTH - 1], "Last byte matches");
    }

    /**
     * Test of position method, of class WindowedSeekableByteChannel.
     */
    @Test
    void testPosition_long() throws Exception {

        @SuppressWarnings("resource")
        WindowedSeekableByteChannel instance = new WindowedSeekableByteChannel(getChannel(26), 20);
        final ByteBuffer b = ByteBuffer.allocate(1);
        instance.position(10);
        instance.read(b);
        assertEquals(LCA + 10, b.get(0));

        instance.position(0);
        b.flip();
        instance.read(b);
        assertEquals(LCA, b.get(0));

        instance = new WindowedSeekableByteChannel(getChannel(40), 10);
        // set past max in memory
        instance.position(26);
        b.flip();
        instance.read(b);
        // should go back to A
        assertEquals(LCA, b.get(0));

        assertTrue(instance.getMinPosition() > 0);
        boolean threw = false;
        try {
            instance.position(0L);
        } catch (IllegalStateException ex) {
            threw = true;
        }
        assertTrue(threw, "Did not throw while attempting to move before available window");
    }

    @Test
    void testSizePosition() throws Exception {
        @SuppressWarnings("resource")
        WindowedSeekableByteChannel instance = new WindowedSeekableByteChannel(getChannel(26), 50);
        assertEquals(26, instance.getMaxPosition());
        assertEquals(0L, instance.position());

        instance = new WindowedSeekableByteChannel(getChannel(26), 13);
        assertEquals(14, instance.getMaxPosition());

        final ByteBuffer b = ByteBuffer.allocate(20);
        assertTrue(instance.read(b) == 20 && instance.position() == 20);
        boolean eofEx = false;
        try {
            instance.position(Integer.MAX_VALUE);
        } catch (EOFException ex) {
            eofEx = true;
        }
        assertTrue(eofEx);
    }

    /* mostly for coverage */
    @Test
    void testUnsupportedOps() throws Exception {
        boolean hitEx = false;
        try {
            @SuppressWarnings("resource")
            final WindowedSeekableByteChannel instance = new WindowedSeekableByteChannel(getChannel(26), 50);
            instance.write(ByteBuffer.allocate(10));
        } catch (UnsupportedOperationException e) {
            hitEx = true;
        }
        assertTrue(hitEx);

        hitEx = false;
        try {
            @SuppressWarnings("resource")
            final WindowedSeekableByteChannel instance = new WindowedSeekableByteChannel(getChannel(26), 50);
            instance.truncate(10L);

        } catch (UnsupportedOperationException e) {
            hitEx = true;
        }
        assertTrue(hitEx);
    }
}
