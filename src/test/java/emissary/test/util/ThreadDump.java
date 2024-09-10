package emissary.test.util;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ThreadDump {
    public static final List<String> SYSTEM_THREADS = List.of("Reference Handler", "Finalizer", "Signal Dispatcher");

    ThreadMXBean tmbean = ManagementFactory.getThreadMXBean();

    public List<ThreadInfo> getThreadInfo() {
        final long[] tids = this.tmbean.getAllThreadIds();
        return Arrays.asList(this.tmbean.getThreadInfo(tids, Integer.MAX_VALUE));
    }

    public List<ThreadInfo> getThreadInfo(final boolean excludeSystem) {
        final List<ThreadInfo> t = getThreadInfo();
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
            return l;
        }
        return t;
    }

}
