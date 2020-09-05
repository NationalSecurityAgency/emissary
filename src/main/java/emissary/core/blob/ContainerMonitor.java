package emissary.core.blob;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Log occasional stats on Container usage.
 *
 */
public class ContainerMonitor {

    /** Singleton */
    private static volatile ContainerMonitor instance;

    /**
     * Get the singleton
     *
     * @return the singleton
     */
    public static ContainerMonitor getInstance() {
        ContainerMonitor temp = instance;
        if (temp == null) {
            synchronized (ContainerMonitor.class) {
                temp = instance;
                if (temp == null) {
                    instance = new ContainerMonitor();
                    temp = instance;
                }
            }
        }
        return temp;
    }

    /** log */
    private final Logger log = LoggerFactory.getLogger(ContainerMonitor.class);
    /** References that have been seen */
    private final ConcurrentSkipListSet<ContainerReference> refs = new ConcurrentSkipListSet<>();
    /** References that have been garbage collected. */
    ReferenceQueue<IDataContainer> refQueue = new ReferenceQueue<>();

    /**
     * Initialise resources and start the daemon thread.
     */
    private ContainerMonitor() {
        Runnable task = new Runnable() {

            @Override
            public void run() {
                while (true) {
                    try {
                        ContainerReference ref;
                        while ((ref = (ContainerReference) refQueue.poll()) != null) {
                            refs.remove(ref);
                        }
                        Map<Class<? extends IDataContainer>, ContainerMetrics> metrics = new HashMap<>();
                        Iterator<ContainerReference> iter = refs.iterator();
                        while (iter.hasNext()) {
                            ref = iter.next();
                            IDataContainer cont = ref.get();
                            if (cont == null) {
                                iter.remove();
                                continue;
                            }
                            ContainerMetrics m = metrics.get(cont.getClass());
                            if (m == null) {
                                m = new ContainerMetrics();
                                metrics.put(cont.getClass(), m);
                            }
                            m.count++;
                            m.totalSize += cont.length();
                        }
                        log.info("Current Containers: {}", metrics);
                        Thread.sleep(10000);
                    } catch (Exception e) {
                        log.error("Problem with container monitoring.", e);
                    }
                }
            }
        };
        Thread t = new Thread(task);
        t.setName("Emissary Container Monitoring");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /**
     * Register a container to be monitored.
     *
     * @param container The container to monitor.
     */
    public void register(IDataContainer container) {
        refs.add(new ContainerReference(container));
    }

    private class ContainerReference extends WeakReference<IDataContainer> implements Comparable<ContainerReference> {

        public long registeredTime = System.currentTimeMillis();
        public int origHash;

        public ContainerReference(IDataContainer referent) {
            super(referent, refQueue);
            origHash = referent.hashCode();
        }

        @Override
        public int hashCode() {
            return origHash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ContainerReference other = (ContainerReference) obj;
            if (origHash != other.origHash) {
                return false;
            }
            return registeredTime == other.registeredTime;
        }

        @Override
        public int compareTo(ContainerReference o) {
            int compare = Long.compare(registeredTime, o.registeredTime);
            if (compare == 0) {
                compare = hashCode() - o.hashCode();
            }
            if (compare == 0) {
                compare = toString().compareTo(o.toString());
            }
            return compare;
        }


    }

    private static class ContainerMetrics {
        public int count;
        public long totalSize;

        @Override
        public String toString() {
            return count + " instances totalling " + totalSize;
        }
    }
}
