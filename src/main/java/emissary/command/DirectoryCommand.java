package emissary.command;

import emissary.client.EmissaryClient;
import emissary.client.response.DirectoryResponseEntity;

import com.beust.jcommander.Parameters;
import org.apache.http.client.methods.HttpGet;

import static emissary.server.api.Directories.DIRECTORIES_ENDPOINT;

@Parameters(commandDescription = "List all of the active directories")
public class DirectoryCommand extends MonitorCommand<DirectoryResponseEntity> {

    public static final String COMMAND_NAME = "directory";
    public static final int DEFAULT_PORT = 8001;

    @Override
    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public DirectoryResponseEntity sendRequest(EmissaryClient client, String endpoint) {
        return client.send(new HttpGet(endpoint)).getContent(DirectoryResponseEntity.class);
    }

    @Override
    public String getTargetEndpoint() {
        return DIRECTORIES_ENDPOINT;
    }
}
