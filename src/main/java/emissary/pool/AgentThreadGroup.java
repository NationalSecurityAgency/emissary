package emissary.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide a ThreadGroup and an uncaught exception handler for our agents to be a part of
 */
public class AgentThreadGroup extends ThreadGroup {

    private static final Logger logger = LoggerFactory.getLogger(AgentThreadGroup.class);

    public AgentThreadGroup(String s) {
        super(s);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        logger.error("Fatal Thread Error on {} had an uncaught exception ", thread.getName(), throwable);
        if (throwable instanceof VirtualMachineError vme) {
            throw vme;
        }
    }
}
