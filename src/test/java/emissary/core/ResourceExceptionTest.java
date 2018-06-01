package emissary.core;

import static org.junit.Assert.assertEquals;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class ResourceExceptionTest extends UnitTest {
    @Test
    public void testConstructors() {
        ResourceException r = new ResourceException("Blah");
        assertEquals("Message should be used from constructor", "Blah", r.getMessage());

        r = new ResourceException(new Throwable("Blah"));
        assertEquals("Message from throwable should be used", "Exception: Blah", r.getMessage());

        r = new ResourceException("Blah", new Throwable("Blah"));
        assertEquals("Message from contstructo should be used", "Blah", r.getMessage());

    }
}
