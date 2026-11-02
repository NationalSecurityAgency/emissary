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
 * Renders the output policy test form. The form posts directly to the output trace API.
 */
@Path("")
// context is emissary
public class OutputTestAction {

    @GET
    @Path("/OutputTest.action")
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/output_test")
    public Map<String, String> outputTest() {
        Map<String, String> model = new HashMap<>();
        model.put("baseResourcePath", BaseResourcePathUtil.getBaseResourcePath());
        return model;
    }
}
