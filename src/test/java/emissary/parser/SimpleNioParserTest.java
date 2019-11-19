package emissary.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SimpleNioParserTest extends UnitTest {

    private Path testDataFile;
    private FileChannel channel;
    private static final int DATALEN = 1000;
    RandomAccessFile raf = null;


    @Test
    public void testInterface() {
        // Compile should test this but in case anyone changes it
        // They will have to look here :-)
        try {
            SimpleNioParser sp = new SimpleNioParser(channel);
            assertTrue("SimpleParser interface definition", sp instanceof SessionParser);
        } catch (ParserException ex) {
            fail(ex.getMessage());
        }
    }


    @Test
    public void testDataSlicing() throws ParserException, ParserEOFException {
        SimpleNioParser sp = new SimpleNioParser(channel);
        DecomposedSession sd = sp.getNextSession();
        assertNotNull("Session object created", sd);
        assertTrue("Session decomposed", sd.isValid());
        assertEquals("Data size", DATALEN, sd.getData().length);
    }

    @Test
    public void testNonExistingSession() throws ParserException, ParserEOFException {
        SimpleNioParser sp = new SimpleNioParser(channel);
        DecomposedSession sd = sp.getNextSession();
        assertTrue("Session decomposed", sd.isValid());
        try {
            sp.getNextSession();
            fail("Produced extra session rather than throw ParserEOF");
        } catch (ParserEOFException ex) {
            // expected
        }
    }

    @Before
    public void initTestDataFile() throws IOException {
        byte[] DATA = new byte[DATALEN];
        for (int i = 0; i < DATA.length; i++) {
            DATA[i] = 'a';
        }

        // Make test file
        testDataFile = Files.createTempFile("SimpleNioParserTest", ".dat");

        // Write the test data to the file
        try (OutputStream os = Files.newOutputStream(testDataFile)) {
            os.write(DATA);
        }

        raf = new RandomAccessFile(testDataFile.toFile(), "r");
        channel = raf.getChannel();
    }

    @After
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
