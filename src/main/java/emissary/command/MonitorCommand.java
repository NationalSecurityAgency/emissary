package emissary.command;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import emissary.client.EmissaryClient;
import emissary.client.response.BaseResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MonitorCommand<T extends BaseResponseEntity> extends HttpCommand {

    static final Logger LOG = LoggerFactory.getLogger(MonitorCommand.class);


    public static String COMMAND_NAME = "MonitorCommand";

    @Parameter(names = {"--mon"}, description = "runs the agents command in monitor mode, executing every 30 seconds by default")
    private boolean monintor = false;

    public boolean getMonitor() {
        return monintor;
    }

    @Parameter(names = {"-i", "--interval"}, description = "how many seconds to wait between each endpoint call")
    private int sleepInterval = 30;

    public int getSleepInterval() {
        return sleepInterval;
    }

    @Parameter(names = {"--cluster"}, description = "sets endpoint to clustered mode")
    private boolean clustered = false;

    public boolean getClustered() {
        return clustered;
    }

    public abstract T sendRequest(EmissaryClient client, String endpoint);

    public abstract String getTargetEndpoint();

    @Override
    public void run(JCommander jc) {
        setup();
        try {
            do {
                LOG.info(new Date().toString());
                collectEndpointData();
                if (getMonitor()) {
                    TimeUnit.SECONDS.sleep(getSleepInterval());
                }
            } while (getMonitor());
        } catch (InterruptedException e) {
            // nothing to log here, command was terminated
        }
    }

    private void collectEndpointData() {
        EmissaryClient client = new EmissaryClient();

        T entity = sendRequest(client, buildEndpoint(getHost(), getPort()));
        try {
            if (getClustered()) {
                sendClusterRequests(client, entity);
            }
        } catch (IOException e) {
            LOG.error("Problem generating peer list. Something is very wrong.");
        }

        displayEntityResults(entity);
    }

    private void sendClusterRequests(final EmissaryClient client, final T entity) throws IOException {
        PeersCommand.getPeers(getHostAndPort(), true).parallelStream().forEach(hostAndPort -> {
            try {
                String endpoint = buildEndpoint(hostAndPort);
                T response = sendRequest(client, endpoint);
                synchronized (entity) {
                    entity.append(response);
                }
            } catch (Exception e) {
                LOG.error("Problem hitting agents endpoint: " + hostAndPort + "\n" + e.getMessage());
                synchronized (entity) {
                    entity.addError(e.getMessage());
                }
            }
        });
    }

    // Here as a hook in case commands have summarize/custom display options
    protected void displayEntityResults(T entity) {
        entity.dumpToConsole();
        for (String error : entity.getErrors()) {
            System.err.print(error);
        }
    }

    private String buildEndpoint(final String host, final int port) {
        return buildEndpoint(host + ":" + port);
    }

    private String buildEndpoint(final String hostAndPort) {
        return getScheme() + "://" + hostAndPort + "/" + getTargetEndpoint();
    }

}
