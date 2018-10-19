package emissary.output.roller.journal;

import static emissary.util.io.UnitTestFileUtils.cleanupDirectoryRecursively;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import emissary.test.core.UnitTest;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class JournaledChannelTest extends UnitTest {

    private static Path TEMP_DIR;
    private Path p;
    private JournaledChannel channel;
    private String onekstring = "";

    @BeforeClass
    public static void setUpClass() throws Exception {
        TEMP_DIR = Files.createTempDirectory("journaledChannelTest");
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        this.p = TEMP_DIR.resolve(UUID.randomUUID().toString());
        this.channel = new JournaledChannel(this.p, "unittest", 0);
        for (int i = 0; i < 1024; i++) {
            this.onekstring += "" + (i % 10);
        }
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (this.channel != null) {
            IOUtils.closeQuietly(this.channel);
        }
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
        cleanupDirectoryRecursively(TEMP_DIR);
    }

    @Test
    public void testWrite_int() throws Exception {
        this.channel.write(1);
        final Path outfile = this.channel.path;
        this.channel.close();
        assertTrue("Wrote one byte", Files.size(outfile) == 1);
    }

    @Test
    public void testWrite_3args() throws Exception {
        final byte[] onekbuff = this.onekstring.getBytes();
        this.channel.write(onekbuff, 0, onekbuff.length);
        this.channel.commit();
        assertTrue("Position is 1k " + this.channel.position(), this.channel.position() == onekbuff.length);
        String fiveKStr = "";
        for (int i = 0; i < 5; i++) {
            fiveKStr += this.onekstring;
        }
        final byte[] fiveK = fiveKStr.getBytes();
        this.channel.write(fiveK, 0, fiveK.length);
        this.channel.commit();
        assertTrue("Position is 6k " + this.channel.position(), this.channel.position() == (onekbuff.length + fiveK.length));
        final Path channelPath = this.channel.path;
        this.channel.close();
        try (SeekableByteChannel sbc = Files.newByteChannel(channelPath)) {
            final ByteBuffer buff = ByteBuffer.allocate(1024);
            for (int i = 0; i < 6; i++) {
                buff.clear();
                sbc.read(buff);
                buff.flip();
                final String read = new String(buff.array());
                assertTrue("Read one k back\n" + read + "\n" + this.onekstring, read.equals(this.onekstring));
            }
        }
    }

    @Test
    public void testWrite_ByteBuffer() throws Exception {
        final ByteBuffer buff = ByteBuffer.allocateDirect(6 * 1024);
        for (int i = 0; i < 6; i++) {
            buff.put(this.onekstring.getBytes());
        }
        buff.flip();
        this.channel.write(buff);
        assertTrue("6k written " + this.channel.position() + " " + buff.capacity(), this.channel.position() == buff.capacity());
    }

    @Ignore
    @Test
    public void testPosition_0args() throws Exception {}

    @Ignore
    @Test
    public void testSize() throws Exception {}

    @Ignore
    @Test
    public void testIsOpen() {}

    @Ignore
    @Test
    public void testCommit() throws Exception {}

    @Ignore
    @Test
    public void testClose() throws Exception {}

    @Ignore
    @Test
    public void testPosition_long() throws Exception {}

    @Ignore
    @Test
    public void testRead() throws Exception {}

    @Ignore
    @Test
    public void testTruncate() throws Exception {}
}
