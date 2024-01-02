package emissary.server.api;

import emissary.client.response.Directory;
import emissary.client.response.DirectoryList;
import emissary.client.response.DirectoryResponseEntity;
import emissary.core.EmissaryException;
import emissary.directory.DirectoryEntry;
import emissary.directory.DirectoryPlace;
import emissary.directory.IDirectoryPlace;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Path("")
public class Directories {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String DIRECTORIES_ENDPOINT = "api/directories";

    @GET
    @Path("/directories")
    @Produces(MediaType.APPLICATION_JSON)
    public Response directories() {
        return Response.ok().entity(getDir()).build();
    }

    protected DirectoryResponseEntity getDir() {
        DirectoryResponseEntity entity = new DirectoryResponseEntity();
        try {
            IDirectoryPlace dirPlace = DirectoryPlace.lookup();
            DirectoryList entries = new DirectoryList();
            entries.setDirectoryPlace(dirPlace.getKey());
            List<DirectoryEntry> dirEntries = dirPlace.getEntries();

            for (DirectoryEntry currentDir : dirEntries) {
                entries.addEntries(new Directory(currentDir));
            }

            entity.setLocal(entries);
            logger.debug("Returning Directory Entries: {}", entity.getLocal());
        } catch (EmissaryException e) {
            logger.error("Problem finding the directory in the namespace,", e);
            entity.addError("Problem finding the directory in the namespace: " + e.getMessage());
        }
        return entity;
    }

}
