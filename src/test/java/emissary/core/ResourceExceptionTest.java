package emissary.core;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceExceptionTest extends UnitTest {
    @Test
    void testConstructors() {
        ResourceException r = new ResourceException("Blah");
        assertEquals("Blah", r.getMessage(), "Message should be used from constructor");

        r = new ResourceException(new Throwable("Blah"));
        assertEquals("Exception: Blah", r.getMessage(), "Message from throwable should be used");

        r = new ResourceException("Blah", new Throwable("Blah"));
        assertEquals("Blah", r.getMessage(), "Message from contstructo should be used");

    }
}
