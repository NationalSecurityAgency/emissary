package emissary.core;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Pausable extends Thread implements IPausable {

    private static final Logger logger = LoggerFactory.getLogger(Pausable.class);

    public static final long DEFAULT_PAUSE_INTERVAL = TimeUnit.MINUTES.toMillis(1);
    protected long pauseInterval;

    private boolean paused = false;

    public Pausable(String name) {
        this(name, DEFAULT_PAUSE_INTERVAL);
    }

    public Pausable(String name, long pauseInterval) {
        super(name);
        this.pauseInterval = pauseInterval;
    }

    /**
     * Stop taking work
     */
    @Override
    public void pause() {
        paused = true;
    }

    /**
     * Resume taking work
     */
    @Override
    public void unpause() {
        paused = false;
    }

    /**
     * Check to see if the current thread is paused
     *
     * @return true if work is paused, false otherwise
     */
    @Override
    public boolean isPaused() {
        return paused;
    }

    /**
     * Get the time to sleep before checking if the thread has been unpaused
     *
     * @return the pause check interval
     */
    public long getPauseInterval() {
        return pauseInterval;
    }

    /**
     * Check to see if we are currently paused. If paused, sleep for the configured interval and return true.
     *
     * @return true if paused, false otherwise
     */
    public boolean checkPaused() {
        // check to see if we want to stop taking work
        if (isPaused()) {
            try {
                logger.info("{} currently paused, sleeping for {}", getClass().getName(), getPauseInterval());
                sleep(getPauseInterval());
            } catch (InterruptedException ignore) {
                // ignore and continue
            }
            return true;
        }
        return false;
    }
}
