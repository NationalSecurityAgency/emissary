package emissary.util.web;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class HtmlEscaperTest extends UnitTest {

    @Test
    public void testEscapesInString() {
        final String expected = "(&lt;)(&amp;)(&gt;)1234567890";
        final String actual = HtmlEscaper.escapeHtml("(<)(&)(>)1234567890");
        assertEquals(expected, actual);
    }

    @Test
    public void testEscaoesInByteArray() {
        final byte[] expected = "(&lt;)(&amp;)(&gt;)1234567890".getBytes();
        final byte[] actual = HtmlEscaper.escapeHtml("(<)(&)(>)1234567890".getBytes());
        assertArrayEquals(expected, actual);
    }
}
