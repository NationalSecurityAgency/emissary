package emissary.core.sentinel.protocols.actions;

import emissary.core.IMobileAgent;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.sentinel.protocols.trackers.Tracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Map;

/**
 * Log the problem agents/threads
 */
public class LogThreadDump extends Action {

    private static final Logger SENTINEL_LOG = LoggerFactory.getLogger("sentinel");

    @Override
    public void trigger(Map<String, Tracker> trackers) {
        SENTINEL_LOG.warn("Sentinel detected possible locked agents -- {}", format(trackers));
        List<String> agentNames = getAgentNames(trackers);
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        for (String agentName : agentNames) {
            try {
                IMobileAgent mobileAgent = (IMobileAgent) Namespace.lookup(agentName);
                ThreadInfo info = bean.getThreadInfo(mobileAgent.getThreadId(), Integer.MAX_VALUE);
                SENTINEL_LOG.info("{}", info);
            } catch (NamespaceException e) {
                logger.error("Could not fetch agent {}", agentName, e);
            }
        }
    }
}
