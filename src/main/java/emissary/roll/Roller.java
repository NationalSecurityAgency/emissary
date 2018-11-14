package emissary.roll;

import java.util.Observable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stateful object for the RollManager to track progress of a provided Rollable.
 */
public class Roller extends Observable implements Runnable {

    static final Logger log = LoggerFactory.getLogger(Roller.class);

    /** Constant to refer to roll interval in config files **/
    public static final String CFG_ROLL_INTERVAL = "ROLL_INTERVAL";

    private final long max;
    private volatile long progress;
    private final TimeUnit t;
    private final long period;
    private final Rollable r;
    private final ReentrantLock lock = new ReentrantLock();
    private long lastRun;
    private final AtomicBoolean progressSchedule = new AtomicBoolean(false);

    public Roller(long max, TimeUnit t, long period, Rollable r) {
        this.max = max;
        this.t = t;
        this.period = period;
        this.r = r;
    }

    public Roller(TimeUnit t, long period, Rollable r) {
        this(0, t, period, r);
    }

    public final long incrementProgress() {
        return incrementProgress(1L);
    }

    public final long incrementProgress(long val) {
        try {
            lock.lock();
            progress += val;
            if (progress >= max) {
                setChanged();
                notifyObservers();
            }
            return progress;
        } finally {
            lock.unlock();
        }
    }

    public final long getMax() {
        return max;
    }

    public final long getProgress() {
        return progress;
    }

    public final TimeUnit getTimeUnit() {
        return t;
    }

    public final long getPeriod() {
        return period;
    }

    public final Rollable getRollable() {
        return r;
    }

    /*
     * There is the potential that we could lose some progress during a roll since this method is called immediately after a
     * roll. Places should control that behavior via internal locking if necessary to maintain exact progress.
     */
    protected void resetProgress(long start) {
        try {
            lock.lock();
            progress = 0;
            lastRun = start;
        } finally {
            lock.unlock();
        }
    }

    public final long getLastRun() {
        return lastRun;
    }

    /* returns true if we set this flag for progress execution */
    protected boolean setProgressScheduled() {
        return progressSchedule.compareAndSet(false, true);
    }

    @Override
    public void run() {
        try {
            long start = System.currentTimeMillis();
            if (r.isRolling()) {
                log.debug("Rollable target {} already rolling", r.getClass());
                return;
            }
            if (!shouldRoll(start)) {
                return;
            }
            log.debug("Beginning roll for {}", r.getClass());
            r.roll();
            long time = (System.currentTimeMillis() - start) / 1000L;
            resetProgress(start);
            log.info("Completed roll for {} in {} seconds", r.getClass(), time);
        } finally {
            progressSchedule.compareAndSet(true, false);
        }
    }

    /* Convert our scheduled interval to millis */
    private long getIntervalInMillis() {
        return TimeUnit.MILLISECONDS.convert(period, t);
    }

    /*
     * There are a couple of conditions where we would not want to execute when both a progress value and time schedule are
     * configured: - Time based roll happened and a progress run is scheduled - Progress roll happened between schduled runs
     */
    private boolean shouldRoll(long start) {
        // verify both time and progress are set
        // if we're below max we'll check the interval
        if ((period > 0 && max > 0) && progress < max) {
            // we fired a progress run or delayed start due to starvation. add 100 for clock skew
            if ((start - lastRun + 100) < getIntervalInMillis()) {
                return false;
            }
        }
        return true;
    }

}
