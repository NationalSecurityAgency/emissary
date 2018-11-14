/* $Id$ */
package emissary.util;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;

import emissary.util.io.ReadOutput;

public class WatcherThread extends Thread {
    private Process proc = null;
    private int delay = 1000;
    private boolean flag = true;

    public WatcherThread() {}

    public WatcherThread(Process pp) {
        proc = pp;
    }

    public void setDelay(int nn) {
        delay = nn;
    }

    public void setProcess(Process pp) {
        proc = pp;
    }

    public void killMe() {
        flag = false;
    }

    /**
     * I've chosen to use an internal flag (called flag) to represent the state of the running thread. Since this thread
     * spends most of its time sleeping, this will work fine. Otherwise, we'd have to use the interrupt method of the
     * thread.
     */
    @Override
    public void run() {
        int nTries = 10;
        int partialDelay = delay / nTries;
        int ii = 0;

        while (flag && (ii++ < nTries)) {
            if (proc != null) {
                try {
                    sleep(partialDelay);

                    if (ii == nTries) {
                        try {
                            proc.exitValue();
                        } catch (IllegalThreadStateException itsx) {
                            proc.destroy();
                            proc = null;
                            flag = false;
                        }
                    }
                } catch (InterruptedException ix) {
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        WatcherThread wt = new WatcherThread();
        String name = wt.getClass().getName();

        if (args.length != 2) {
            System.out.println("usage: " + name + " time cmd");

            return;
        }

        wt.setDelay(Integer.parseInt(args[0]));

        System.out.println(name + " begins at " + new Date());

        Process proc = Runtime.getRuntime().exec(args[1]);

        wt.setProcess(proc);

        PrintStream out = null;
        PrintStream err = null;

        out = System.out;
        err = System.err;

        Thread t1 = new Thread(new ReadOutput(proc.getInputStream(), name, out));
        Thread t2 = new Thread(new ReadOutput(proc.getErrorStream(), name, err));

        wt.start();
        t1.start();
        t2.start();

        try {
            proc.waitFor();
            wt.killMe();
            t1.join();
            t2.join();
        } catch (InterruptedException ix) {
        }

        System.out.println(name + " ends at " + new Date());

        return;
    }
}
