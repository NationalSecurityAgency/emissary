package emissary.server.util;

import emissary.config.ConfigUtil;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseResourcePathUtilTest extends UnitTest {

    @Test
    void testValidBaseResourcePathRegexMatchesExpectedRoutes() throws Exception {
        assertTrue(BaseResourcePathUtil.isValidBaseResourcePath("/route"), "Leading slash route should match");
        assertTrue(BaseResourcePathUtil.isValidBaseResourcePath("/myroute/route"), "Nested route should match");
        assertTrue(BaseResourcePathUtil.isValidBaseResourcePath("/my_route-route2"), "Underscores and hyphens should match");
        assertTrue(BaseResourcePathUtil.isValidBaseResourcePath("/myroute/route2/route3/route4/route5"), "Up to 5 nested routes should match");
        assertTrue(BaseResourcePathUtil.isValidBaseResourcePath("/route~route"), "Tilde character should match");
    }

    @Test
    void testInvalidBaseResourcePathRegexRejectsBadRoutes() throws Exception {
        assertFalse(BaseResourcePathUtil.isValidBaseResourcePath("route"), "Route without leading slash should not match");
        assertFalse(BaseResourcePathUtil.isValidBaseResourcePath("./route"), "Relative route should not match");
        assertFalse(BaseResourcePathUtil.isValidBaseResourcePath("http://www.myroute.com"), "Absolute URL should not match");
        assertFalse(BaseResourcePathUtil.isValidBaseResourcePath("myroute!"), "Invalid character should not match");
        assertFalse(BaseResourcePathUtil.isValidBaseResourcePath("/myroute/"), "Ending slash route should not match");
        assertFalse(BaseResourcePathUtil.isValidBaseResourcePath("/myroute/route2/route3/route4/route5/route6"),
                "More than 5 nested routes should not match");
    }

    @Test
    void testFallbackToDefaultBaseResourcePathOnConfigLoadFailure() throws Exception {
        try (MockedStatic<ConfigUtil> configUtil = Mockito.mockStatic(ConfigUtil.class)) {
            configUtil.when(() -> ConfigUtil.getConfigInfo(BaseResourcePathUtil.class)).thenThrow(new IOException("unable to load"));

            Method initMethod = BaseResourcePathUtil.class.getDeclaredMethod("initBaseResourcePath");
            initMethod.setAccessible(true);

            String path = (String) initMethod.invoke(null);
            assertEquals("", path, "If configuration cannot be loaded, fallback should be the empty base path");
        }
    }


}
