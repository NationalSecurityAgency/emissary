package emissary.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;

import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.Test;

class CounterSetTest extends UnitTest {

    @Test
    void testKeysReturned() {
        CounterSet c = new CounterSet();
        c.addKey("FOO");
        c.addKey("BAR");
        Set<String> keys = c.getKeys();
        assertEquals(2, keys.size(), "Key size must be correct");
    }

    @Test
    void testIncrement() {
        CounterSet c = new CounterSet();
        c.addKey("FOO");
        c.increment("FOO");
        assertEquals(1, (int) c.get("FOO"), "Key increment");
    }

    @Test
    void testIncrementOnNonExistingKeyWithoutFlexEntryFails() {
        CounterSet c = new CounterSet();
        c.increment("BAR");
        assertNull(c.get("BAR"), "Key must not exist");
    }

    @Test
    void testIncrementOnNonExistingKeyWithFlexEntry() {
        CounterSet c = new CounterSet(true);
        c.increment("BAR");
        assertEquals(1, (int) c.get("BAR"), "Key must exist");
    }

    @Test
    void testAdd() {
        CounterSet c = new CounterSet(true);
        c.add("BAR", 10);
        assertEquals(10, (int) c.get("BAR"), "Key must have value");
    }

}
