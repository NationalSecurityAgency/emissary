package emissary.command;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

import com.beust.jcommander.Parameter;
import com.google.common.net.HostAndPort;
import emissary.client.EmissaryClient;
import emissary.client.EmissaryResponse;
import emissary.command.converter.FileExistsConverter;
import emissary.directory.EmissaryNode;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* abstract command to configure http options
 * <p>
 * Used for both running servers and clients, so things
 * like port or host change meaning depending on how it
 * will be used. This class just setup up the config options.
 *
 */
public abstract class HttpCommand extends BaseCommand {
    static final Logger LOG = LoggerFactory.getLogger(HttpCommand.class);

    public static String COMMAND_NAME = "HttpCommand";

    public static int DEFAULT_PORT = 9001;

    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    @Parameter(names = {"-p", "--port"}, description = "http port")
    private int port = getDefaultPort();

    public int getPort() {
        return port;
    }

    @Parameter(names = {"-h", "--host"}, description = "http host")
    private String host = "localhost";

    public String getHost() {
        return host;
    }

    @Parameter(names = {"-s", "--scheme"}, description = "http scheme")
    private String scheme = "http";

    public String getScheme() {
        return scheme;
    }

    @Parameter(names = {"-j", "--jettyuserfile"}, description = "jetty-users file to load", converter = FileExistsConverter.class)
    private File jettyUserFile;

    public File getJettyUserFile() {
        return jettyUserFile;
    }

    @Parameter(names = {"--ssl"}, description = "run node with SSL enabled, reads keystore and keytstorepass from HTTPConnectionFactory.cfg")
    private boolean sslEnabled = false;

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    @Override
    public void setupCommand() {
        setupHttp();
    }

    public void setupHttp() {
        setupConfig();
        if (getJettyUserFile() != null) {
            LOG.debug("Setting {} to {}", EmissaryClient.JETTY_USER_FILE_PROPERTY_NAME, getJettyUserFile().getAbsolutePath());
            System.setProperty(EmissaryClient.JETTY_USER_FILE_PROPERTY_NAME, getJettyUserFile().getAbsolutePath());
        }
        // add SSL flavor if sslEnabled and ensure scheme is https
        if (sslEnabled) {
            String flavorMode;
            if (getFlavor() == null) {
                flavorMode = "SSL";
            } else {
                flavorMode = "SSL" + "," + getFlavor();
            }

            // Must maintain insertion order
            Set<String> flavorSet = new LinkedHashSet<>();
            for (String f : flavorMode.split(",")) {
                flavorSet.add(f.toUpperCase());
            }
            overrideFlavor(String.join(",", flavorSet));

            if (getScheme().equals("http")) {
                // maybe remove this debug and make this always happen
                LOG.debug("Oops, scheme set to http, overriding with https");
                scheme = "https";
            }

            // TODO: also check that keystore and keystorepass are set
        }

        logInfo("Setting {} to {} ", EmissaryNode.NODE_NAME_PROPERTY, host);
        System.setProperty(EmissaryNode.NODE_NAME_PROPERTY, host);
        logInfo("Setting {} to {} ", EmissaryNode.NODE_PORT_PROPERTY, Integer.toString(port));
        System.setProperty(EmissaryNode.NODE_PORT_PROPERTY, Integer.toString(port));
        logInfo("Setting {} to {} ", EmissaryNode.NODE_SCHEME_PROPERTY, scheme);
        System.setProperty(EmissaryNode.NODE_SCHEME_PROPERTY, scheme);
    }


    /**
     * Send a get request using the {@link EmissaryClient}
     *
     * @param endpoint the endpoint i.e. /api/health
     * @return the response object
     */
    protected EmissaryResponse performGet(String endpoint) {
        return new EmissaryClient().send(new HttpGet(getEndpoint(endpoint)));
    }

    /**
     * Send a get request using the {@link EmissaryClient}
     *
     * @param endpoint the endpoint i.e. /api/health
     * @return the response object
     */
    protected EmissaryResponse performPost(String endpoint) {
        EmissaryClient client = new EmissaryClient();
        HttpPost post = client.createHttpPost(getEndpoint(endpoint));
        return client.send(post);
    }

    public HostAndPort getHostAndPort() {
        return HostAndPort.fromParts(getHost(), getPort());
    }

    /**
     * Build the full url to the Emissary endpoint
     *
     * @param endpoint the endpoint i.e. /api/health
     * @return the full url
     */
    protected String getEndpoint(String endpoint) {
        return getScheme() + "://" + getHostAndPort() + endpoint;
    }
}
