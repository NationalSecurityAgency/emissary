package emissary.output.roller.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import emissary.test.core.junit5.UnitTest;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournaledChannelTest extends UnitTest {

    @TempDir
    private static Path TEMP_DIR;
    private JournaledChannel channel;
    private String onekstring = "";

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        this.channel = new JournaledChannel(TEMP_DIR.resolve(UUID.randomUUID().toString()), "unittest", 0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1024; i++) {
            sb.append(i % 10);
        }
        this.onekstring = sb.toString();
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (this.channel != null) {
            IOUtils.closeQuietly(this.channel);
        }
    }

    @Test
    void testWrite_int() throws Exception {
        this.channel.write(1);
        final Path outfile = this.channel.path;
        this.channel.close();
        assertEquals(1, Files.size(outfile), "Wrote one byte");
    }

    @Test
    void testWrite_3args() throws Exception {
        final byte[] onekbuff = this.onekstring.getBytes();
        this.channel.write(onekbuff, 0, onekbuff.length);
        this.channel.commit();
        assertEquals(this.channel.position(), onekbuff.length, "Position is 1k " + this.channel.position());
        StringBuilder fiveKStr = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            fiveKStr.append(this.onekstring);
        }
        final byte[] fiveK = fiveKStr.toString().getBytes();
        this.channel.write(fiveK, 0, fiveK.length);
        this.channel.commit();
        assertEquals(this.channel.position(), (onekbuff.length + fiveK.length), "Position is 6k " + this.channel.position());
        final Path channelPath = this.channel.path;
        this.channel.close();
        try (SeekableByteChannel sbc = Files.newByteChannel(channelPath)) {
            final ByteBuffer buff = ByteBuffer.allocate(1024);
            for (int i = 0; i < 6; i++) {
                buff.clear();
                sbc.read(buff);
                buff.flip();
                final String read = new String(buff.array());
                assertEquals(read, this.onekstring, "Read one k back\n" + read + "\n" + this.onekstring);
            }
        }
    }

    @Test
    void testWrite_ByteBuffer() throws Exception {
        final ByteBuffer buff = ByteBuffer.allocateDirect(6 * 1024);
        for (int i = 0; i < 6; i++) {
            buff.put(this.onekstring.getBytes());
        }
        buff.flip();
        this.channel.write(buff);
        assertEquals(this.channel.position(), buff.capacity(), "6k written " + this.channel.position() + " " + buff.capacity());
    }

    @Test
    void testSize() throws IOException {
        assertEquals(0, channel.size());
        channel.write(1);
        assertEquals(1, channel.size());
    }

    @Test
    void testIsOpen() {
        assertTrue(channel.isOpen());
    }

    @Test
    void testPosition_long() {
        assertThrows(UnsupportedOperationException.class, () -> channel.position(1));
    }

    @Test
    void testRead() {
        ByteBuffer buff = ByteBuffer.allocateDirect(1);
        assertThrows(UnsupportedOperationException.class, () -> channel.read(buff));
    }

    @Test
    void testTruncate() {
        assertThrows(UnsupportedOperationException.class, () -> channel.truncate(1));
    }
}
