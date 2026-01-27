package emissary.core.sentinel.protocols.actions;

import emissary.core.IMobileAgent;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.sentinel.protocols.trackers.AgentTracker;
import emissary.core.sentinel.protocols.trackers.Tracker;
import emissary.log.MDCConstants;

import org.apache.commons.lang3.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Log the problem agents/threads
 */
public class LogStackTrace extends Action {

    private static final Logger SENTINEL_LOG = LoggerFactory.getLogger("sentinel");

    @Override
    public void trigger(final Map<String, Tracker> trackers) {
        SENTINEL_LOG.warn("Sentinel detected possible locked agents -- {}", format(trackers));
        for (AgentTracker agent : toAgentTrackers(trackers)) {
            try {
                final IMobileAgent mobileAgent = (IMobileAgent) Namespace.lookup(agent.getAgentName());
                final Thread t = ThreadUtils.findThreadById(mobileAgent.getThreadId());
                MDC.put(MDCConstants.SHORT_NAME, agent.getShortName());
                MDC.put(MDCConstants.SERVICE_LOCATION, agent.getDirectoryEntryKey());
                SENTINEL_LOG.info("In agent {} for {} minute(s)\n\t{}", agent.getAgentName(), agent.getTimer(),
                        Arrays.stream(t.getStackTrace())
                                .map(StackTraceElement::toString)
                                .collect(Collectors.joining("\n\t")));
                MDC.remove(MDCConstants.SHORT_NAME);
                MDC.remove(MDCConstants.SERVICE_LOCATION);
            } catch (NamespaceException e) {
                logger.error("Could not fetch agent {}", agent, e);
            }
        }
    }
}
