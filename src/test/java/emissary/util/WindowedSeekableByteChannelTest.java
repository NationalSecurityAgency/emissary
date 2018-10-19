package emissary.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;

import emissary.test.core.UnitTest;
import org.junit.Test;

/**
 *
 */
public class WindowedSeekableByteChannelTest extends UnitTest {

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
    public void testConstruction() throws Exception {
        try {
            @SuppressWarnings({"resource", "unused"})
            final WindowedSeekableByteChannel instance = new WindowedSeekableByteChannel(null, 1);
            // should throw
            assertTrue("Should have thrown IllegalArgException", false);
        } catch (IllegalArgumentException ex) {
            // old style
        }
        @SuppressWarnings({"resource"})
        final WindowedSeekableByteChannel instance = new WindowedSeekableByteChannel(getChannel(10), 30);
        assertTrue(instance.size() == 10L);
        assertTrue(instance.getMaxPosition() == 10L);
    }

    /**
     * Test of close method, of class WindowedSeekableByteChannel.
     */
    @Test
    public void testClose() throws Exception {
        final WindowedSeekableByteChannel instance = new WindowedSeekableByteChannel(getChannel(1), 1);
        instance.close();
        assertFalse("Instance should be closed", instance.isOpen());
        boolean ex = false;
        try {
            final ByteBuffer dst = ByteBuffer.allocate(1);
            instance.read(dst);
        } catch (ClosedChannelException c) {
            ex = true;
        }
        assertTrue("ClosedChannelException after close", ex);
    }

    /**
     * Test of read method, of class WindowedSeekableByteChannel.
     */
    @Test
    public void testRead() throws Exception {
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
    public void testReadOdd() throws Exception {
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

        assertEquals("Read " + ODD + " bytes", ODD, result);
        assertEquals("Last byte was 'a'", LCA, buff[(ODD % buff.length) - 1]);
    }

    /**
     * Test of read into odd dest Buff with even window buffer size.
     */
    @Test
    public void testReadOddEven() throws Exception {

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
        assertEquals("Expected to read 4KB", fourKB, result);
        assertEquals("Last byte matches", buildArry(I_ALPHABET)[FINAL_ALPHABET_LEN - 1], buff[LAST_READ_LENGTH - 1]);
    }

    /**
     * Test reading into even size dst with odd size window.
     */
    @Test
    public void testReadEvenOdd() throws Exception {
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
        assertEquals("Expected to read " + oddTimes3 + " bytes", oddTimes3, result);
        assertEquals("Last byte matches", buildArry(I_ALPHABET)[FINAL_ALPHABET_LEN - 1], buff[LAST_READ_LENGTH - 1]);
    }

    /**
     * Test of position method, of class WindowedSeekableByteChannel.
     */
    @Test
    public void testPosition_long() throws Exception {

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
        assertTrue("Did not throw while attempting to move before available window", threw);
    }

    @Test
    public void testSizePosition() throws Exception {
        @SuppressWarnings("resource")
        WindowedSeekableByteChannel instance = new WindowedSeekableByteChannel(getChannel(26), 50);
        assertTrue(instance.getMaxPosition() == 26);
        assertTrue(instance.position() == 0L);

        instance = new WindowedSeekableByteChannel(getChannel(26), 13);
        assertTrue(instance.getMaxPosition() == 14);

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
    public void testUnsupportedOps() throws Exception {
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
