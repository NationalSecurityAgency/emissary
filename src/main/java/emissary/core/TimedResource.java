package emissary.core;

import java.util.concurrent.locks.ReentrantLock;

import com.codahale.metrics.Timer;
import emissary.place.IServiceProviderPlace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to help track the things we are interested in monitoring
 */
public class TimedResource implements AutoCloseable {

    protected static final Logger LOG = LoggerFactory.getLogger(TimedResource.class);

    public static final TimedResource EMPTY = new TimedResource();

    private final IMobileAgent agent;
    private final int payloadCount;
    private final long allowedDuration;
    private final String placeName;
    private final long started;

    private final Timer.Context timerContext;

    private final ReentrantLock lock = new ReentrantLock();

    private volatile boolean isClosed = false;

    private TimedResource() {
        isClosed = true;
        started = -1;
        allowedDuration = -1;
        agent = null;
        payloadCount = -1;
        placeName = "NOOP";
        timerContext = null;
    }

    public TimedResource(final IMobileAgent agent, final IServiceProviderPlace place, final long allowedDuration, final Timer timer) {
        this.started = System.currentTimeMillis();
        this.agent = agent;
        this.payloadCount = agent.payloadCount();
        this.placeName = place.getPlaceName();
        this.timerContext = timer.time();
        this.allowedDuration = allowedDuration;

    }

    // checks the state of the current place, returns true if it's closed
    protected boolean checkState(long now) {
        if (allowedDuration > 0 && (now - started) > (allowedDuration * payloadCount)) {
            interruptAgent();
        }
        return isClosed;
    }

    // test visibility
    void interruptAgent() {
        // don't grab the lock if we're done
        if (isClosed) {
            return;
        }
        lock.lock();
        try {
            if (!isClosed) {
                LOG.debug("Found agent that needs interrupting {} in place {}", agent.getName(), placeName);
                agent.interrupt();
            }
        } catch (Exception e) {
            LOG.error("Unable to interrupt agent {}: {}", agent.getName(), e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        try {
            if (isClosed) {
                return;
            }
            timerContext.stop();
            isClosed = true;
        } finally {
            lock.unlock();
        }
    }
}
