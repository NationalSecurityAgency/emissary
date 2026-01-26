package emissary.core.sentinel.protocols.actions;

import emissary.core.IMobileAgent;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.sentinel.protocols.trackers.Tracker;

import org.apache.commons.lang3.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Log the problem agents/threads
 */
public class LogStackTrace extends Action {

    private static final Logger SENTINEL_LOG = LoggerFactory.getLogger("sentinel");

    @Override
    public void trigger(Map<String, Tracker> trackers) {
        SENTINEL_LOG.warn("Sentinel detected possible locked agents -- {}", format(trackers));
        List<String> agentNames = getAgentNames(trackers);
        for (String agentName : agentNames) {
            try {
                IMobileAgent mobileAgent = (IMobileAgent) Namespace.lookup(agentName);
                Thread t = ThreadUtils.findThreadById(mobileAgent.getThreadId());
                SENTINEL_LOG.info("Agent {}\n\t{}", agentName,
                        Arrays.stream(t.getStackTrace())
                                .map(StackTraceElement::toString)
                                .collect(Collectors.joining("\n\t")));
            } catch (NamespaceException e) {
                logger.error("Could not fetch agent {}", agentName, e);
            }
        }
    }
}
