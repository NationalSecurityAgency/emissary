package emissary.util.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.Test;

class UrlDataTest extends UnitTest {

    @Test
    void testDefaultAttributeValues() {
        final UrlData urlData = new UrlData();
        assertNull(urlData.getPassword());
        assertEquals(0, urlData.getNumberOfProperties());
        assertNull(urlData.getReferer());
        assertEquals(0, urlData.getResponseCode());
        assertEquals(0, urlData.getContentLength());
        assertEquals(Url.UNINITIALIZED, urlData.getTheMethod());
        assertNull(urlData.getTheUrl());
        assertNull(urlData.getUserAgent());
        assertNull(urlData.getUserName());
    }

    @Test
    void testSetters() {
        final UrlData urlData = new UrlData();
        urlData.setPassword("password");
        assertEquals("password", urlData.getPassword());

        final UrlRequestProperty property = new UrlRequestProperty("KEY", "VALUE");
        final UrlRequestProperty[] properties = new UrlRequestProperty[1];
        properties[0] = property;
        urlData.setProps(properties);
        assertEquals(1, urlData.getNumberOfProperties());

        urlData.setReferer("referer");
        assertEquals("referer", urlData.getReferer());

        urlData.setResponseCode(49);
        assertEquals(49, urlData.getResponseCode());

        urlData.setTheContent("1234".getBytes(UTF_8));
        assertEquals(4, urlData.getContentLength());

        urlData.setTheMethod(Url.POST);
        assertEquals(Url.POST, urlData.getTheMethod());

        urlData.setTheUrl("http://test.example.com");
        assertEquals("http://test.example.com", urlData.getTheUrl());

        urlData.setUserAgent("userAgent");
        assertEquals("userAgent", urlData.getUserAgent());

        urlData.setUserName("userName");
        assertEquals("userName", urlData.getUserName());
    }

    @Test
    void testConstructorWithJustURL() {
        final UrlData urlData = new UrlData("http://www.example.com");
        assertNull(urlData.getPassword());
        assertEquals(0, urlData.getNumberOfProperties());
        assertNull(urlData.getReferer());
        assertEquals(0, urlData.getResponseCode());
        assertEquals(0, urlData.getContentLength());
        assertEquals(Url.UNINITIALIZED, urlData.getTheMethod());
        assertEquals("http://www.example.com", urlData.getTheUrl());
        assertNull(urlData.getUserAgent());
        assertNull(urlData.getUserName());
    }

    @Test
    void testConstructor() {
        final byte[] theContent = new byte[0];
        final int responseCode = 0;
        final UrlRequestProperty[] properties = new UrlRequestProperty[0];
        final UrlData urlData = new UrlData("http://www.example.com", theContent, responseCode, properties);
        assertNull(urlData.getPassword());
        assertEquals(0, urlData.getNumberOfProperties());
        assertNull(urlData.getReferer());
        assertEquals(0, urlData.getResponseCode());
        assertEquals(0, urlData.getContentLength());
        assertEquals(Url.GET, urlData.getTheMethod());
        assertEquals("http://www.example.com", urlData.getTheUrl());
        assertNull(urlData.getUserAgent());
        assertNull(urlData.getUserName());
    }

    @Test
    void testConstructorCopiesRequestProperties() {
        final UrlRequestProperty property = new UrlRequestProperty("KEY", "VALUE");
        UrlRequestProperty[] properties = new UrlRequestProperty[1];
        properties[0] = property;

        final UrlData urlData = new UrlData("http://www.example.com", new byte[0], 0, properties);
        // nullify the properties array to show that the UrlData object holds its
        // own list.
        properties = null;
        assertEquals("VALUE", urlData.getProps()[0].getValue());
    }

    @Test
    void testAddNullProperty() {
        final UrlData urlData = new UrlData();
        urlData.addProp(null);
        assertEquals(0, urlData.getNumberOfProperties());
    }

    @Test
    void testAddProperty() {
        final UrlData urlData = new UrlData();
        urlData.addProp(new UrlRequestProperty("KEY", "VALUE"));
        assertEquals(1, urlData.getNumberOfProperties());
    }

    @Test
    void testAddNullPropertyList() {
        final UrlData urlData = new UrlData();
        urlData.addProps(null);
        assertEquals(0, urlData.getNumberOfProperties());
    }

    @Test
    void testAddZeroLengthPropertyList() {
        final UrlData urlData = new UrlData();
        urlData.addProps(new UrlRequestProperty[0]);
        assertEquals(0, urlData.getNumberOfProperties());
    }

    @Test
    void testAddPropertyListToEmptyList() {
        final UrlRequestProperty property = new UrlRequestProperty("KEY", "VALUE");
        final UrlRequestProperty[] properties = new UrlRequestProperty[2];
        properties[0] = property;
        properties[1] = property;
        final UrlData urlData = new UrlData();
        urlData.addProps(properties);
        assertEquals(2, urlData.getNumberOfProperties());
    }

    @Test
    void testAddPropertyList() {
        final UrlRequestProperty property = new UrlRequestProperty("KEY", "VALUE");
        final UrlRequestProperty[] properties = new UrlRequestProperty[2];
        properties[0] = property;
        properties[1] = property;
        final UrlData urlData = new UrlData();
        urlData.setProps(properties);
        urlData.addProps(properties);
        assertEquals(4, urlData.getNumberOfProperties());
    }
}
