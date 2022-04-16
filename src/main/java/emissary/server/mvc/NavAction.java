package emissary.server.mvc;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;

import org.glassfish.jersey.server.mvc.Template;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("")
// context is emissary
public class NavAction {

    EmissaryNav nav;

    public NavAction() throws IOException {
        Configurator config = ConfigUtil.getConfigInfo(this.getClass());
        nav = new EmissaryNav();
        nav.setAppName(config.findStringEntry("APP_NAME", "Emissary"));
        nav.setAppVersion(config.findStringEntry("APP_VERSION", ""));
        nav.setNavItems(config.findStringMatchMap("NAV_ITEM_", true, true));
        nav.setNavButtons(config.findStringMatchMap("NAV_BUTTON_", true, true));
    }

    @GET
    @Path("/Nav.action")
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/nav")
    public EmissaryNav nav() {
        return nav;
    }

    public static class EmissaryNav {

        String appName;
        String appVersion;
        List<NavItem> navItems;
        List<NavItem> navButtons;

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public String getAppVersion() {
            return appVersion;
        }

        public void setAppVersion(String appVersion) {
            this.appVersion = appVersion;
        }

        public List<NavItem> getNavItems() {
            return navItems;
        }

        public void setNavItems(Map<String, String> navItems) {
            this.navItems = convert(navItems);
        }

        public List<NavItem> getNavButtons() {
            return navButtons;
        }

        public void setNavButtons(Map<String, String> navButtons) {
            this.navButtons = convert(navButtons);
        }

        protected static List<NavItem> convert(Map<String, String> map) {
            return map.entrySet().stream().map(e -> new NavItem(e.getKey(), e.getValue())).collect(Collectors.toList());
        }

        public static class NavItem {

            String display;
            String link;

            public NavItem(String display, String link) {
                this.display = display;
                this.link = link;
            }

            public String getDisplay() {
                return display;
            }

            public void setDisplay(String display) {
                this.display = display;
            }

            public String getLink() {
                return link;
            }

            public void setLink(String link) {
                this.link = link;
            }
        }
    }


}
