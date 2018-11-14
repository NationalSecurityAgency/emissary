package emissary.util.web;

import static org.junit.Assert.assertEquals;

import emissary.test.core.UnitTest;
import org.junit.Before;
import org.junit.Test;

public class HttpPostParametersTest extends UnitTest {

    private HttpPostParameters hpp = new HttpPostParameters("A", "B");

    @Before
    public void before() {

    }

    @Test
    public void testSimpleToString() {
        final String expected = "A=B";
        final String actual = this.hpp.toString();
        assertEquals(expected, actual);
    }

    @Test
    public void testLength() {
        assertEquals(3, this.hpp.length());
    }

    @Test
    public void testToPostString() {
        assertEquals("A=B", this.hpp.toPostString());
    }

    @Test
    public void testToGetString() {
        assertEquals("?A=B", this.hpp.toGetString());
    }

    /*
     * Note that this test has a lambda character which when encoded becomes two bytes. As you can see in this it becomes
     * encoded as %CE %BB.
     */
    @Test
    public void testAddParameter() {
        this.hpp.add("λ", "λ");
        assertEquals(17, this.hpp.length());
        assertEquals("A=B&%CE%BB=%CE%BB", this.hpp.toPostString());
        assertEquals("?A=B&%CE%BB=%CE%BB", this.hpp.toGetString());
        assertEquals("A=B&λ=λ", this.hpp.toString());
    }

    @Test
    public void testNullFirstParameter() {
        final HttpPostParameters object = new HttpPostParameters(null, "B");
        final String expected = "null=B";
        final String actual = object.toString();
        assertEquals(expected, actual);
    }

    @Test
    public void testNullSecondParameter() {
        final HttpPostParameters object = new HttpPostParameters(null, null);
        final String expected = "null=";
        final String actual = object.toString();
        assertEquals(expected, actual);
    }
}
