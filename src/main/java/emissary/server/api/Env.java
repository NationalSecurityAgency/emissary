package emissary.server.api;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.client.response.MapResponseEntity;
import emissary.command.ServerCommand;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.server.EmissaryServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The env Emissary API endpoint that returns key=value pairs of config info for the running node
 * <p>
 * Suitable for parsing or sourcing in bash, as the 'env.sh' command calls it calls.
 */
@Path("")
// context is /api
public class Env {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @GET
    @Path("/env")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEnvJson() {
        return Response.ok().entity(getEnv()).build();
    }

    @GET
    @Path("/env.sh")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getEnvForBash() {
        return Response.ok().entity(envString()).build();
    }

    private MapResponseEntity getEnv() {
        MapResponseEntity entity = new MapResponseEntity();
        try {
            EmissaryServer server = (EmissaryServer) Namespace.lookup("EmissaryServer");
            ServerCommand command = server.getServerCommand();
            entity.addKeyValue("CONFIG_DIR", command.getConfig().toAbsolutePath().toString());
            entity.addKeyValue("PROJECT_BASE", command.getProjectBase().toAbsolutePath().toString());
            entity.addKeyValue("OUTPUT_ROOT", command.getOutputDir().toAbsolutePath().toString());
            entity.addKeyValue("BIN_DIR", command.getBinDir().toAbsolutePath().toString());
            entity.addKeyValue("HOST", command.getHost());
            entity.addKeyValue("PORT", Integer.toString(command.getPort()));
            entity.addKeyValue("SCHEME", command.getScheme());
            logger.debug("Returning env: {}", entity.getResponse());
        } catch (NamespaceException e) {
            entity.addError(e.getMessage());
        }
        return entity;
    }

    private String envString() {
        StringBuilder sb = new StringBuilder();
        MapResponseEntity entity = getEnv();
        if (entity.getErrors().size() > 0) {
            for (String msg : entity.getErrors()) {
                sb.append(msg + "\n");
            }
        } else {
            for (Map.Entry<String, String> entry : entity.getResponse().entrySet()) {
                sb.append("export " + entry.getKey() + "=\"" + entry.getValue() + "\"\n");
            }
        }
        return sb.toString();
    }

}
