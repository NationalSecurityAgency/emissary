package emissary.core.sentinel.protocols.actions;

import emissary.core.IMobileAgent;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.sentinel.protocols.trackers.AgentTracker;
import emissary.core.sentinel.protocols.trackers.Tracker;
import emissary.log.MDCConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Map;

/**
 * Log the problem agents/threads
 */
public class LogThreadDump extends Action {

    private static final Logger SENTINEL_LOG = LoggerFactory.getLogger("sentinel");

    @Override
    public void trigger(final Map<String, Tracker> trackers) {
        SENTINEL_LOG.warn("Sentinel detected possible locked agents -- {}", format(trackers));
        final ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        for (AgentTracker agent : toAgentTrackers(trackers)) {
            try {
                final IMobileAgent mobileAgent = (IMobileAgent) Namespace.lookup(agent.getAgentName());
                final ThreadInfo info = bean.getThreadInfo(mobileAgent.getThreadId(), Integer.MAX_VALUE);
                MDC.put(MDCConstants.SHORT_NAME, mobileAgent.getShortName());
                MDC.put(MDCConstants.SERVICE_LOCATION, agent.getDirectoryEntryKey());
                /*
                 * mobileAgent.currentForm() is not necessarily the form at the time the payload arrived at this place, since some
                 * places change the form even before processing, but it's the best we have to identify the payload being processed
                 */
                SENTINEL_LOG.info("In agent {} with currentForm {} for {} minute(s)\n\t{}", agent.getAgentName(), mobileAgent.getPayloadCurrentForm(),
                        agent.getTimer(), info);
                MDC.remove(MDCConstants.SHORT_NAME);
                MDC.remove(MDCConstants.SERVICE_LOCATION);
            } catch (NamespaceException e) {
                logger.error("Could not fetch agent {}", agent, e);
            }
        }
    }
}
