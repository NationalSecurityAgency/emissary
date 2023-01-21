package emissary.core.channels;

import com.google.common.io.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileChannelFactoryTest {
    private static final String TEST_STRING = "test data";
    private static final byte[] TEST_BYTES = TEST_STRING.getBytes(StandardCharsets.US_ASCII);

    @Test
    void testCanCreateMultipleIndependentChannelsForTheSameFile(@TempDir Path tempDir) throws IOException {
        final Path path = tempDir.resolve("testBytes");

        Files.write(TEST_BYTES, path.toFile());

        final SeekableByteChannel sbc = FileChannelFactory.create(path).create();
        final SeekableByteChannel sbc2 = FileChannelFactory.create(path).create();

        final ByteBuffer buff = ByteBuffer.allocate(4);
        sbc.read(buff);
        assertEquals("test", new String(buff.array(), UTF_8));
        sbc2.read(buff);
        assertEquals("test", new String(buff.array(), UTF_8));
    }

    @Test
    void testCannotCreateFactoryWithNullByteArray() {
        assertThrows(NullPointerException.class, () -> FileChannelFactory.create(null),
                "Factory allowed null to be set, which would fail when getting an instance later");
    }

    @Test
    void testNormalPath(@TempDir Path tempDir) throws IOException {
        final Path path = tempDir.resolve("testBytes");

        Files.write(TEST_BYTES, path.toFile());

        final SeekableByteChannelFactory sbcf = FileChannelFactory.create(path);
        final ByteBuffer buff = ByteBuffer.allocate(TEST_STRING.length());
        sbcf.create().read(buff);
        assertEquals(TEST_STRING, new String(buff.array(), UTF_8));
    }

    @Test
    void testClose(@TempDir Path tempDir) throws IOException {
        final Path path = tempDir.resolve("testBytes");
        Files.write(TEST_BYTES, path.toFile());
        final SeekableByteChannelFactory sbcf = FileChannelFactory.create(path);
        try (final SeekableByteChannel sbc = sbcf.create()) {
            assertTrue(sbc.isOpen());
            sbc.close();
            assertFalse(sbc.isOpen());
        }

        try (final SeekableByteChannel sbc = sbcf.create()) {
            assertTrue(sbc.isOpen());
            sbc.read(ByteBuffer.allocate(1));
            sbc.close();
            assertFalse(sbc.isOpen());
        }
    }

    @Test
    void testImmutability(@TempDir Path tempDir) throws IOException {
        final Path path = tempDir.resolve("testBytes");

        Files.write(TEST_BYTES, path.toFile());

        final SeekableByteChannelFactory sbcf = FileChannelFactory.create(path);
        final SeekableByteChannel sbc = sbcf.create();
        final ByteBuffer buff = ByteBuffer.wrap("New data".getBytes(UTF_8));
        assertThrows(NonWritableChannelException.class, () -> sbc.write(buff), "Can't write to byte channel as it's immutable");
        assertThrows(NonWritableChannelException.class, () -> sbc.truncate(5l), "Can't truncate byte channel as it's immutable");
    }

    @Test
    void testCanCreateAndRetrieveEmptyFile(@TempDir Path tempDir) throws IOException {
        final Path path = tempDir.resolve("testBytes");

        Files.write(new byte[0], path.toFile());

        final SeekableByteChannelFactory simbcf = FileChannelFactory.create(path);
        assertEquals(0L, simbcf.create().size());
    }

    @Test
    void testConstructors(@TempDir Path tempDir) throws IOException {
        final Path path = tempDir.resolve("testBytes");

        Files.write(TEST_BYTES, path.toFile());

        final SeekableByteChannelFactory sbcf = FileChannelFactory.create(path);
        assertEquals(9, sbcf.create().size());
        assertThrows(NullPointerException.class, () -> FileChannelFactory.create(null), "Can't create a FCF with nulls");
    }
}
