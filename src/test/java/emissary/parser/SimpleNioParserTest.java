package emissary.parser;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class SimpleNioParserTest extends UnitTest {

    private Path testDataFile;
    @Nullable
    private FileChannel channel;
    private static final int DATALEN = 1000;
    @Nullable
    RandomAccessFile raf = null;

    @Test
    void testInterface() {
        // Compile should test this but in case anyone changes it
        // They will have to look here :-)
        try {
            SimpleNioParser sp = new SimpleNioParser(channel);
            assertInstanceOf(SessionParser.class, sp, "SessionParser interface definition");
        } catch (ParserException ex) {
            fail("SimpleNioParser is not a SessionParser", ex);
        }
    }


    @Test
    void testDataSlicing() throws ParserException {
        SimpleNioParser sp = new SimpleNioParser(channel);
        DecomposedSession sd = sp.getNextSession();
        assertNotNull(sd, "Session object created");
        assertTrue(sd.isValid(), "Session decomposed");
        assertEquals(DATALEN, sd.getData().length, "Data size");
    }

    @Test
    void testNonExistingSession() throws ParserException {
        SimpleNioParser sp = new SimpleNioParser(channel);
        DecomposedSession sd = sp.getNextSession();
        assertTrue(sd.isValid(), "Session decomposed");
        assertThrows(ParserEOFException.class, sp::getNextSession);
    }

    @BeforeEach
    public void initTestDataFile() throws IOException {
        byte[] DATA = new byte[DATALEN];
        Arrays.fill(DATA, (byte) 'a');

        // Make test file
        testDataFile = Files.createTempFile(temporaryDirectory.toPath(), "SimpleNioParserTest", ".dat");

        // Write the test data to the file
        try (OutputStream os = Files.newOutputStream(testDataFile)) {
            os.write(DATA);
        }

        raf = new RandomAccessFile(testDataFile.toFile(), "r");
        channel = raf.getChannel();
    }

    @AfterEach
    public void cleanup() throws Exception {
        super.tearDown();
        if (channel != null) {
            channel.close();
        }
        if (raf != null) {
            raf.close();
        }
        Files.deleteIfExists(testDataFile);
    }


}
