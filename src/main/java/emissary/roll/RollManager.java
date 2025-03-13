package emissary.roll;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RollManager handles all incremental rolls for configured objects within the framework
 */
public class RollManager implements PropertyChangeListener {
    static final Logger log = LoggerFactory.getLogger(RollManager.class);
    public static final String CFG_ROLL_MANAGER_THREADS = "ROLL_MANAGER_THREADS";
    int executorThreadCount = 10;
    ScheduledThreadPoolExecutor exec;

    final HashSet<Roller> rollers = new HashSet<>();
    // SINGLETON
    @Nullable
    @SuppressWarnings("NonFinalStaticField")
    private static RollManager rollManager;

    protected RollManager() {
        init();
    }

    protected RollManager(Configurator configG) {
        init(configG);
    }

    /**
     * Load the configurator
     */
    @SuppressWarnings("SystemExitOutsideMain")
    protected void init() {
        try {
            Configurator configG = ConfigUtil.getConfigInfo(this.getClass());
            init(configG);
        } catch (IOException ex) {
            if (ex.getMessage().startsWith("No config stream available")) {
                log.info("No Rollables configured in the default configuration");
            } else {
                log.warn("Unable to configure RollManager from Configurator.", ex);
            }
            System.exit(1);
        }
    }

    protected void init(Configurator configG) {
        ArrayList<Roller> cfgRollers = new ArrayList<>();
        executorThreadCount = configG.findIntEntry(CFG_ROLL_MANAGER_THREADS, executorThreadCount);
        for (String roller : configG.findEntries("ROLLABLE")) {
            try {
                Map<String, String> map = configG.findStringMatchMap(roller + "_");
                cfgRollers.add(RollUtil.buildRoller(map));
            } catch (RuntimeException e) {
                log.warn("Unable to configure Rollable for: {}", roller);
            }
        }

        exec = new RollScheduledExecutor(executorThreadCount, new RMThreadFactory());
        for (Roller r : cfgRollers) {
            addRoller(r);
        }
    }

    public final void addRoller(Roller r) {
        boolean time = r.getTimeUnit() != null && r.getPeriod() > 0L;
        boolean progress = r.getMax() > 0;
        if (time) {
            if (log.isInfoEnabled()) {
                log.info("Scheduling Rollable {} at {} {}", r.getRollable().getClass(), r.getPeriod(), r.getTimeUnit().name());
            }
            var unused = exec.scheduleAtFixedRate(r, r.getPeriod(), r.getPeriod(), r.getTimeUnit());
        }
        if (progress) {
            r.addPropertyChangeListener(this);
        }
        if (time || progress) {
            rollers.add(r);
        } else {
            log.error("Roller not scheduled. Time or progress must be set: Class={} Max={} Interval={} {}", r.getClass().getName(), r.getMax(),
                    r.getPeriod(), r.getTimeUnit());
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (rollers.contains((Roller) evt.getNewValue())) {
            Roller r = (Roller) evt.getNewValue();
            // only schedule one time when we're notified
            if (r.setProgressScheduled()) {
                exec.execute((Roller) evt.getNewValue());
            }

        }
    }

    /**
     * Synchronized on RM to prevent multiple returns on RollManager
     */
    public static synchronized RollManager getManager() {
        if (rollManager == null) {
            rollManager = new RollManager();
        }
        return rollManager;
    }

    /**
     * Synchronized on RM to prevent multiple returns on RollManager
     * <p>
     * Used to create custom RollManager based on configs.
     */
    public static synchronized RollManager getManager(Configurator configG) {
        if (rollManager == null) {
            rollManager = new RollManager(configG);
        }
        return rollManager;
    }

    public static void shutdown() {
        rollManager.exec.shutdown();
        log.info("Closing all rollers ({})", rollManager.rollers.size());
        for (Roller roller : rollManager.rollers) {
            Rollable r = roller.getRollable();
            try {
                r.roll();
                r.close();
            } catch (IOException ex) {
                log.warn("Error while closing Rollable: {}", r.getClass(), ex);
            }
        }
        rollManager = null;
    }

    private static final class RMThreadFactory implements ThreadFactory {
        final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "RollManager-daemon-" + count.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
