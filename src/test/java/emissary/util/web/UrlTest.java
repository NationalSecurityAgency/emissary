package emissary.util.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import emissary.test.core.UnitTest;
import org.junit.Before;
import org.junit.Test;

public class UrlTest extends UnitTest {

    @Before
    public void before() {}

    /*
     * The getUrl method should not throw an exception regardless of the situation.
     */
    @Test
    public void testEmptyUrl() {
        assertEquals(0, Url.getUrl(new UrlData()).getResponseCode());
    }

    @Test
    public void testDoUrlWithNull() {
        try {
            Url.doUrl(null);
            fail("did not get expected illegal argument exception.");
        } catch (IllegalArgumentException e) {
            // expected, can be ignored.
        }
    }

    @Test
    public void testDoUrlWithHead() {
        try {
            final UrlData urlData = new UrlData();
            urlData.setTheMethod(Url.HEAD);
            Url.doUrl(urlData);
            fail("did not get expected illegal argument exception.");
        } catch (IllegalArgumentException e) {
            // expected, can be ignored.
        }
    }


    @Test
    public void testDoUrlWithGet() {
        final UrlData urlData = new UrlData();
        urlData.setTheMethod(Url.GET);
        assertEquals(0, Url.doUrl(urlData).getResponseCode());
    }

    @Test
    public void testDoUrlWithPost() {
        final UrlData urlData = new UrlData();
        urlData.setTheMethod(Url.POST);
        assertEquals(0, Url.doUrl(urlData).getResponseCode());
    }
}
