package emissary.core;

import static org.junit.Assert.assertEquals;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class EmissaryExceptionTest extends UnitTest {
    @Test
    public void testConstructors() {
        EmissaryException r = new EmissaryException("Blah");
        assertEquals("Message should be used from constructor", "Blah", r.getMessage());

        r = new EmissaryException(new Throwable("Blah"));
        assertEquals("Message from throwable should be used", "Exception: Blah", r.getMessage());

        r = new EmissaryException("Blah", new Throwable("Blah"));
        assertEquals("Message from contstructo should be used", "Blah", r.getMessage());

    }
}
