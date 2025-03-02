package emissary.core;

import emissary.place.IServiceProviderPlace;
import emissary.server.EmissaryServer;

import com.codahale.metrics.Timer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

/**
 * Class to help track the things we are interested in monitoring
 */
public class TimedResource implements AutoCloseable {

    protected static final Logger LOG = LoggerFactory.getLogger(TimedResource.class);

    public static final TimedResource EMPTY = new TimedResource();

    @Nullable
    private final IMobileAgent agent;
    private final int payloadCount;
    private final long allowedDuration;
    private final long maxAllowedDuration;
    private final String placeName;
    private final long started;
    private final Action action;

    @Nullable
    private final Timer.Context timerContext;

    private final ReentrantLock lock = new ReentrantLock();

    private volatile boolean isClosed = false;

    private TimedResource() {
        isClosed = true;
        started = -1;
        allowedDuration = -1;
        maxAllowedDuration = -1;
        action = Action.NOTIFY;
        agent = null;
        payloadCount = -1;
        placeName = "NOOP";
        timerContext = null;
    }

    public TimedResource(final IMobileAgent agent, final IServiceProviderPlace place, final long allowedDuration, final Timer timer) {
        this(agent, place, allowedDuration, timer, -1);
    }

    public TimedResource(final IMobileAgent agent, final IServiceProviderPlace place, final long allowedDuration, final Timer timer,
            final long maxAllowedDuration) {
        this(agent, place, allowedDuration, timer, maxAllowedDuration, Action.NOTIFY);
    }

    public TimedResource(final IMobileAgent agent, final IServiceProviderPlace place, final long allowedDuration, final Timer timer,
            final long maxAllowedDuration, final Action action) {
        this.started = System.currentTimeMillis();
        this.agent = agent;
        this.payloadCount = agent.payloadCount();
        this.placeName = place.getPlaceName();
        this.timerContext = timer.time();
        this.allowedDuration = allowedDuration;
        this.maxAllowedDuration = maxAllowedDuration;
        this.action = action;
    }

    // checks the state of the current place, returns true if it's closed
    protected boolean checkState(long now) {
        if (allowedDuration > 0 && (now - started) > (allowedDuration * payloadCount)) {
            interruptAgent(now);
        }
        return isClosed;
    }

    // test visibility
    void interruptAgent(long now) {
        // don't grab the lock if we're done
        if (isClosed) {
            return;
        }
        lock.lock();
        try {
            if (!isClosed) {
                if (maxAllowedDuration > 0 && (now - started) > (maxAllowedDuration * payloadCount)) {
                    action.run(agent, placeName);
                } else {
                    Action.RECOVER.run(agent, placeName);
                }
            }
        } catch (RuntimeException e) {
            LOG.error("Unable to interrupt agent {}: {}", agent.getName(), e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    @Override
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

    public enum Action {
        NOTIFY {
            @Override
            public void run(final IMobileAgent agent, final String placeName) {
                LOG.warn("Found agent that is possibly locked -- {} in place {}", agent.getName(), placeName);
            }
        },
        RECOVER {
            @Override
            public void run(final IMobileAgent agent, final String placeName) {
                LOG.debug("Found agent that needs interrupting {} in place {}", agent.getName(), placeName);
                agent.interrupt();
            }
        },
        STOP {
            @Override
            public void run(final IMobileAgent agent, final String placeName) {
                LOG.error("Found unrecoverable agent, initiating graceful shutdown -- {} in place {}", agent.getName(), placeName);
                var unused = CompletableFuture.runAsync(EmissaryServer::stopServer);
            }
        },
        KILL {
            @Override
            public void run(final IMobileAgent agent, final String placeName) {
                LOG.error("Found unrecoverable agent, initiating forceful shutdown -- {} in place {}", agent.getName(), placeName);
                var unused = CompletableFuture.runAsync(EmissaryServer::stopServerForce);
            }
        },
        EXIT {
            @Override
            @SuppressWarnings("SystemExitOutsideMain")
            public void run(final IMobileAgent agent, final String placeName) {
                LOG.error("Found unrecoverable agent, exiting now -- {} in place {}", agent.getName(), placeName);
                System.exit(1);
            }
        };

        public abstract void run(IMobileAgent agent, String placeName);

        public static Action from(final String value) {
            try {
                return valueOf(StringUtils.upperCase(value));
            } catch (IllegalArgumentException e) {
                LOG.warn("Unknown option [{}], falling back to {}", value, NOTIFY);
                return NOTIFY;
            }
        }
    }
}
