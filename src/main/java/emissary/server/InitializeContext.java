package emissary.server;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import emissary.directory.EmissaryNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initialize the application from inside the webapp context This initializer performs the following actions
 * <ol>
 * <li>Create and fill the emissary.pool.AgentPool</li>
 * <li>Create the emissary.pool.MoveSpool</li>
 * <li>Initialize the Charset conversion subsystem</li>
 * <li>Initialize the Metadata Dictionary subsystem</li>
 * <li>Deploy any places configure to run on this instance This is determined by looking for an appropriate config file
 * or stream based on the following preferences:
 * <ol>
 * <li>node-ENV{{@value emissary.directory.EmissaryNode#NODE_NAME_PROPERTY} -ENV{
 * {@value emissary.directory.EmissaryNode#NODE_PORT_PROPERTY} .cfg</li>
 * <li>node-ENV{{@value emissary.directory.EmissaryNode#NODE_NAME_PROPERTY} .cfg</li>
 * <li>node-ENV{os.name}.toLowerCase().cfg</li>
 * <li>node.cfg</li>
 * </ol>
 * </li>
 * </ol>
 * 
 * @see emissary.directory.EmissaryNode
 */
// TODO: can we annotate this instead with @WebListener
public class InitializeContext implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(InitializeContext.class);
    private final EmissaryNode node;

    public InitializeContext(EmissaryNode node) {
        super();
        this.node = node;
    }

    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        // ServletContext sc = sce.getServletContext();

        logger.info("Emissary Node services starting for " + node);
        try {
            node.configureEmissaryServer();
        } catch (emissary.core.EmissaryException ex) {
            logger.error("Unable to start Emissary Node services", ex);
        }
    }

    @Override
    public void contextDestroyed(final ServletContextEvent sce) {
        logger.debug("Received servlet context destroyed event");
    }
}
