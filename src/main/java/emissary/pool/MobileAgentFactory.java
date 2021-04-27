package emissary.pool;

import java.io.IOException;

import emissary.core.Factory;
import emissary.core.IMobileAgent;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MobileAgentFactory implements PooledObjectFactory<IMobileAgent> {

    // This is the default class when nothing else is
    // configured or passed on on a constructor. This value
    // can be overridden from the AgentPool.cfg file
    static String DEFAULT_CLASS_STRING = "emissary.core.MobileAgent";

    public static String AGENT_NAME = "MobileAgent";

    // This is the class we are going to be pooling
    // The value can be set by constructor, by public setter
    // or from the value in DEFAULT_CLASS_STRING
    String classString = DEFAULT_CLASS_STRING;

    int maxAgentMoveErrors = 3;

    int maxAgentItinerary = 100;

    private static final Logger logger = LoggerFactory.getLogger(MobileAgentFactory.class);

    // Thread group for every agent produced by this factory
    private static final AgentThreadGroup threadGroup = new AgentThreadGroup("Agent Threads");

    // True if created objects should be registered in namespace
    private boolean useNamespace = true;

    // Track how many objects created here
    private long objectsCreated = 0;

    protected void configure() {
        // Setup the DEFAULT_CLASS_STRING value by peeking at the config file
        try {
            emissary.config.Configurator conf = emissary.config.ConfigUtil.getConfigInfo(AgentPool.class);
            classString = conf.findStringEntry("agent.class", DEFAULT_CLASS_STRING);

            maxAgentMoveErrors = conf.findIntEntry("agent.move.errors", maxAgentMoveErrors);
            maxAgentItinerary = conf.findIntEntry("agent.max.itinerary", maxAgentItinerary);
        } catch (IOException e) {
            logger.debug("Cannot read AgentPool.cfg, taking default values");
        }
    }

    /**
     * create a new instance of the mobile agent factory for use by the pool
     */
    public MobileAgentFactory() {
        super();
        configure();
        logger.debug("Factory will create " + classString + " agents");
    }

    /**
     * create a new instance of the mobile agent factory using the specified class as the implementation du jour
     * 
     * @param clazz the class of mobile agent to use
     */
    public MobileAgentFactory(String clazz) {
        super();
        configure();
        setClassString(clazz);
        logger.debug("Factory will create " + classString + " agents");
    }

    /**
     * Set whether created mobile agents should be registered in the global namespace or not
     * 
     * @param arg true if registration in namespace is desired
     */
    public void setUseNamespace(boolean arg) {
        this.useNamespace = arg;
    }

    /**
     * called by the pool to get an instance of the specified implementation
     * 
     * @return a newly Factory.create()ed instance
     */
    @Override
    public PooledObject<IMobileAgent> makeObject() {
        logger.debug("Calling MobileAgentFactory.makeObject for " + getClassString());
        IMobileAgent agent = null;
        String aname = AGENT_NAME + "-" + (objectsCreated < 10 ? "0" : "") + objectsCreated;
        try {
            if (useNamespace) {
                agent = (IMobileAgent) Factory.createV(getClassString(), aname, threadGroup, aname);
            } else {
                agent = (IMobileAgent) Factory.create(getClassString(), threadGroup, aname);
            }
            agent.setMaxItinerarySteps(maxAgentItinerary);
            agent.setMaxMoveErrors(maxAgentMoveErrors);
        } catch (Throwable t) {
            logger.error("Unable to Factory.create(" + getClassString() + ") with a threadGroup argument", t);
            if (useNamespace) {
                agent = (IMobileAgent) Factory.createV(getClassString(), aname);
            } else {
                agent = (IMobileAgent) Factory.create(getClassString());
            }
        }
        objectsCreated++;
        return new DefaultPooledObject<>(agent);
    }

    /**
     * Called by the pool to activate an object
     *
     * @param o the object to be activated in the pool
     */
    @Override
    public void activateObject(PooledObject<IMobileAgent> o) {
        logger.trace("Activating {}", o.getObject().getName());
        // no code
    }

    /**
     * Called by the pool to passivate an object
     *
     * @param o the object to be passivated in the pool
     */
    @Override
    public void passivateObject(PooledObject<IMobileAgent> o) {
        logger.trace("Passivating {}", o.getObject().getName());
        // no code
    }

    /**
     * called by the pool when it is configured to validate objects
     * 
     * @param o the object to validate, should be instance of the specified implementation for this factory
     * @return IMobileAgent.isInUse() with proper checking
     */
    @Override
    public boolean validateObject(PooledObject<IMobileAgent> o) {
        logger.debug("Validating {}", o.getObject().getName());
        return !o.getObject().isInUse();
    }

    /**
     * Called by the pool to destroy an object
     * 
     * @param o the object to be removed from the pool and destroyed
     */
    @Override
    public void destroyObject(PooledObject<IMobileAgent> o) {
        logger.debug("Destroying {}", o.getObject().getName());
        o.getObject().killAgentAsync();
    }

    /**
     * Set the class implementing IMobileAgent
     * 
     * @param s the name of the implementing class
     */
    public void setClassString(String s) {
        classString = s;
    }

    /**
     * Get the current class for IMobileAgent we are using
     * 
     * @return the current implementation class string
     */
    public String getClassString() {
        return classString;
    }

    /**
     * Debug info
     */
    @Override
    public String toString() {
        return "MobileAgentFactory created " + objectsCreated + " " + getClassString() + " instances";
    }
}
