package emissary.core;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformHistoryTest extends UnitTest {

    @Test
    void testSet() {
        String key1 = "UNKNOWN.FILE_PICK_UP.INPUT.http://localhost:8001/FilePickUpPlace$5050";
        String key2 = "KNOWN.COOL_STUFF.TRANSFORM.http://localhost:8001/CoolStuffPlace$5050";
        String key3 = "KNOWN.ONE_THING.ANALYZE.http://localhost:8001/DoOneThingPlace$5050";
        String key4 = "KNOWN.LAST_PLACE.VERIFY.http://localhost:8001/LastThingPlace$5050";

        var keySet1 = List.of(key1, key2);
        var keySet2 = List.of(key3, key4);

        // create, set and check the history
        TransformHistory th = new TransformHistory();
        th.set(keySet1);
        assertEquals(2, th.getHistory().size());
        assertTrue(th.get().contains(key1));
        assertTrue(th.get().contains(key2));

        // re-set and check that history is cleared
        th.set(keySet2);
        assertEquals(2, th.getHistory().size());
        assertTrue(th.get().contains(key3));
        assertTrue(th.get().contains(key4));

        // set using object
        TransformHistory historyObject = new TransformHistory();
        historyObject.set(keySet1);
        th.set(historyObject);
        assertEquals(2, th.getHistory().size());
        assertTrue(th.get().contains(key1));
        assertTrue(th.get().contains(key2));

        // create using object
        TransformHistory newHistory = new TransformHistory(historyObject);
        assertEquals(2, newHistory.getHistory().size());
        assertTrue(newHistory.get().contains(key1));
        assertTrue(newHistory.get().contains(key2));
    }

    @Test
    void testAppend() {
        String key1 = "UNKNOWN.FILE_PICK_UP.INPUT.http://localhost:8001/FilePickUpPlace$5050";
        String key2 = "KNOWN.COOL_STUFF.COORDINATE.http://localhost:8001/CoolStuffPlace$5050";
        String key3 = "KNOWN.ONE_THING.ANALYZE.http://localhost:8001/DoOneThingPlace$5050";
        var keyList = List.of(key1);

        // create and seed a transform history
        TransformHistory th = new TransformHistory();
        th.set(keyList);
        assertEquals(1, th.getHistory().size());
        assertTrue(th.get().contains(key1));

        // now append a value
        th.append(key2);
        assertEquals(2, th.getHistory().size());
        assertTrue(th.get().contains(key2));
        assertTrue(th.getHistory().get(1).getCoordinated().isEmpty());

        // now append a coordinated value
        th.append(key3, true);
        assertEquals(2, th.getHistory().size());
        assertFalse(th.get().contains(key3)); // does not include coordinated results
        assertTrue(th.get(true).contains(key3));
        assertEquals(key3, th.getHistory().get(1).getCoordinated().get(0));
    }

    @Test
    void testVisitation() {
        String key1 = "UNKNOWN.FILE_PICK_UP.INPUT.http://localhost:8001/FilePickUpPlace$5050";
        String key2 = "KNOWN.COOL_STUFF.COORDINATE.http://localhost:8001/CoolStuffPlace$5050";
        String key3 = "KNOWN.ONE_THING.ANALYZE.http://localhost:8001/DoOneThingPlace$5050";
        String key4 = "KNOWN.LAST_PLACE.VERIFY.http://localhost:8001/LastThingPlace$5050";

        TransformHistory th = new TransformHistory();

        // set and check the history when last place visited was coordinated
        th.append(key1);
        th.append(key2);
        th.append(key3, true);
        assertEquals(2, th.getHistory().size());

        assertEquals(key2, th.lastVisit().getKey()); // skips the coordinated place
        assertEquals(key1, th.penultimateVisit().getKey());

        // set and check the history with the last place visited was not coordinated
        th.append(key4);
        assertEquals(3, th.getHistory().size());

        assertEquals(key4, th.lastVisit().getKey());
        assertEquals(key2, th.penultimateVisit().getKey()); // skips the coordinated place

        // check if a place has been visited
        assertFalse(th.hasVisited("*.ONE_THING.*.*")); // coordinated place is ignored
        assertTrue(th.hasVisited("*.LAST_PLACE.*.*"));
        assertFalse(th.hasVisited("*.NEVER_PLACE.*.*"));

        // clear and check for null
        th.clear();
        assertNull(th.lastVisit());
        assertNull(th.penultimateVisit());
    }

    @Test
    void testBeforeStart() {
        String key1 = "UNKNOWN.FILE_PICK_UP.INPUT.http://localhost:8001/FilePickUpPlace$5050";
        String key2 = "KNOWN.COOL_STUFF.TRANSFORM.http://localhost:8001/CoolStuffPlace$5050";
        String key3 = "*.*.<SPROUT>.http://localhost:8001/CoolStuffPlace$0";
        String key4 = "KNOWN.LAST_PLACE.VERIFY.http://localhost:8001/LastThingPlace$5050";
        var keyList = List.of(key1, key2, key3);

        TransformHistory th = new TransformHistory();

        // empty should return true
        assertTrue(th.beforeStart());

        // just one key, no sprout
        th.append(key1);
        assertFalse(th.beforeStart());

        // history with a sprout as the last item
        th.set(keyList);
        assertTrue(th.beforeStart());

        // processing after sprout
        th.append(key4);
        assertFalse(th.beforeStart());
    }

    @Test
    void testToString() {
        String key1 = "UNKNOWN.FILE_PICK_UP.INPUT.http://localhost:8001/FilePickUpPlace$5050";
        String key2 = "KNOWN.COOL_STUFF.TRANSFORM.http://localhost:8001/CoolStuffPlace$5050";
        String key3 = "*.*.<SPROUT>.http://localhost:8001/CoolStuffPlace$0";
        String key4 = "KNOWN.COOL_STUFF.COORDINATE.http://localhost:8001/CoolStuffPlace$5050";
        String key5 = "KNOWN.ONE_THING.ANALYZE.http://localhost:8001/DoOneThingPlace$5050";
        String key6 = "KNOWN.LAST_PLACE.VERIFY.http://localhost:8001/LastThingPlace$5050";

        String newline = System.getProperty("line.separator");
        StringBuilder expected = new StringBuilder();
        expected.append("transform history (6) :").append(newline);
        expected.append("        -> ").append(key1).append(newline);
        expected.append("        -> ").append(key2).append(newline);
        expected.append("        -> ").append(key3).append(newline);
        expected.append("        -> ").append(key4).append(newline);
        expected.append("           +--> ").append(key5).append(newline);
        expected.append("        -> ").append(key6).append(newline);

        TransformHistory th = new TransformHistory();
        th.append(key1);
        th.append(key2);
        th.append(key3);
        th.append(key4);
        th.append(key5, true);
        th.append(key6);

        assertEquals(expected.toString(), th.toString());
    }

    @Test
    void testGetKeyNoUrl() {
        String key1 = "UNKNOWN.FILE_PICK_UP.INPUT.http://localhost:8001/FilePickUpPlace$5050";

        TransformHistory th = new TransformHistory();
        th.append(key1);

        assertEquals("UNKNOWN.FILE_PICK_UP.INPUT", th.getHistory().get(0).getKey(true));
    }
}
