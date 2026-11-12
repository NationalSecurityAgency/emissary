package emissary.server.mvc;

import emissary.server.util.BaseResourcePathUtil;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.server.mvc.Template;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders the configuration viewer. The page calls the configuration API directly.
 */
@Path("")
// context is emissary
public class ConfigsAction {

    @GET
    @Path("/Configs.action")
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/configs")
    public Map<String, String> configs() {
        Map<String, String> model = new HashMap<>();
        model.put("baseResourcePath", BaseResourcePathUtil.getBaseResourcePath());
        return model;
    }
}
