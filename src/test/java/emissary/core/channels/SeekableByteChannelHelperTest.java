package emissary.core.channels;

import emissary.core.BaseDataObject;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.io.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeekableByteChannelHelperTest {

    private static final String TEST_STRING = "test data";
    private static final byte[] TEST_BYTES = TEST_STRING.getBytes(StandardCharsets.US_ASCII);

    @Test
    void testImmutable() throws IOException {
        final SeekableByteChannelFactory sbcf = SeekableByteChannelHelper.immutable(SeekableByteChannelHelper.memory(TEST_BYTES));
        final SeekableByteChannel sbc = sbcf.create();
        final ByteBuffer someTestBytes = ByteBuffer.wrap(TEST_BYTES);
        assertThrows(NonWritableChannelException.class, () -> sbc.truncate(2l));
        assertThrows(NonWritableChannelException.class, () -> sbc.write(someTestBytes));
    }

    @Test
    void testMemory() throws IOException {
        final SeekableByteChannelFactory sbcf = SeekableByteChannelHelper.memory(TEST_BYTES);
        final SeekableByteChannel sbc = sbcf.create();
        final ByteBuffer buff = ByteBuffer.allocate(4);
        sbc.read(buff);
        buff.flip();
        byte[] bytes = new byte[4];
        buff.get(bytes);
        assertArrayEquals("test".getBytes(), bytes);
    }

    @Test
    void testFile(@TempDir Path tempDir) throws IOException {
        final Path path = tempDir.resolve("testBytes");

        Files.write(TEST_BYTES, path.toFile());

        final SeekableByteChannelFactory sbcf = SeekableByteChannelHelper.file(path);
        final SeekableByteChannel sbc = sbcf.create();
        final ByteBuffer byteBuffer = ByteBuffer.allocate(TEST_BYTES.length);

        sbc.read(byteBuffer);
        assertArrayEquals(TEST_BYTES, byteBuffer.array());
    }

    @Test
    void testFill() throws IOException {
        final byte[] bytes = "0000000000".getBytes(StandardCharsets.US_ASCII);
        final SeekableByteChannelFactory sbcf = SeekableByteChannelHelper.fill(bytes.length, bytes[0]);
        final SeekableByteChannel sbc = sbcf.create();
        final ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length);

        sbc.read(byteBuffer);
        assertArrayEquals(bytes, byteBuffer.array());
    }

    @Test
    void testInputStream() throws IOException {
        final byte[] bytes = "0123456789".getBytes(StandardCharsets.US_ASCII);
        final InputStreamFactory inputStreamFactory = new InputStreamFactory() {
            @Override
            public InputStream create() {
                return new ByteArrayInputStream(bytes);
            }
        };
        final SeekableByteChannelFactory sbcf = SeekableByteChannelHelper.inputStream(bytes.length, inputStreamFactory);
        final SeekableByteChannel sbc = sbcf.create();
        final ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length);

        sbc.read(byteBuffer);
        assertArrayEquals(bytes, byteBuffer.array());
    }

    private static final InputStream IS = new ByteArrayInputStream("Test data".getBytes());

    @Test
    void testGetFromInputStreamWithArrayByteBuffer() throws IOException {
        final ByteBuffer buff = ByteBuffer.allocate(4);
        assertTrue(buff.hasArray());
        assertEquals(4, SeekableByteChannelHelper.getFromInputStream(IS, buff, 0));

        assertEquals(-1, SeekableByteChannelHelper.getFromInputStream(
                new ByteArrayInputStream(new byte[0]), ByteBuffer.allocate(1), 0));
    }

    @Test
    void testGetFromInputStreamWithDirectByteBuffer() throws IOException {
        final ByteBuffer buff = ByteBuffer.allocateDirect(4);
        assertFalse(buff.hasArray());
        assertEquals(4, SeekableByteChannelHelper.getFromInputStream(IS, buff, 0));

        assertEquals(-1, SeekableByteChannelHelper.getFromInputStream(
                new ByteArrayInputStream(new byte[0]), ByteBuffer.allocateDirect(1), 0));
    }

    @Test
    void testGetFromInputStreamWithInvalidOffset() {
        final ByteBuffer buff = ByteBuffer.allocate(1);
        assertThrows(IllegalArgumentException.class, () -> SeekableByteChannelHelper.getFromInputStream(IS, buff, -2));
    }

    private static class ExceptionInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("This InputStream always throws an exception!");
        }
    }

    @Test
    void testLength() throws IOException {
        final byte[] bytes = "01234567890".getBytes(StandardCharsets.US_ASCII);

        assertEquals(bytes.length, SeekableByteChannelHelper.length(new ByteArrayInputStream(bytes)));

        try (InputStream eis = new ExceptionInputStream(); InputStream is = new SequenceInputStream(new ByteArrayInputStream(bytes), eis)) {
            assertEquals(bytes.length, SeekableByteChannelHelper.length(is));
        }
    }

    @Test
    void testGetDataWhenLargerThanMaxInt() throws IOException {
        final BaseDataObject bdo = new BaseDataObject();
        bdo.setFilename("filename.txt");

        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        final Logger channelLogger = (Logger) LoggerFactory.getLogger(SeekableByteChannelHelper.class);

        final String testString = "test data"; // length 9
        bdo.setChannelFactory(SeekableByteChannelHelper.memory(testString.getBytes()));

        try {
            // Wrap the actual call with a way to read the logs
            appender.start();
            channelLogger.addAppender(appender);
            final byte[] trimmedArray = SeekableByteChannelHelper.getByteArrayFromChannel(bdo, 5);
            assertEquals(5, trimmedArray.length);
            assertTrue(appender.list.stream().anyMatch(i -> i.getFormattedMessage().equals(
                    "Returned data for [filename.txt] will be truncated by 4 bytes due to size constraints of byte arrays")));
        } finally {
            channelLogger.detachAppender(appender);
            appender.stop();
        }
    }
}
