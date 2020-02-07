package emissary.pool;

import emissary.core.IMobileAgent;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extends the GenericObjectPool to hold MobileAgents, each on it's own thread.
 */
public class AgentPool extends GenericObjectPool<IMobileAgent> {
    /**
     * The default name by which we register into the namespace
     */
    protected static final String DEFAULT_NAMESPACE_NAME = "AgentPool";

    /**
     * Super class has private access on factory, so save here
     */
    protected MobileAgentFactory factory;

    /**
     * Our logger
     */
    protected static final Logger logger = LoggerFactory.getLogger(AgentPool.class);

    /**
     * The name used by this pool
     */
    protected String namespaceName;

    final private int initialPoolSize;

    /**
     * Compute the default size for the pool
     */
    public static int computePoolSize() {
        // UNUSED - Investigate this block, sizePerAgent var was never used
        // int sizePerAgent = 1024 * 1024; // default
        // try {
        // emissary.config.Configurator conf = emissary.config.ConfigUtil.getConfigInfo(AgentPool.class);
        // // Size should be in kb so multiply by 1024
        // sizePerAgent = conf.findIntEntry("agent.average_size_kb", 1024) * 1024;
        // }
        // catch (IOException ex) {
        // logger.info("Cannot read config file " + ex.getMessage() + ", using default agent size");
        // }

        long maxMem = Runtime.getRuntime().maxMemory();
        // UNUSED
        // float headRoom = 0.40f; // space for places

        // 15 if less than 1 Gb
        // 20 for first Gb, +5 for each additional Gb
        int size = (((int) (maxMem / (1024 * 1024 * 1024)) - 1) * 5) + 20;

        // Allow override based on property
        size = Integer.getInteger("agent.poolsize", size).intValue();

        logger.debug("Computed default pool size of " + size);

        return size;
    }

    /**
     * Create and configure the pool using the default name and size
     * 
     * @param factory pool object producer
     */
    public AgentPool(MobileAgentFactory factory) {
        this(factory, AgentPool.computePoolSize(), DEFAULT_NAMESPACE_NAME);
    }

    /**
     * Create and configure the pool using the default name
     * 
     * @param maxActive max pool size
     * @param factory pool object producer
     */
    public AgentPool(MobileAgentFactory factory, int maxActive) {
        this(factory, maxActive, DEFAULT_NAMESPACE_NAME);
    }


    /**
     * Create and configure the pool using the specified name
     * 
     * @param factory pool object producer
     * @param maxActive max pool size
     * @param name name of the pool in the namespace
     */
    public AgentPool(MobileAgentFactory factory, int maxActive, String name) {
        super(factory);
        this.factory = factory;
        initialPoolSize = maxActive;
        configurePool(name);
    }

    /**
     * Configure the commons pool stuff based on our requirements
     * 
     * @param name name of the pool in the namespace
     */
    protected void configurePool(String name) {
        setMaxActive(initialPoolSize);
        namespaceName = name;

        // Set blocking policy
        setWhenExhaustedAction(GenericObjectPool.WHEN_EXHAUSTED_BLOCK);

        // Set maximum wait time when blocking on exhausted pool
        setMaxWait(1000 * 60 * 50); // 50 min

        logger.debug("Configuring AgentPool to use " + initialPoolSize + " agents");

        // start them all
        setMinIdle(initialPoolSize);
        setMaxIdle(initialPoolSize);

        bindPool();
        fillPool();
    }

    /**
     * Ensure the pool is full
     */
    protected void fillPool() {
        int level = getMaxActive();
        // fill in the pool
        for (int i = 0; i < level; i++) {
            try {
                addObject();
            } catch (Exception e) {
                logger.error("Cannot fill AgentPool", e);
            }
        }
    }

    /**
     * Reset the factory. Pool will be emptied and refilled
     * 
     * @param factory the new factory
     */
    @SuppressWarnings("deprecation")
    public void resetFactory(MobileAgentFactory factory) {
        // Ideally we will need to drop and recreate th entire pool
        // in order to get around this deprecated method, but that has
        // impact on the global namespace, most weirdly for the caller
        // of this method since the reference they hold is obsoleted by
        // making this call
        // close(); // shutdown and unbind
        // logger.info("AgentPool#resetFactory caused Namespace registered instance to change");
        // new AgentPool(factory, getMaxActive(), getPoolName()); // reload and bind
        setFactory(factory);
        emptyPool();
        fillPool();
    }

