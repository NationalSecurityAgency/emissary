package emissary.util.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.Test;

class HtmlEscaperTest extends UnitTest {

    @Test
    void testEscapesInString() {
        final String expected = "(&lt;)(&amp;)(&gt;)1234567890";
        final String actual = HtmlEscaper.escapeHtml("(<)(&)(>)1234567890");
        assertEquals(expected, actual);
    }

    @Test
    void testEscaoesInByteArray() {
        final byte[] expected = "(&lt;)(&amp;)(&gt;)1234567890".getBytes(UTF_8);
        final byte[] actual = HtmlEscaper.escapeHtml("(<)(&)(>)1234567890".getBytes(UTF_8));
        assertArrayEquals(expected, actual);
    }
}
