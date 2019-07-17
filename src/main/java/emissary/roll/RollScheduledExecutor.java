package emissary.roll;

import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduled Executor to support unhandled execution Exceptions. The intention of Rollable tasks is that they run for
 * the life of the process without exception. We don't track the individual futures to reduce complexity and instead
 * will log uncaught errors here.
 */
public class RollScheduledExecutor extends ScheduledThreadPoolExecutor {
    static final Logger log = LoggerFactory.getLogger(RollScheduledExecutor.class);

    public RollScheduledExecutor(int corePoolSize, ThreadFactory threadFactory) {
        super(corePoolSize, threadFactory);
    }

    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(Runnable runnable, RunnableScheduledFuture<V> task) {
        if (runnable instanceof Roller) {
            return new RollFuture<>(task, (Roller) runnable);
        } else {
            return super.decorateTask(runnable, task);
        }
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        if (r instanceof RollFuture) {
            RollFuture<?> f = (RollFuture<?>) r;
            try {
                // we don't want to block if we're scheduled for another run
                if (f.isDone()) {
                    f.get();
                }
            } catch (InterruptedException ex) {
                // shouldn't happen;
                log.warn("Thread Interrupted", ex);
            } catch (ExecutionException ee) {
                Throwable ex = ee.getCause();
                Rollable rollable = f.r.getRollable();
                log.error("Unhandled Throwable in Rollable, {}. To String: {}", rollable.getClass(), rollable.toString(), ex);
            }
        }
    }

    /**
     * Wrapper for the Future to give us a handle to our Rollable object.
     */
    static class RollFuture<V> implements RunnableScheduledFuture<V> {
        private final RunnableScheduledFuture<V> rsf;
        final Roller r;

        public RollFuture(RunnableScheduledFuture<V> rsf, Roller r) {
            this.rsf = rsf;
            this.r = r;
        }

        @Override
        public boolean isPeriodic() {
            return rsf.isPeriodic();
        }

        @Override
        public void run() {
            rsf.run();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return rsf.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return rsf.isCancelled();
        }

        @Override
        public boolean isDone() {
            return rsf.isDone();
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            return rsf.get();
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return rsf.get(timeout, unit);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return rsf.getDelay(unit);
        }

        @Override
        public int compareTo(Delayed o) {
            return rsf.compareTo(o);
        }

    }


}
