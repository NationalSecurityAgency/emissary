package emissary.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SimpleNioParserTest extends UnitTest {

    private File testDataFile;
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
        testDataFile = File.createTempFile("SimpleNioParserTest", ".dat");
        testDataFile.deleteOnExit();

        // Write the test data to the file
        FileOutputStream os = new FileOutputStream(testDataFile);
        os.write(DATA);
        os.close();

        raf = new RandomAccessFile(testDataFile, "r");
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
    }


}
