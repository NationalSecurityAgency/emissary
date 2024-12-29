package emissary.command;

import emissary.client.EmissaryResponse;
import emissary.core.EmissaryRuntimeException;
import emissary.directory.EmissaryNode;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import static emissary.server.api.HealthCheckAction.HEALTH;
import static emissary.server.api.Shutdown.SHUTDOWN;

/**
 * Abstract command to control service components (stop/pause/unpause)
 */
public abstract class ServiceCommand extends HttpCommand {

    static final Logger LOG = LoggerFactory.getLogger(ServiceCommand.class);

    public static final String COMMAND_NAME = "ServiceCommand";
    public static final String SERVICE_HEALTH_ENDPOINT = "/api/" + HEALTH;
    public static final String SERVICE_SHUTDOWN_ENDPOINT = "/api/" + SHUTDOWN;
    public static final String SERVICE_KILL_ENDPOINT = SERVICE_SHUTDOWN_ENDPOINT + "/force";

    @Option(names = {"--csrf"}, description = "disable csrf protection\nDefault: ${DEFAULT-VALUE}", arity = "1")
    private boolean csrf = true;

    @Option(names = {"--stop"}, description = "Shutdown the service\nDefault: ${DEFAULT-VALUE}")
    private boolean stop = false;

    @Option(names = {"--refresh"}, description = "Refresh services\nDefault: ${DEFAULT-VALUE}")
    private boolean refresh = false;

    @Option(names = {"--kill"}, description = "Force the shutdown of the service\nDefault: ${DEFAULT-VALUE}")
    private boolean kill = false;

    @Option(names = {"--pause"}, description = "Stop the service from taking work\nDefault: ${DEFAULT-VALUE}")
    private boolean pause = false;

    @Option(names = {"--unpause"}, description = "Allow a paused service to take work\nDefault: ${DEFAULT-VALUE}")
    private boolean unpause = false;

    public boolean isCsrf() {
        return csrf;
    }

    public boolean isStop() {
        return stop;
    }

    public boolean isRefresh() {
        return refresh;
    }

    public boolean isKill() {
        return kill;
    }

    public boolean isPause() {
        return pause;
    }

    public boolean isUnpause() {
        return unpause;
    }

    public String getServiceHealthEndpoint() {
        return SERVICE_HEALTH_ENDPOINT;
    }

    public String getServiceShutdownEndpoint() {
        return isKill() ? SERVICE_KILL_ENDPOINT : SERVICE_SHUTDOWN_ENDPOINT;
    }

    public String getServiceName() {
        return getCommandName();
    }

    /**
     * Startup the service
     */
    protected abstract void startService();

    @Override
    public void setupHttp() {
        super.setupHttp();

        // check to see if this is set -- feed will call server command and reset this value
        if (StringUtils.isBlank(System.getProperty(EmissaryNode.NODE_SERVICE_TYPE_PROPERTY))) {
            logInfo("Setting {} to {} ", EmissaryNode.NODE_SERVICE_TYPE_PROPERTY, getCommandName());
            System.setProperty(EmissaryNode.NODE_SERVICE_TYPE_PROPERTY, getCommandName());
        }
    }

    @Override
    public void run(CommandLine c) {
        setup();

        // let's check to see if the server is already running
        LOG.debug("Checking to see if Emissary {} is running at {}", getServiceName(), getServiceHealthEndpoint());
        EmissaryResponse response = performGet(getServiceHealthEndpoint());
        boolean isRunning = response.getStatus() == 200;
        if (isStop() || isKill()) {
            if (isRunning) {
                stopService();
            } else {
                LOG.warn("Error stopping service: no service {} running", getServiceName());
            }
        } else {
            if (isRunning) {
                // the server is already running so pause/unpause or fail
                if (isPause()) {
                    pauseService();
                } else if (isUnpause()) {
                    unpauseService();
                } else if (isRefresh()) {
                    refreshService();
                } else {
                    throw new EmissaryRuntimeException("Emissary " + getServiceName() + " is already running");
                }
            } else {
                if (isPause()) {
                    // we hadn't intended to start the service, so don't try to do so now
                    throw new EmissaryRuntimeException("Error pausing service: request returned status " + response.getStatus());
                } else {
                    // no running server so fire it up
                    startService();
                }
            }
        }
    }

    /**
     * Shutdown method that uses an endpoint to stop a running service
     */
    protected void stopService() {
        LOG.info("Stopping Emissary {} at {}", getServiceName(), getServiceShutdownEndpoint());
        EmissaryResponse response = performPost(getServiceShutdownEndpoint());
        if (response.getStatus() != 200) {
            LOG.error("Problem shutting down {} -- {}", getServiceName(), response.getContentString());
        } else {
            LOG.info("Emissary {} stopped", getServiceName());
        }
    }

    /**
     * A method that stops a running service from taking work
     */
    protected void pauseService() {
        throw new UnsupportedOperationException("Pause not implemented for " + getServiceName());
    }

    /**
     * A method that allows a paused service to take work
     */
    protected void unpauseService() {
        throw new UnsupportedOperationException("Unpause not implemented for " + getServiceName());
    }

    /**
     * A method that refreshes services
     */
    protected void refreshService() {
        throw new UnsupportedOperationException("Refresh not implemented for " + getServiceName());
    }

}
