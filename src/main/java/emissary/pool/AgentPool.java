package emissary.pool;

import emissary.core.IMobileAgent;
import emissary.core.Namespace;
import emissary.core.NamespaceException;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import javax.annotation.Nullable;

/**
 * Extends the GenericObjectPool to hold MobileAgents, each on it's own thread.
 */
public class AgentPool extends GenericObjectPool<IMobileAgent> {

    private static final int MAX_CALCULATED_AGENT_COUNT = 50;
    private static final int BYTES_IN_GIGABYTES = 1073741824;

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

    private final int initialPoolSize;

    /**
     * Compute the default size for the pool
     * 
     * @param maxMemoryInBytes System max memory used in calculating pool size
     * @param poolSizeOverride User set property for pool size
     */
    protected static int computePoolSize(final long maxMemoryInBytes, @Nullable final Integer poolSizeOverride) {

        // Override based on property
        if (poolSizeOverride != null && poolSizeOverride > 0) {
            logger.debug("Default pool size from properties {}", poolSizeOverride);
            return poolSizeOverride;
        }
        // Check that maxMemoryInBytes is a valid argument
        if (maxMemoryInBytes <= 0) {
            throw new IllegalArgumentException("Must be greater then zero.");
        }

        // 15 if less than 1 Gb
        // 20 for first Gb, +5 for each additional Gb, no more then 50 when calculated
        int size = (((int) (maxMemoryInBytes / BYTES_IN_GIGABYTES) - 1) * 5) + 20;
        size = Math.min(size, MAX_CALCULATED_AGENT_COUNT);
        logger.debug("Computed default pool size of {}", size);

        return size;
    }

    /**
     * Compute the default size for the pool
     */
    public static int computePoolSize() {
        final Integer poolSizeProperty = Integer.getInteger("agent.poolsize", null);
        final long maxMemoryInBytes = Runtime.getRuntime().maxMemory();
        return computePoolSize(maxMemoryInBytes, poolSizeProperty);
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
        namespaceName = name;

        // Set blocking policy
        setBlockWhenExhausted(true);

        // Set maximum wait time when blocking on exhausted pool
        setMaxWait(Duration.ofMinutes(50));

        logger.debug("Configuring AgentPool to use {} agents", initialPoolSize);

        setMaxTotal(initialPoolSize);
        setMinIdle(initialPoolSize);
        setMaxIdle(initialPoolSize);

        bindPool();
        fillPool();
    }

    /**
     * Ensure the pool is full
     */
    protected void fillPool() {
        int level = getMaxTotal();
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
    public void resetFactory(MobileAgentFactory factory) {
        // Ideally we will need to drop and recreate the entire pool
        // in order to get around this deprecated method, but that has
        // impact on the global namespace, most weirdly for the caller
        // of this method since the reference they hold is obsoleted by
        // making this call
        this.factory = factory;
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
            logger.trace("POOL borrow active={}", getNumActive());
            return a;
        } catch (Exception e) {
            logger.info("AgentPool.borrowAgent did not work, stats={}", this);
            throw e;
        }
    }

    /*
     * Get the total current agents in the pool
     */
    public synchronized int getCurrentPoolSize() {
        return getNumIdle() + getNumActive();
    }

    protected void emptyPool() {
        int numberKilled = 0;
        int numberToKill = getCurrentPoolSize();
        long waitTil = System.currentTimeMillis() + (30 * 60 * 1000); // 30 min
        logger.debug("Going to kill {} agents", numberToKill);
        try {
            while (getCurrentPoolSize() != 0) {
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

                    a.killAgent();
                    numberKilled++;
                    killedThisRound++;

                    try {
                        // destroys the object, needed to decrement the numIdle
                        returnAgent(a);
                    } catch (RuntimeException e) {
                        logger.error("Error trying to returnAgent: {}", a.getName(), e);
                    }
                }
                logger.debug("Killed {} agents this round, {} total killed", killedThisRound, numberKilled);
                // give some space for working agents to be returned
                setMaxIdle(numberToKill - numberKilled);
                Thread.sleep(5000);
            }
            logger.info("Pool is now empty");
        } catch (InterruptedException e) {
            logger.error("emptyPool interrupted", e);
            Thread.currentThread().interrupt();
        } finally {
            setMaxIdle(0); // just in case
        }
    }

    /**
     * Gracefully close down all agents and unbind the pool
     */
    @Override
    public void close() {
        logger.info("Closing the agent pool");
        setMaxTotal(0);
        emptyPool();
        super.close();
        Namespace.unbind(getPoolName());
        logger.info("Done closing the agent pool");
    }

    /**
     * Forcibly stop all agents and unbind the pool
     */
    public void kill() {
        logger.info("Killing the agent pool");
        super.close();
        Namespace.unbind(getPoolName());
        logger.info("Done killing the agent pool");
    }

    /**
     * Return an agent to the pool
     */
    public void returnAgent(IMobileAgent agent) {
        logger.trace("Returning {}", agent.getName());
        returnObject(agent);
        logger.trace("POOL return active={}", getNumActive());
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
