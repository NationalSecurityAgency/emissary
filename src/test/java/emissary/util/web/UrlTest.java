package emissary.util.web;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlTest extends UnitTest {

    @BeforeEach
    public void before() {}

    /*
     * The getUrl method should not throw an exception regardless of the situation.
     */
    @Test
    void testEmptyUrl() {
        assertEquals(0, Url.getUrl(new UrlData()).getResponseCode());
    }

    @Test
    void testDoUrlWithNull() {
        assertThrows(IllegalArgumentException.class, () -> Url.doUrl(null));
    }

    @Test
    void testDoUrlWithHead() {
        final UrlData urlData = new UrlData();
        urlData.setTheMethod(Url.HEAD);
        assertThrows(IllegalArgumentException.class, () -> Url.doUrl(urlData));
    }

    @Test
    void testDoUrlWithGet() {
        final UrlData urlData = new UrlData();
        urlData.setTheMethod(Url.GET);
        assertEquals(0, Url.doUrl(urlData).getResponseCode());
    }

    @Test
    void testDoUrlWithPost() {
        final UrlData urlData = new UrlData();
        urlData.setTheMethod(Url.POST);
        assertEquals(0, Url.doUrl(urlData).getResponseCode());
    }
}
