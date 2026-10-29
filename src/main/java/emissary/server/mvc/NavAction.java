package emissary.server.mvc;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;

import com.google.common.base.CharMatcher;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.server.mvc.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("")
// context is emissary
public class NavAction {

    private static final Logger logger = LoggerFactory.getLogger(NavAction.class);

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

        private static final CharMatcher SAFE_LINK_CHARS = CharMatcher.inRange('a', 'z')
                .or(CharMatcher.inRange('A', 'Z'))
                .or(CharMatcher.inRange('0', '9'))
                .or(CharMatcher.anyOf("/.=?&-_"));

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
            return map.entrySet().stream()
                    .filter(e -> isValidLink(e.getValue()))
                    // TODO: Remove the following map once all necessary integrations are complete, and backwards compatibility is no longer
                    // required.
                    .map(e -> Map.entry(e.getKey(), e.getValue().startsWith("/") ? "." + e.getValue() : e.getValue()))
                    .map(e -> new NavItem(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
        }

        protected static boolean isValidLink(String link) {
            if (link == null || link.isBlank()) {
                logInvalidLink(link);
                return false;
            }

            try {
                var decoded = link.indexOf('%') == -1 ? link : URLDecoder.decode(link, StandardCharsets.UTF_8);

                // Check basic character safety and obvious bad patterns first
                if (decoded.isEmpty() || decoded.contains("//") || decoded.contains(":/") || !SAFE_LINK_CHARS.matchesAllOf(decoded)) {
                    logInvalidLink(link);
                    return false;
                }

                // Allow starting with either '/' or './'
                boolean startsWithSlash = decoded.charAt(0) == '/';
                boolean startsWithDotSlash = decoded.startsWith("./");
                if (!startsWithSlash && !startsWithDotSlash) {
                    logInvalidLink(link);
                    return false;
                }

                // Block directory traversal (../) or hidden files (/.)
                String pathToCheck = startsWithDotSlash ? decoded.substring(2) : decoded;
                if (pathToCheck.contains("/.")) {
                    logInvalidLink(link);
                    return false;
                }

                // Normalize and validate using URI
                var uri = new URI(decoded).normalize();

                // Ensure normalization didn't escape the intended directory scope (e.g., if someone snuck a relative path in)
                if (uri.toString().contains("..")) {
                    logInvalidLink(link);
                    return false;
                }

                if (uri.isAbsolute() || uri.getScheme() != null) {
                    logInvalidLink(link);
                    return false;
                }

                logger.trace("Valid navigation link {}", decoded);
                return true;
            } catch (URISyntaxException | IllegalArgumentException e) {
                logInvalidLink(link);
                return false;
            }
        }

        private static void logInvalidLink(String link) {
            if (logger.isWarnEnabled()) {
                var cleaned = link == null ? null : SAFE_LINK_CHARS.negate().replaceFrom(link.substring(0, Math.min(link.length(), 300)), '*');
                logger.warn("Rejecting invalid navigation link '{}'", cleaned);
            }
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
