package emissary.util.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.Test;

class HttpPostParametersTest extends UnitTest {

    private final HttpPostParameters hpp = new HttpPostParameters("A", "B");

    @Test
    void testSimpleToString() {
        final String expected = "A=B";
        final String actual = this.hpp.toString();
        assertEquals(expected, actual);
    }

    @Test
    void testLength() {
        assertEquals(3, this.hpp.length());
    }

    @Test
    void testToPostString() {
        assertEquals("A=B", this.hpp.toPostString());
    }

    @Test
    void testToGetString() {
        assertEquals("?A=B", this.hpp.toGetString());
    }

    /*
     * Note that this test has a lambda character which when encoded becomes two bytes. As you can see in this it becomes
     * encoded as %CE %BB.
     */
    @Test
    void testAddParameter() {
        this.hpp.add("λ", "λ");
        assertEquals(17, this.hpp.length());
        assertEquals("A=B&%CE%BB=%CE%BB", this.hpp.toPostString());
        assertEquals("?A=B&%CE%BB=%CE%BB", this.hpp.toGetString());
        assertEquals("A=B&λ=λ", this.hpp.toString());
    }

    @Test
    void testNullFirstParameter() {
        final HttpPostParameters object = new HttpPostParameters(null, "B");
        final String expected = "null=B";
        final String actual = object.toString();
        assertEquals(expected, actual);
    }

    @Test
    void testNullSecondParameter() {
        final HttpPostParameters object = new HttpPostParameters(null, null);
        final String expected = "null=";
        final String actual = object.toString();
        assertEquals(expected, actual);
    }
}
