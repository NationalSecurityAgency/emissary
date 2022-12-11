package emissary.util;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineTokenizerTest extends UnitTest {

    private static final String W = "Президент Буш";

    @Test
    void testCharset() {
        LineTokenizer lt = new LineTokenizer((W + "\n").getBytes(), StandardCharsets.UTF_8);
        assertEquals(W, lt.nextToken(), "UTF-8 passed through clean");
    }

    @Test
    void testStringCharset() {
        LineTokenizer lt = new LineTokenizer((W + "\n").getBytes(), StandardCharsets.UTF_8);
        assertEquals(W, lt.nextToken(), "UTF-8 passed through clean");
    }

    @Test
    void testParsing() {
        LineTokenizer lt = new LineTokenizer("ABC\nDEF\r\nGHI\n\nJKL\r\n\r\nMNO".getBytes());
        assertEquals(7, lt.countTokens(), "Line counting");
        assertTrue(lt.hasMoreTokens(), "Token setup");
        assertEquals(0, lt.index, "Index should be at the beginning of the string");
        assertEquals("ABC", lt.nextToken(), "Line parsing with LF");
        assertEquals(4, lt.index, "Index should be at the index of 'D'");
        assertEquals("DEF\r", lt.nextToken(), "Line parsing with CRLF");
        assertEquals(9, lt.index, "Index should be at the index of 'G'");
        assertEquals("GHI", lt.nextToken(), "Line parsing before double LF");
        assertEquals(13, lt.index, "Index should be at the index of the '\n' before 'JKL'");
        assertEquals("", lt.nextToken(), "Blank line with LF");
        assertEquals(14, lt.index, "Index should be at the index of 'J'");
        assertEquals("JKL\r", lt.nextToken(), "Line pargin before double CRLF");
        assertEquals(19, lt.index, "Index should be at the index of '\r' before '\nMNO'");
        assertEquals("\r", lt.nextToken(), "Blank line with CRLF");
        assertEquals(21, lt.index, "Index should at the index of 'M'");
        assertEquals("MNO", lt.nextToken(), "Trailing portion without LF");
        assertEquals(24, lt.index, "Index should be at the end of the string");
        assertNull(lt.nextToken(), "No remaining tokens");
        assertEquals(24, lt.index, "Index should not have changed");
    }

    @Test
    void testParsingWithSpecifiedDelimiter() {
        LineTokenizer lt = new LineTokenizer("ABC\r\nDEF\r\nGHI\n\nJKL\r\n\r\nMNO".getBytes(), (byte) '\r', StandardCharsets.UTF_8);
        assertEquals(5, lt.countTokens(), "Line counting");
        assertTrue(lt.hasMoreTokens(), "Token setup");
        assertEquals("ABC", lt.nextToken(), "Line parsing with LF");
        assertEquals("\nDEF", lt.nextToken(), "Line parsing with CRLF");
    }

    @Test
    void testParsingWithSpecifiedDelimiterAndStringCharset() {
        LineTokenizer lt = new LineTokenizer("ABC\r\nDEF\r\nGHI\n\nJKL\r\n\r\nMNO".getBytes(), (byte) '\r', "UTF-8");
        assertEquals(5, lt.countTokens(), "Line counting");
        assertTrue(lt.hasMoreTokens(), "Token setup");
        assertEquals("ABC", lt.nextToken(), "Line parsing with LF");
        assertEquals("\nDEF", lt.nextToken(), "Line parsing with CRLF");
    }

    @Test
    void testPushback() {
        LineTokenizer lt = new LineTokenizer("ABC\nDEF\nGHI\nJKL\n".getBytes());
        assertEquals(4, lt.countTokens(), "Line counting");
        assertEquals("ABC", lt.nextToken(), "First token");
        assertEquals(4, lt.index, "Index should be at the index of 'D'");
        lt.pushBack();
        assertEquals(0, lt.index, "Index should be at the start of the string");
        assertEquals(4, lt.countTokens(), "Line counting after push");
        assertEquals("ABC", lt.nextToken(), "First token after push");
        assertEquals(4, lt.index, "Index should be at the index of 'D'");

        assertEquals("DEF", lt.nextToken(), "Second token");
        assertEquals(8, lt.index, "Index should be at the index of 'G'");
        lt.pushBack();
        assertEquals(4, lt.index, "Index should be at the index of 'D'");
        assertEquals("DEF", lt.nextToken(), "Second token after push");
        assertEquals(8, lt.index, "Index should be at the index of 'G'");
        lt.pushBack();
        assertEquals(4, lt.index, "Index should be at the index of 'D'");
        assertEquals("DEF", lt.nextToken(), "Second token after push");
        assertEquals(8, lt.index, "Index should be at the index of 'G'");

        lt.nextToken();
        assertEquals("JKL", lt.nextToken(), "End of the string");
        assertEquals(15, lt.index, "Index should be at the end of the string");

        lt.pushBack();
        assertEquals(12, lt.index, "Index should at the index of 'J'");
        assertEquals("JKL", lt.nextToken(), "End of the string");
        assertEquals(15, lt.index, "Index should be at the end of the string");
        assertNull(lt.nextToken(), "No remaining tokens");
        assertEquals(15, lt.index, "Index should not have changed");
    }

}
