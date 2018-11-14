package emissary.util;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class HtmlEntityMapTest extends UnitTest {
    @Test
    public void testEntityMap() {
        HtmlEntityMap h = new HtmlEntityMap();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream myOut = new PrintStream(baos);
        h.dumpTestPage(myOut);
        myOut.close();
        String report = baos.toString();
        assertTrue("HtmlEntityMap must contain escaped output", report.indexOf("&Uarr;") > -1);
        assertTrue("HtmlEntityMap must contain utf chars", report.indexOf("\u219F") > -1);
    }
}
