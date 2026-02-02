package emissary.server.mvc;

import emissary.server.mvc.NavAction.EmissaryNav;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavActionTest {

    @Test
    void navItems() {

        var navItems = new LinkedHashMap<String, String>();
        navItems.put("Item1", "/path/to/resource");
        navItems.put("Item2", "http://testing1.com");
        navItems.put("Item3", "https://testing2.com");
        navItems.put("Item4", "javascript:alert(document.cookie)");
        navItems.put("Item5", "ftp://testing");

        EmissaryNav nav = new EmissaryNav();

        // insert empty map
        nav.setNavItems(Map.of());
        assertTrue(CollectionUtils.isEmpty(nav.getNavItems()));

        nav.setNavItems(navItems);
        assertEquals(3, nav.getNavItems().size());
        assertEquals("Item1", nav.getNavItems().get(0).getDisplay());
        assertEquals("/path/to/resource", nav.getNavItems().get(0).getLink());
        assertEquals("Item2", nav.getNavItems().get(1).getDisplay());
        assertEquals("http://testing1.com", nav.getNavItems().get(1).getLink());
        assertEquals("Item3", nav.getNavItems().get(2).getDisplay());
        assertEquals("https://testing2.com", nav.getNavItems().get(2).getLink());
    }

}
