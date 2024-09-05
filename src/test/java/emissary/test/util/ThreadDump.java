package emissary.test.util;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("AvoidObjectArrays")
public class ThreadDump {
    public static final String[] SYSTEM_THREADS = {"Reference Handler", "Finalizer", "Signal Dispatcher"};

    ThreadMXBean tmbean = ManagementFactory.getThreadMXBean();

    public ThreadInfo[] getThreadInfo() {
        final long[] tids = this.tmbean.getAllThreadIds();
        return this.tmbean.getThreadInfo(tids, Integer.MAX_VALUE);
    }

    public ThreadInfo[] getThreadInfo(final boolean excludeSystem) {
        final ThreadInfo[] t = getThreadInfo();
        final List<ThreadInfo> l = new ArrayList<>();
        if (excludeSystem) {
            for (final ThreadInfo ti : t) {
                if (ti == null) {
                    continue;
                }
                final String name = ti.getThreadName();
                boolean isSystem = false;
                for (final String sysname : SYSTEM_THREADS) {
                    if (sysname.equals(name)) {
                        isSystem = true;
                        break;
                    }
                }
                if (!isSystem) {
                    l.add(ti);
                }
            }
            return l.toArray(new ThreadInfo[0]);
        }
        return t;
    }

}
