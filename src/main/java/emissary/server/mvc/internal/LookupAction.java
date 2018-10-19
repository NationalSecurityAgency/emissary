package emissary.server.mvc.internal;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.core.Namespace;
import emissary.core.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("")
// context is /emissary, set in EmissaryServer
public class LookupAction {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    // Used when communicating over HTTP
    public static final String NAME_PARAMETER = "namespaceName";
    public static final String VALUE_PARAMETER = "namespaceValue";

    @GET
    @Path("/Lookup.action")
    @Produces(MediaType.TEXT_PLAIN)
    public Response lookupPlaceWorker(@QueryParam(NAME_PARAMETER) String name) {
        // TODO: is this used? is it useful? Maybe remove the toString and figure out
        // how to get info from the object
        logger.debug("Namespace lookup for {}", name);

        try {
            final Object value = (name == null) ? null : Namespace.lookup(name);
            if (value != null) {
                logger.debug("Lookup returned " + value);
                return Response.ok().entity(value.toString()).build();
            }
            return Response.serverError().entity("Could not find " + name).build();
        } catch (NamespaceException e) {
            logger.warn("Namespace could not find {}", name, e);
            return Response.serverError().entity("Namespace did not find " + name).build();
        }
    }
}
