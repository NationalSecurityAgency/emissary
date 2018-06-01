package emissary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class LineTokenizerTest extends UnitTest {

    private static String W = "Президент Буш";

    @Test
    public void testCharset() {
        LineTokenizer lt = new LineTokenizer((W + "\n").getBytes(), Charset.forName("UTF-8"));
        assertEquals("UTF-8 passed through clean", W, lt.nextToken());
    }

    @Test
    public void testStringCharset() {
        LineTokenizer lt = new LineTokenizer((W + "\n").getBytes(), "UTF-8");
        assertEquals("UTF-8 passed through clean", W, lt.nextToken());
    }

    @Test
    public void testParsing() {
        LineTokenizer lt = new LineTokenizer("ABC\nDEF\r\nGHI\n\nJKL\r\n\r\nMNO".getBytes());
        assertEquals("Line counting", 7, lt.countTokens());
        assertTrue("Token setup", lt.hasMoreTokens());
        assertEquals("Line parsing with LF", "ABC", lt.nextToken());
        assertEquals("Line parsing with CRLF", "DEF\r", lt.nextToken());
        assertEquals("Line parsing before double LF", "GHI", lt.nextToken());
        assertEquals("Blank line with LF", "", lt.nextToken());
        assertEquals("Line pargin before double CRLF", "JKL\r", lt.nextToken());
        assertEquals("Blank line with CRLF", "\r", lt.nextToken());
        assertEquals("Trailing portion without LF", "MNO", lt.nextToken());
    }

    @Test
    public void testParsingWithSpecifiedDelimiter() {
        LineTokenizer lt = new LineTokenizer("ABC\r\nDEF\r\nGHI\n\nJKL\r\n\r\nMNO".getBytes(), (byte) '\r', Charset.forName("UTF-8"));
        assertEquals("Line counting", 5, lt.countTokens());
        assertTrue("Token setup", lt.hasMoreTokens());
        assertEquals("Line parsing with LF", "ABC", lt.nextToken());
        assertEquals("Line parsing with CRLF", "\nDEF", lt.nextToken());
    }

    @Test
    public void testParsingWithSpecifiedDelimiterAndStringCharset() {
        LineTokenizer lt = new LineTokenizer("ABC\r\nDEF\r\nGHI\n\nJKL\r\n\r\nMNO".getBytes(), (byte) '\r', "UTF-8");
        assertEquals("Line counting", 5, lt.countTokens());
        assertTrue("Token setup", lt.hasMoreTokens());
        assertEquals("Line parsing with LF", "ABC", lt.nextToken());
        assertEquals("Line parsing with CRLF", "\nDEF", lt.nextToken());
    }

    @Test
    public void testPushback() {
        LineTokenizer lt = new LineTokenizer("ABC\nDEF\nGHI\nJKL\n".getBytes());
        assertEquals("Line counting", 4, lt.countTokens());
        assertEquals("First token", "ABC", lt.nextToken());
        lt.pushBack();
        assertEquals("Line counting after push", 4, lt.countTokens());
        assertEquals("First token after push", "ABC", lt.nextToken());

        assertEquals("Second token", "DEF", lt.nextToken());
        lt.pushBack();
        assertEquals("Second token after push", "DEF", lt.nextToken());
        lt.pushBack();
        assertEquals("Second token after push", "DEF", lt.nextToken());
    }

}
