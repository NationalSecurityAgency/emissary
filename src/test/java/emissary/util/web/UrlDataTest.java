package emissary.util.web;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        final List<UrlRequestProperty> properties = List.of(property);
        urlData.setProps(properties);
        assertEquals(1, urlData.getNumberOfProperties());

        urlData.setReferer("referer");
        assertEquals("referer", urlData.getReferer());

        urlData.setResponseCode(49);
        assertEquals(49, urlData.getResponseCode());

        urlData.setTheContent("1234".getBytes());
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
        final List<UrlRequestProperty> properties = List.of();
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
        List<UrlRequestProperty> properties = List.of(property);

        final UrlData urlData = new UrlData("http://www.example.com", new byte[0], 0, properties);
        // nullify the properties array to show that the UrlData object holds its
        // own list.
        properties = null;
        assertEquals("VALUE", urlData.getProps().get(0).getValue());
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
        urlData.addProps(new ArrayList<>());
        assertEquals(0, urlData.getNumberOfProperties());
    }

    @Test
    void testAddPropertyListToEmptyList() {
        final UrlRequestProperty property = new UrlRequestProperty("KEY", "VALUE");
        final List<UrlRequestProperty> properties = List.of(property, property);
        final UrlData urlData = new UrlData();
        urlData.addProps(properties);
        assertEquals(2, urlData.getNumberOfProperties());
    }

    @Test
    void testAddPropertyList() {
        final UrlRequestProperty property = new UrlRequestProperty("KEY", "VALUE");
        final List<UrlRequestProperty> properties = List.of(property, property);
        final UrlData urlData = new UrlData();
        urlData.setProps(properties);
        urlData.addProps(properties);
        assertEquals(4, urlData.getNumberOfProperties());
    }
}
