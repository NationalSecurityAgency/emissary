package emissary.command;

import static emissary.server.api.HealthCheckAction.HEALTH;
import static emissary.server.api.Shutdown.SHUTDOWN;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import emissary.client.EmissaryResponse;
import emissary.directory.EmissaryNode;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract command to control service components (stop/pause/unpause)
 */
public abstract class ServiceCommand extends HttpCommand {

    static final Logger LOG = LoggerFactory.getLogger(ServiceCommand.class);

    public static String COMMAND_NAME = "ServiceCommand";
    public static String SERVICE_HEALTH_ENDPOINT = "/api/" + HEALTH;
    public static String SERVICE_SHUTDOWN_ENDPOINT = "/api/" + SHUTDOWN;

    @Parameter(names = {"--csrf"}, description = "disable csrf protection", arity = 1)
    private boolean csrf = true;

    @Parameter(names = {"--stop"}, description = "Shutdown the service")
    private boolean stop = false;

    @Parameter(names = {"--pause"}, description = "Stop the service from taking work")
    private boolean pause = false;

    @Parameter(names = {"--unpause"}, description = "Allow a paused service to take work")
    private boolean unpause = false;

    public boolean isCsrf() {
        return csrf;
    }

    public boolean isStop() {
        return stop;
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
        return SERVICE_SHUTDOWN_ENDPOINT;
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
    public void run(JCommander jc) {
        setup();

        // let's check to see if the server is already running
        LOG.debug("Checking to see if Emissary {} is running at {}", getServiceName(), getServiceHealthEndpoint());
        EmissaryResponse response = performGet(getServiceHealthEndpoint());
        boolean isRunning = response.getStatus() == 200;
        if (isStop()) {
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
                } else {
                    throw new RuntimeException("Emissary " + getServiceName() + " is already running");
                }
            } else {
                // no running server so fire it up
                startService();
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

}
