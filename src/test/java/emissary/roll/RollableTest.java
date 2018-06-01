package emissary.roll;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import emissary.test.core.UnitTest;

public class RollableTest extends UnitTest implements Rollable, Observer {
    boolean wasRolled;
    long updateCount;
    ArrayBlockingQueue<Object> x = new ArrayBlockingQueue<>(1);

    public RollableTest() {}

    @Override
    public void roll() {
        x.offer(new Object());
        wasRolled = true;
    }

    @Override
    public boolean isRolling() {
        return false;
    }

    public Object waitForThread() throws InterruptedException {
        // this is for testing.
        return x.poll(3L, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws IOException {}

    @SuppressWarnings("unused")
    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof Roller) {
            Roller r = (Roller) o;

            updateCount++;
        }
    }

    public long getUpdateCount() {
        return updateCount;
    }

}
