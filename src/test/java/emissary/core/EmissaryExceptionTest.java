package emissary.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emissary.test.core.UnitTest;
import org.junit.jupiter.api.Test;

class EmissaryExceptionTest extends UnitTest {
    @Test
    void testConstructors() {
        EmissaryException r = new EmissaryException("Blah");
        assertEquals("Blah", r.getMessage(), "Message should be used from constructor");

        r = new EmissaryException(new Throwable("Blah"));
        assertEquals("Exception: Blah", r.getMessage(), "Message from throwable should be used");

        r = new EmissaryException("Blah", new Throwable("Blah"));
        assertEquals("Blah", r.getMessage(), "Message from contstructo should be used");

    }
}
