package emissary.core.channels;

import emissary.test.core.junit5.UnitTest;

import com.google.common.io.Files;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileChannelFactoryTest extends UnitTest {
    private static final String TEST_STRING = "test data";
    private static final byte[] TEST_BYTES = TEST_STRING.getBytes(StandardCharsets.US_ASCII);

    @SuppressWarnings("NonFinalStaticField")
    private static SeekableByteChannelFactory sbcf;

    @BeforeAll
    static void setup(@TempDir final Path tempDir) throws IOException {
        final Path path = tempDir.resolve("testBytes");

        Files.write(TEST_BYTES, path.toFile());

        sbcf = FileChannelFactory.create(path);
    }

    @Test
    void testCanCreateMultipleIndependentChannelsForTheSameFile() throws IOException {
        final SeekableByteChannel sbc = sbcf.create();
        final SeekableByteChannel sbc2 = sbcf.create();

        final ByteBuffer buff = ByteBuffer.allocate(4);
        sbc.read(buff);
        assertEquals("test", new String(buff.array()));
        sbc2.read(buff);
        assertEquals("test", new String(buff.array()));
    }

    @Test
    void testCannotCreateFactoryWithNullByteArray() {
        assertThrows(NullPointerException.class, () -> FileChannelFactory.create(null),
                "Factory allowed null to be set, which would fail when getting an instance later");
    }

    @Test
    void testNormalPath() throws IOException {
        final ByteBuffer buff = ByteBuffer.allocate(TEST_STRING.length());
        sbcf.create().read(buff);
        assertEquals(TEST_STRING, new String(buff.array()));
    }

    @Test
    void testPositioning() throws IOException {
        final ByteBuffer buff = ByteBuffer.allocate(TEST_STRING.length() - 2);
        sbcf.create().position(2).read(buff);
        assertEquals(TEST_STRING.substring(2), new String(buff.array()));
    }

    @Test
    void testClose() throws IOException {
        final SeekableByteChannel sbc = sbcf.create();
        assertTrue(sbc.isOpen());
        sbc.read(ByteBuffer.allocate(1));
        sbc.close();
        assertFalse(sbc.isOpen());
    }

    @Test
    void testImmutability() throws IOException {
        final SeekableByteChannel sbc = sbcf.create();
        final ByteBuffer buff = ByteBuffer.wrap("New data".getBytes());
        assertThrows(NonWritableChannelException.class, () -> sbc.write(buff), "Can't write to byte channel as it's immutable");
        assertThrows(NonWritableChannelException.class, () -> sbc.truncate(5L), "Can't truncate byte channel as it's immutable");
    }

    @Test
    void testCanCreateAndRetrieveEmptyFile(@TempDir final Path tempDir) throws IOException {
        final Path path = tempDir.resolve("emptyBytes");
        Files.write(new byte[0], path.toFile());
        final SeekableByteChannelFactory simbcf = FileChannelFactory.create(path);
        assertEquals(0L, simbcf.create().size());
    }

    @Test
    void testConstructors() throws IOException {
        assertEquals(9, sbcf.create().size());
        assertThrows(NullPointerException.class, () -> FileChannelFactory.create(null), "Can't create a FCF with nulls");
    }
}
