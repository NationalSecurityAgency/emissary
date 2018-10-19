package emissary.server.mvc.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.config.ConfigUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web-tier worker to server local config files to remote requestors
 */
@Path("")
// context is /emissary, set in EmissaryServer
public class ConfigFileAction {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    public static final String CONFIG_PARAM = "ConfigItem";
    public static final String CONFIG_NOT_FOUND = "Config item not found: ";

    /**
     * Perform the action Input ConfigItem Output RequestAttribute MESSAGE
     */
    @GET
    @Path("/ConfigFile.action")
    @Produces(MediaType.TEXT_PLAIN)
    public Response configFile(@QueryParam(CONFIG_PARAM) String configName) {
        try {
            if (StringUtils.isBlank(configName)) {
                logger.error(CreatePlaceAction.EMPTY_PARAM_MSG);
                return Response.serverError().entity(CreatePlaceAction.EMPTY_PARAM_MSG).build();
            }

            logger.debug("Providing remote access to {}", configName);
            // TODO Look and see if this works for favored configs
            String content = IOUtils.toString(ConfigUtil.getConfigStream(configName), StandardCharsets.UTF_8);
            return Response.ok().entity(content).build();
        } catch (IOException e) {
            logger.error("Config item not found", e);
            return Response.serverError().entity(CONFIG_NOT_FOUND + configName).build();
        }
    }
}
