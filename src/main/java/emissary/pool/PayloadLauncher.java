package emissary.pool;

import java.util.List;

import emissary.core.EmissaryException;
import emissary.core.IMobileAgent;
import emissary.core.NamespaceException;
import emissary.directory.DirectoryEntry;
import emissary.log.MDCConstants;
import emissary.place.IServiceProviderPlace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Launch an incoming payload the best way possible.
 */
public class PayloadLauncher {
    private static final Logger logger = LoggerFactory.getLogger(PayloadLauncher.class);


    public static boolean launch(Object payload, IServiceProviderPlace place, int errorCount, List<DirectoryEntry> itineraryItems)
            throws EmissaryException {
        String payloadName = emissary.util.PayloadUtil.getName(payload);
        // Try to grab the arrival spool and save the payload and place
        // because this will allow us to give an asynchronous response

        MDC.put(MDCConstants.SHORT_NAME, payloadName);
        try {
            MoveSpool spool = null;
            try {
                spool = MoveSpool.lookup();
            } catch (NamespaceException spoolex) {
                // empty catch block
            }

            if (spool != null) {
                int sz = spool.arrive(payload, place, errorCount, itineraryItems);
                logger.debug("Payload " + payloadName + " spooled out, " + sz + " on the spool");

                return true;
            }
            // Use the agent pool directly, this may block the caller
            AgentPool pool = null;
            try {
                pool = AgentPool.lookup();
            } catch (NamespaceException nse) {
                throw new EmissaryException("No agent pool available for " + payloadName, nse);
            }

            IMobileAgent agent = null;
            try {
                agent = pool.borrowAgent();
            } catch (Exception e) {
                logger.error("Cannot get agent from pool for " + payloadName, e);
                throw new EmissaryException("Cannot get agent from pool for " + payloadName, e);
            }

            try {
                agent.arrive(payload, place, errorCount, itineraryItems);
                logger.debug("Finished setting up agent with place=" + place + ", payload=" + payloadName + ", isInUse=" + agent.isInUse());
                return true;
            } catch (Exception e) {
                logger.error("Cannot get agent started on payload, " + "is the same implementation of IMobileAgent in use "
                        + "on both sides of the moveTo? payload=" + payloadName, e);
                try {
                    pool.returnAgent(agent);
                } catch (Exception poolex) {
                    logger.error("Could not return agent to pool when it " + "failed to start, killing agent " + agent.getName(), poolex);
                    agent.killAgent();
                }

                throw new EmissaryException("Cannot get agent started on payload for " + payloadName, e);
            }
        } finally {
            MDC.remove(MDCConstants.SHORT_NAME);
        }
    }

    /** This class is not meant to be instantiated. */
    private PayloadLauncher() {}
}
