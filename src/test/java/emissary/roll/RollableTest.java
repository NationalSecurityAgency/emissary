package emissary.roll;

import emissary.test.core.junit5.UnitTest;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class RollableTest extends UnitTest implements Rollable, PropertyChangeListener {
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
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getNewValue() instanceof Roller) {
            Roller r = (Roller) evt.getNewValue();

            updateCount++;
        }
    }

    public long getUpdateCount() {
        return updateCount;
    }

}
