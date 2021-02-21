package emissary.server.api;

import java.io.IOException;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;

@Path("")
// context is /api
public class Nav {

    EmissaryNav nav;

    public Nav() throws IOException {
        Configurator config = ConfigUtil.getConfigInfo(this.getClass());
        nav = new EmissaryNav();
        nav.setAppName(config.findStringEntry("APP_NAME", "Emissary"));
        nav.setAppVersion(config.findStringEntry("APP_VERSION", ""));
        nav.setNavItems(config.findStringMatchMap("NAV_ITEM_", true, true));
        nav.setNavButtons(config.findStringMatchMap("NAV_BUTTON_", true, true));
    }

    @GET
    @Path("/nav")
    @Produces(MediaType.APPLICATION_JSON)
    public Response nav() {
        return Response.ok().entity(nav).build();
    }

    public class EmissaryNav {

        String appName;
        String appVersion;
        Map<String, String> navItems;
        Map<String, String> navButtons;

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public Map<String, String> getNavItems() {
            return navItems;
        }

        public String getAppVersion() {
            return appVersion;
        }

        public void setAppVersion(String appVersion) {
            this.appVersion = appVersion;
        }

        public void setNavItems(Map<String, String> navItems) {
            this.navItems = navItems;
        }

        public Map<String, String> getNavButtons() {
            return navButtons;
        }

        public void setNavButtons(Map<String, String> navButtons) {
            this.navButtons = navButtons;
        }
    }

}
