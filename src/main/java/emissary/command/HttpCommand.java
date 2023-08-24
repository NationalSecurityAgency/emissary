package emissary.command;

import emissary.client.EmissaryClient;
import emissary.client.EmissaryResponse;
import emissary.command.converter.FileExistsConverter;
import emissary.directory.EmissaryNode;

import com.google.common.net.HostAndPort;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/* abstract command to configure http options
 * <p>
 * Used for both running servers and clients, so things
 * like port or host change meaning depending on how it
 * will be used. This class just setup up the config options.
 *
 */
public abstract class HttpCommand extends BaseCommand {
    static final Logger LOG = LoggerFactory.getLogger(HttpCommand.class);

    public static final String COMMAND_NAME = "HttpCommand";

    public static final int DEFAULT_PORT = 9001;

    @Option(names = {"-p", "--port"}, description = "http port\nDefault: ${DEFAULT-VALUE}")
    private int port = getDefaultPort();

    @Option(names = {"-h", "--host"}, description = "http host\nDefault: ${DEFAULT-VALUE}")
    private String host = "localhost";

    @Option(names = {"-s", "--scheme"}, description = "http scheme\nDefault: ${DEFAULT-VALUE}")
    private String scheme = "http";

    @Option(names = {"-j", "--jettyuserfile"}, description = "jetty-users file to load", converter = FileExistsConverter.class)
    private File jettyUserFile;

    @Option(names = {"--ssl"},
            description = "run node with SSL enabled, reads keystore and keytstorepass from HTTPConnectionFactory.cfg\nDefault: ${DEFAULT-VALUE}")
    private boolean sslEnabled = false;

    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public String getScheme() {
        return scheme;
    }

    public File getJettyUserFile() {
        return jettyUserFile;
    }

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

        setSystemProperty(EmissaryNode.NODE_NAME_PROPERTY, host);
        setSystemProperty(EmissaryNode.NODE_PORT_PROPERTY, Integer.toString(port));
        setSystemProperty(EmissaryNode.NODE_SCHEME_PROPERTY, scheme);
    }


    /**
     * Send a get request using the {@link EmissaryClient}
     *
     * @param endpoint the endpoint i.e. /api/health
     * @return the response object
     */
    protected EmissaryResponse performGet(String endpoint) {
        return new EmissaryClient().send(new HttpGet(getFullUrl(endpoint)));
    }

    /**
     * Send a get request using the {@link EmissaryClient}
     *
     * @param endpoint the endpoint i.e. /api/health
     * @return the response object
     */
    protected EmissaryResponse performPost(String endpoint) {
        EmissaryClient client = new EmissaryClient();
        HttpPost post = client.createHttpPost(getFullUrl(endpoint));
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
    protected String getFullUrl(String endpoint) {
        return getScheme() + "://" + getHostAndPort() + endpoint;
    }
}
