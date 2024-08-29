package emissary.util.search;

import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByteTokenizerTest {

    @Test
    void testEncodingConstructor() throws Exception {

        byte[] testBytes = "these are test bytes".getBytes(StandardCharsets.UTF_8);
        ByteTokenizer byteTokenizer = new ByteTokenizer(testBytes, 0, 12, " ", StandardCharsets.UTF_8.name());
        assertEquals(3, byteTokenizer.countTokens());

        assertThrows(UnsupportedEncodingException.class, () -> new ByteTokenizer(testBytes, 0, 12, " ", null));

        Exception exception = assertThrows(UnsupportedEncodingException.class, () -> new ByteTokenizer(testBytes, 0, 12, " ", "fake-encoding"));
        assertEquals("java.io.UnsupportedEncodingException: java.nio.charset.UnsupportedCharsetException: fake-encoding", exception.toString());
    }
}
