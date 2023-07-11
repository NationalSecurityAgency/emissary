package emissary.command;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.directory.KeyManipulator;

import com.google.common.net.HostAndPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

@Command(description = "Read the peers.cfg (respective flavors) and return hosts as bashable list", subcommands = {HelpCommand.class})
public class PeersCommand extends HttpCommand {

    private static final Logger logger = LoggerFactory.getLogger(PeersCommand.class);

    public static final String COMMAND_NAME = "peers";

    private static final String PEER_CONFIG = "peer.cfg";

    @Option(names = {"-d", "--delimiter"}, description = "delimiter to use when writing host output (note: newline needs to be \\n")
    private String delimiter = ",";

    @Option(names = {"-ih", "--ignoreHost"}, description = "the host to ignore with optional port (host[:port])")
    private String ignoreHost = "";

    @Option(names = "--withPort", description = "returns each peer with associated port")
    private boolean withPort = false;

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public void run() {
        setup();
        try {
            System.out.print(String.join(delimiter, getPeers(HostAndPort.fromString(ignoreHost), this.withPort)));
        } catch (IOException e) {
            LOG.debug("Problem reading file", e);
        }
    }

    @Override
    public void setup() {
        super.setup();
        if (delimiter.contains("\\n")) {
            String replaced = delimiter.replaceAll("\\\\n", System.getProperty("line.separator"));
            delimiter = replaced;
        }

    }

    public static Set<String> getPeers(final HostAndPort ignoreHost, boolean wantPort) throws IOException {
        Configurator peerConfig = ConfigUtil.getConfigInfo(PEER_CONFIG);
        final Set<String> peers = peerConfig.findEntriesAsSet("RENDEZVOUS_PEER");
        final Set<String> added = new TreeSet<>();
        peers.forEach(peerString -> {
            // form peer config files
            HostAndPort peer = HostAndPort.fromString(KeyManipulator.getServiceHost(peerString));

            if ((ignoreHost.hasPort() && !ignoreHost.equals(peer)) || !ignoreHost.getHost().equals(peer.getHost())) {
                if (wantPort) {
                    added.add(peer.toString());
                } else {
                    added.add(peer.getHost());
                }
            }
        });

        return added;
    }
}
