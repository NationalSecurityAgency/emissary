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
        if (throwable instanceof java.lang.ThreadDeath) {
            logger.error("Fatal ThreadDeath on " + thread.getName() + " had ThreadDeath exception", throwable);
            throw (java.lang.ThreadDeath) throwable;
        }
        logger.error("#######    #    #######    #    #      ");
        logger.error("#         # #      #      # #   #      ");
        logger.error("#        #   #     #     #   #  #      ");
        logger.error("#####   #     #    #    #     # #      ");
        logger.error("#       #######    #    ####### #      ");
        logger.error("#       #     #    #    #     # #      ");
        logger.error("#       #     #    #    #     # #######");
        logger.error("Fatal Thread Error on " + thread.getName() + " had an uncaught exception", throwable);
    }
}
