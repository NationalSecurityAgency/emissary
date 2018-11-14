package emissary.scripting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;

import emissary.test.core.UnitTest;
import emissary.util.io.ResourceReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RubyConsoleTest extends UnitTest {
    private RubyConsole console;
    private static Logger logger = LoggerFactory.getLogger(RubyConsoleTest.class);


    @Override
    @Before
    public void setUp() throws Exception {
        // We do this to make sure the place
        // reads our default config for it
        // not something else that got configured in
        ResourceReader rr = new ResourceReader();
        InputStream is = null;
        try {
            logger.debug("Getting config stream for " + this.getClass().getName());
            is = rr.getConfigDataAsStream(this.getClass());
        } catch (Exception ex) {
            logger.error("Cannot create RubyConsole", ex);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException ignore) {
                // empty catch block
            }
        }
        console = RubyConsole.getConsole();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        if (console != null) {
            console.stop();
        }
    }

    @Test
    public void testConsole() {
        try {
            Object answer = console.evalAndWait("$i=6*6", 1000L);
            assertNotNull("Answer should be provided", answer);
            assertEquals("Answer should be correct", "36", answer.toString());
            answer = console.evalAndWait("$i", 1000L);
            assertEquals("Local variable should retain value", "36", answer.toString());
        } catch (Exception ex) {
            logger.debug("Could not use console", ex);
            fail("Could not use console: " + ex.getMessage());
        }
    }

    // @Test
    // Reset is not working with JRuby 1.6.7,
    public void testConsoleReset() throws Exception {
        console.evalAndWait("$i=7*7", 50L);
        console.reset();
        Object answer = console.evalAndWait("$i", 50L);
        if (answer != null) {
            fail("Variable should not retain value after reset not " + answer.getClass().getName() + " - " + answer);
        }
    }

    @Test
    public void testConsoleNoWait() {
        try {
            Object answer = console.evalAndWait("(sleep 1) && (24)", 0L);
            assertNull("Console should have timed out but gave", answer);
        } catch (Exception ex) {
            logger.debug("Could not use console", ex);
            fail("Console exception not expected " + ex.getMessage());
        }
    }

    // @Test
    // Timeout is not working at least with sleep
    public void testConsoleTimeout() {
        try {
            Object answer = console.evalAndWait("(sleep 1) && (42)", 1L);
            assertNull("Console should have timed out but gave " + answer, answer);
        } catch (Exception ex) {
            fail("Console exception not expected " + ex.getMessage());
        }
    }

    @Test
    public void testConsoleStdout() {
        String s = "THIS IS A TEST";
        try {
            console.evalAndWait("puts '" + s + "'", 1000L);
            assertEquals("Console stdout must have puts value", s + "\n", console.getStdout());
        } catch (Exception ex) {
            fail("Console exception not expected " + ex.getMessage());
        }
    }
}
