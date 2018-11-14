package emissary.util;

import static org.junit.Assert.assertEquals;

import java.util.Set;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class CounterSetTest extends UnitTest {

    @Test
    public void testKeysReturned() {
        CounterSet c = new CounterSet();
        c.addKey("FOO");
        c.addKey("BAR");
        Set<String> keys = c.getKeys();
        assertEquals("Key size must be correct", 2, keys.size());
    }

    @Test
    public void testIncrement() {
        CounterSet c = new CounterSet();
        c.addKey("FOO");
        c.increment("FOO");
        assertEquals("Key increment", 1, (int) c.get("FOO"));
    }

    @Test
    public void testIncrementOnNonExistingKeyWithoutFlexEntryFails() {
        CounterSet c = new CounterSet();
        c.increment("BAR");
        assertEquals("Key must not exist", null, c.get("BAR"));
    }

    @Test
    public void testIncrementOnNonExistingKeyWithFlexEntry() {
        CounterSet c = new CounterSet(true);
        c.increment("BAR");
        assertEquals("Key must exist", 1, (int) c.get("BAR"));
    }

    @Test
    public void testAdd() {
        CounterSet c = new CounterSet(true);
        c.add("BAR", 10);
        assertEquals("Key must have value", 10, (int) c.get("BAR"));
    }

}
