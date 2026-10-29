package emissary.server.mvc;

import emissary.server.mvc.NavAction.EmissaryNav;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavActionTest {

    EmissaryNav nav;
    Map<String, String> navItems;

    @BeforeEach
    void setUp() {
        nav = new EmissaryNav();
        navItems = new LinkedHashMap<>();

        // valid
        navItems.put("Relative path", "/path/to/resource");
        navItems.put("Path to action", "/testing.action");
        navItems.put("Path with safe dots", "/static/js/app.v1.js");

        // invalid
        navItems.put("HTTP URL", "http://testing1.com");
        navItems.put("HTTPS URL", "https://testing2.com");
        navItems.put("FTP scheme", "ftp://testing");
        navItems.put("http:/ single-slash", "http:/testing.com/");
        navItems.put("https:/ single-slash", "https:/testing.com/");
        navItems.put("Multiple Schemes", "https:http://testing.com");
        navItems.put("Invalid scheme", "htp:/testing");

        navItems.put("Protocol-relative (Host)", "//testing.com");
        navItems.put("Protocol-relative (IP)", "//169.254.169.254/latest/meta-data/");
        navItems.put("Octal IP bypass", "//0251.0376.0251.0376/");
        navItems.put("Decimal IP bypass", "//2850611454/");
        navItems.put("Hex IP bypass", "//0xA9FEA9FE/");
        navItems.put("Localhost IPv6", "//[::1]");
        navItems.put("Embedded credentials", "//user:pass@testing.com/path");

        navItems.put("Backslash/Slash mix", "/\\testing.com");
        navItems.put("Triple slash host", "///testing.com");
        navItems.put("Triple slash metadata", "///169.254.169.254");
        navItems.put("Dot-segment self-reference", "/./testing.com");
        navItems.put("Path Traversal", "/app/../admin");
        navItems.put("Unicode slash bypass", "\uFF0F\uFF0Ftesting.com");
        navItems.put("At-Symbol Authority", "/@testing.com");
        navItems.put("Square Bracket bypass", "/[google.com]");
        navItems.put("Backslash", "\\testing\\com");
        navItems.put("Double backslash", "\\\\testing\\com");

        navItems.put("URL-encoded relative", "%2F%2Ftesting.action");
        navItems.put("Double encoded slash", "%252f%252ftesting.com");
        navItems.put("Encoded second slash", "/%2f/testing.com");
        navItems.put("Encoded Backslash", "/%5ctesting.com");
        navItems.put("Encoded Traversal", "/%2e%2e%2fadmin");
        navItems.put("Nested URL in param", "/view?url=http://testing.com");
        navItems.put("URL-encoded javascript", "%6aavascript:alert(1)");

        navItems.put("Javascript XSS payload", "javascript:alert(document.cookie)");
        navItems.put("Data URI", "data:text/html,<script>alert(1)</script>");
        navItems.put("CRLF injection in path", "/legit\r\nLocation: https://testing.com");
        navItems.put("Newline injection", "/path%0a//testing.com");
        navItems.put("Null byte obfuscation", "/%00//169.254.169.254");
        navItems.put("Null byte path", "/dashboard%00//testing.com");

        navItems.put("Leading whitespace", " /dashboard");
        navItems.put("Tab in scheme", "java\tscript:alert(1)");
        navItems.put("Non-breaking space", "/%A0/testing.com");
        navItems.put("Form Feed", "/\f/testing.com");
        navItems.put("Zero Width Space", "/test\u200B/path");
        navItems.put("Space in URL", "http:/www. testing.com");

        navItems.put("Empty", "");
        navItems.put("Blank", "      ");
        navItems.put("Null", null);
        navItems.put("Bare String", "testing");
        navItems.put("Colon in Path", "/static/css:styles");
    }

    @Test
    void navItems() {
        nav.setNavItems(navItems);
        assertEquals(3, nav.getNavItems().size());
        assertEquals("Relative path", nav.getNavItems().get(0).getDisplay());
        // TODO: Revert this test to "/path/to/resource" NavAction.java:91 is removed
        assertEquals("./path/to/resource", nav.getNavItems().get(0).getLink());
        assertEquals("Path to action", nav.getNavItems().get(1).getDisplay());
        assertEquals("./testing.action", nav.getNavItems().get(1).getLink());
        assertEquals("Path with safe dots", nav.getNavItems().get(2).getDisplay());
        assertEquals("./static/js/app.v1.js", nav.getNavItems().get(2).getLink());
    }

    @Test
    void navItemsEmpty() {
        nav.setNavItems(Map.of());
        assertTrue(CollectionUtils.isEmpty(nav.getNavItems()));
    }
}
