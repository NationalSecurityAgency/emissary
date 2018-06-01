package emissary.util;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

public class ThreadDump {
    public static final String[] SYSTEM_THREADS = {"Reference Handler", "Finalizer", "Signal Dispatcher"};

    ThreadMXBean tmbean = ManagementFactory.getThreadMXBean();

    public ThreadInfo[] getDeadlockedThreadInfo() {
        final long[] tids = this.tmbean.findMonitorDeadlockedThreads();
        if (tids != null) {
            return this.tmbean.getThreadInfo(tids, Integer.MAX_VALUE);
        }
        return new ThreadInfo[0];
    }

    public ThreadInfo[] getThreadInfo() {
        final long[] tids = this.tmbean.getAllThreadIds();
        return this.tmbean.getThreadInfo(tids, Integer.MAX_VALUE);
    }

    public ThreadInfo[] getThreadInfo(final boolean excludeSystem) {
        final ThreadInfo[] t = getThreadInfo();
        final List<ThreadInfo> l = new ArrayList<ThreadInfo>();
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

    public void printThreadDump() {
        ThreadInfo[] tinfos = getDeadlockedThreadInfo();
        if (tinfos.length > 0) {
            System.out.println(">> Deadlocked Threads:");
            for (final ThreadInfo ti : tinfos) {
                printThreadInfo(ti);
            }
            System.out.println();
        }

        tinfos = getThreadInfo();
        if (tinfos.length > 0) {
            System.out.println(">> Thread Dump");
            for (final ThreadInfo ti : tinfos) {
                printThreadInfo(ti);
            }
        }
    }

    static void printThreadInfo(final ThreadInfo ti) {
        System.out.println("\"" + ti.getThreadName() + "\" tid=" + ti.getThreadId());
        System.out.println("   thread state " + ti.getThreadState()
                + (ti.getLockName() != null ? (" (on " + ti.getLockName() + " owned by " + ti.getLockOwnerId() + ")") : ""));
        if (ti.isSuspended()) {
            System.out.println("   SUSPENDED");
        }
        if (ti.isInNative()) {
            System.out.println("   IN NATIVE CODE");
        }
        for (final StackTraceElement ste : ti.getStackTrace()) {
            System.out.println("      " + ste.toString());
        }
    }
}
