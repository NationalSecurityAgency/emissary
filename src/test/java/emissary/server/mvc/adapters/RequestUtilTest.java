package emissary.server.mvc.adapters;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.servlet.ServletRequest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestUtilTest extends UnitTest {

    @Test
    void testGetParameter() {
        ServletRequest mockRequest = mock(ServletRequest.class);
        when(mockRequest.getAttribute(anyString())).thenReturn("anAttribute").thenReturn(null).thenReturn(null);
        when(mockRequest.getParameter(anyString())).thenReturn("parameter1").thenReturn(null);

        // attribute takes precedence, so return that if one is available
        assertEquals("anAttribute", RequestUtil.getParameter(mockRequest, "param", "theDefault"));

        // without an attribute, get the parameter value
        assertEquals("parameter1", RequestUtil.getParameter(mockRequest, "param", "theDefault"));

        // no attribute or parameter value returns the default value
        assertEquals("theDefault", RequestUtil.getParameter(mockRequest, "param", "theDefault"));
    }

    @Test
    void testGetArrayStringParameterValues() {
        ServletRequest mockRequest = mock(ServletRequest.class);
        when(mockRequest.getAttribute(anyString())).thenReturn("anAttribute").thenReturn(null).thenReturn(null);
        when(mockRequest.getParameterValues(anyString())).thenReturn(new String[] {"parameter1", "parameter2"}).thenReturn(null);

        // attribute takes precedence, so return that if one is available
        assertTrue(Arrays.equals(new String[] {"anAttribute"}, RequestUtil.getParameterValues(mockRequest, "param")));

        // without an attribute, get the parameter values
        assertTrue(Arrays.equals(new String[] {"parameter1", "parameter2"}, RequestUtil.getParameterValues(mockRequest, "param")));

        // no attribute or parameter value returns an empty string array
        assertTrue(Arrays.equals(new String[] {}, RequestUtil.getParameterValues(mockRequest, "param")));
    }

    @Test
    void testGetStringListParameterValues() {
        ServletRequest mockRequest = mock(ServletRequest.class);
        when(mockRequest.getAttribute(anyString())).thenReturn("anAttribute").thenReturn(null).thenReturn(null);
        when(mockRequest.getParameterValues(anyString())).thenReturn(new String[] {"parameter1", "parameter2"}).thenReturn(null);

        // attribute takes precedence, so return that if one is available
        assertEquals(Collections.singletonList("anAttribute"), RequestUtil.getParameterValuesStringList(mockRequest, "param"));

        // without an attribute, get the parameter values
        assertEquals(Arrays.asList("parameter1", "parameter2"), RequestUtil.getParameterValuesStringList(mockRequest, "param"));

        when(mockRequest.getParameterValues(anyString())).thenReturn(new String[] {}).thenReturn(null);
        // no attribute or empty parameter value returns an empty string list
        assertEquals(Collections.emptyList(), RequestUtil.getParameterValuesStringList(mockRequest, "param"));

        // check when parameter value is null (throws null pointer ex) that an empty list is returned
        assertEquals(Collections.emptyList(), RequestUtil.getParameterValuesStringList(mockRequest, "param"));
    }

    @Test
    void testSanitizeParameter() {
        String test = "this\ris\r\nnot\nfine\n\r";

        assertEquals("this_is__not_fine__", RequestUtil.sanitizeParameter(test));

        assertNull(RequestUtil.sanitizeParameter(null));
    }

    @Test
    void testSanitizeStringArrayParameters() {
        String testOk = "this_is_fine";
        String testBad = "this\ris\r\nnot\nfine\n\r";
        String[] testStrings = new String[] {testOk, null, testBad};

        String[] resultStrings = RequestUtil.sanitizeParameters(testStrings);
        assertEquals(testOk, resultStrings[0]);
        assertNull(resultStrings[1]);
        assertEquals("this_is__not_fine__", resultStrings[2]);

        assertArrayEquals(new String[0], RequestUtil.sanitizeParameters(null));
    }

    @Test
    void testSanitizeStringListParameters() {
        String testOk = "this_is_fine";
        String testBad = "this\ris\r\nnot\nfine\n\r";
        List<String> testStrings = Arrays.asList(testOk, null, testBad);

        List<String> resultStrings = RequestUtil.sanitizeParametersStringList(testStrings);
        assertEquals(testOk, resultStrings.get(0));
        assertNull(resultStrings.get(1));
        assertEquals("this_is__not_fine__", resultStrings.get(2));

        assertTrue(RequestUtil.sanitizeParametersStringList(null).isEmpty());
    }
}
