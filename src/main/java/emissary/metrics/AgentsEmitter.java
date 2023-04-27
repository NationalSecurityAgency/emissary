package emissary.metrics;

import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.pool.MobileAgentFactory;
import emissary.roll.Rollable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to write out agents status via a logger.
 *
 * This can be configured in the emissary.roll.RollManager.cfg file, i.e.:"
 *
 * <pre>
 * # This configuration will log the running agents every minute
 * ROLLABLE = "AGENTS"
 * AGENTS_CLASS = "emissary.metrics.AgentsEmitter"
 * AGENTS_TIME_PERIOD = "1"
 * AGENTS_TIME_UNIT = "MINUTES"
 * </pre>
 */
public class AgentsEmitter implements Rollable {

    private static final Logger logger = LoggerFactory.getLogger(AgentsEmitter.class);

    /**
     * Attempt to emit an id that an agent is processing
     */
    @Override
    public void roll() {
        Namespace.keySet().stream().filter(k -> k.startsWith(MobileAgentFactory.AGENT_NAME)).sorted().forEach(agentKey -> {
            try {
                logger.info("{}: {}", agentKey, Namespace.lookup(agentKey));
            } catch (NamespaceException e) {
                logger.error("Missing an agent in the Namespace: {}", agentKey);
            }
        });
    }

    /**
     * Currently there is nothing to lock, so just return false.
     *
     * @return false if the MetricsEmitter is not currently rolling
     */
    @Override
    public boolean isRolling() {
        return false;
    }

    /**
     * Do nothing on shutdown.
     */
    @Override
    public void close() {
        // Do nothing on shutdown
    }
}
