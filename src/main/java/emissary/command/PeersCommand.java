package emissary.command;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.net.HostAndPort;
import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.directory.KeyManipulator;

// TODO Had to extend Http in order to get node host/port substitution. MAybe move up to BaseCommand?
@Parameters(commandDescription = "Read the peers.cfg (respective flavors) and return hosts as bashable list")
public class PeersCommand extends HttpCommand {
    @Parameter(names = {"-d", "--delimiter"}, description = "delimiter to use when writing host output (note: newline needs to be \\n")
    private String delimiter = ",";

    @Parameter(names = {"-ih", "--ignoreHost"}, description = "the host to ignore with optional port (host[:port])")
    private String ignoreHost = "";

    @Parameter(names = "--withPort", description = "returns each peer with associated port")
    private boolean withPort = false;

    private static final String PEER_CONFIG = "peer.cfg";


    @Override
    public String getCommandName() {
        return "peers";
    }

    @Override
    public void run(JCommander jc) {
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