    /**
     * Bind the pool into the namespace
     */
    protected void bindPool() {
        // register this pool in the namespace
        Namespace.bind(namespaceName, this);
    }

    /**
     * Get the name used to register this pool
     */
    public String getPoolName() {
        return namespaceName;
    }

    /**
     * Get an agent from the pool
     */
    public IMobileAgent borrowAgent() throws Exception {
        try {
            IMobileAgent a = borrowObject();
            logger.debug("POOL borrow active=" + getNumActive());
            return a;
        } catch (Exception e) {

            logger.info("AgentPool.borrowAgent did not work, stats=" + this.toString());

            throw e;
        }
    }

    protected void emptyPool() {
        int numberKilled = 0;
        long waitTil = System.currentTimeMillis() + (30 * 60 * 1000); // 30 min
        logger.debug("Going to kill {} agents", initialPoolSize);
        try {
            while (numberKilled < initialPoolSize) {
                if (System.currentTimeMillis() > waitTil) {
                    throw new InterruptedException("Too long, tired of waiting. Some MobileAgents are going to die poorly");
                }

                logger.debug("Emptying pool, {} active, {} idle", getNumActive(), getNumIdle());
                int currentIdle = getNumIdle();
                int killedThisRound = 0;
                setMaxIdle(0); // so the returnAgent call below destroys the agent
                for (int i = 0; i < currentIdle; i++) {
                    IMobileAgent a;
                    try {
                        a = borrowAgent();
                    } catch (Exception e) {
                        logger.error("Error trying to borrowAgent", e);
                        continue;
                    }

                    logger.info("Stopping agent {}", a.getName());
                    a.killAgent();
                    logger.info("Stopped agent {}", a.getName());
                    numberKilled++;
                    killedThisRound++;

                    try {
                        // destroys the object, needed to decrement the numIdle
                        returnAgent(a);
                    } catch (Exception e) {
                        logger.error("Error trying to returnAgent: {}", a.getName(), e);
                    }

                }
                logger.debug("Killed {} agents this round, {} total dead", killedThisRound, numberKilled);
                // give some space for working agents to be returned
                setMaxIdle(initialPoolSize - numberKilled);
                Thread.sleep(5000);
            }
            logger.info("Pool is now empty");
        } catch (InterruptedException e) {
            logger.error("emptyPool interrupted", e);
        } finally {
            setMaxIdle(0); // just in case
        }
    }


    /**
     * Close down all agents and stop and unbind the pool
     */
    @Override
    public void close() {
        setMaxActive(0);
        emptyPool();
        Namespace.unbind(getPoolName());
        logger.info("Done stopping the agent pool");
    }

    /**
     * Return an agent to the pool
     */
    public void returnAgent(IMobileAgent agent) throws Exception {
        logger.debug("Returning {}", agent.getName());
        returnObject(agent);
        logger.debug("POOL return active=" + getNumActive());
    }

    /**
     * Return the default named agent pool instance from the namespace
     */
    public static AgentPool lookup() throws NamespaceException {
        return (AgentPool) Namespace.lookup(DEFAULT_NAMESPACE_NAME);
    }

    /**
     * Return the specified agent pool instance from the Namespace
     */
    public static AgentPool lookup(String name) throws NamespaceException {
        return (AgentPool) Namespace.lookup(name);
    }

    /**
     * To string for lightweight reporting
     */
    @Override
    public synchronized String toString() {
        return "Poolsize active/idle = " + getNumActive() + "/" + getNumIdle() + " - " + getPoolName();
    }

    /**
     * Get the name of the class being used from the factory
     * 
     * @return class name for the agents
     */
    public String getClassName() {
        return factory.getClassString();
    }

    /**
     * Try to predict whether a borrow will block/grow the pool
     */
    public boolean isAgentAvailable() {
        return getNumIdle() > 0;
    }
}
