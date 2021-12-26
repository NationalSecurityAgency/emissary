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
        assertEquals("Index should be at the beginning of the string", 0, lt.index);
        assertEquals("Line parsing with LF", "ABC", lt.nextToken());
        assertEquals("Index should be at the index of 'D'", 4, lt.index);
        assertEquals("Line parsing with CRLF", "DEF\r", lt.nextToken());
        assertEquals("Index should be at the index of 'G'", 9, lt.index);
        assertEquals("Line parsing before double LF", "GHI", lt.nextToken());
        assertEquals("Index should be at the index of the '\n' before 'JKL'", 13, lt.index);
        assertEquals("Blank line with LF", "", lt.nextToken());
        assertEquals("Index should be at the index of 'J'", 14, lt.index);
        assertEquals("Line pargin before double CRLF", "JKL\r", lt.nextToken());
        assertEquals("Index should be at the index of '\r' before '\nMNO'", 19, lt.index);
        assertEquals("Blank line with CRLF", "\r", lt.nextToken());
        assertEquals("Index should at the index of 'M'", 21, lt.index);
        assertEquals("Trailing portion without LF", "MNO", lt.nextToken());
        assertEquals("Index should be at the end of the string", 24, lt.index);
        assertEquals("No remaining tokens", null, lt.nextToken());
        assertEquals("Index should not have changed", 24, lt.index);
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
        assertEquals("Index should be at the index of 'D'", 4, lt.index);
        lt.pushBack();
        assertEquals("Index should be at the start of the string", 0, lt.index);
        assertEquals("Line counting after push", 4, lt.countTokens());
        assertEquals("First token after push", "ABC", lt.nextToken());
        assertEquals("Index should be at the index of 'D'", 4, lt.index);

        assertEquals("Second token", "DEF", lt.nextToken());
        assertEquals("Index should be at the index of 'G'", 8, lt.index);
        lt.pushBack();
        assertEquals("Index should be at the index of 'D'", 4, lt.index);
        assertEquals("Second token after push", "DEF", lt.nextToken());
        assertEquals("Index should be at the index of 'G'", 8, lt.index);
        lt.pushBack();
        assertEquals("Index should be at the index of 'D'", 4, lt.index);
        assertEquals("Second token after push", "DEF", lt.nextToken());
        assertEquals("Index should be at the index of 'G'", 8, lt.index);

        lt.nextToken();
        assertEquals("End of the string", "JKL", lt.nextToken());
        assertEquals("Index should be at the end of the string", 15, lt.index);

        lt.pushBack();
        assertEquals("Index should at the index of 'J'", 12, lt.index);
        assertEquals("End of the string", "JKL", lt.nextToken());
        assertEquals("Index should be at the end of the string", 15, lt.index);
        assertEquals("No remaining tokens", null, lt.nextToken());
        assertEquals("Index should not have changed", 15, lt.index);
    }

}
