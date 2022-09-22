package emissary.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.Test;

class CaseInsensitiveMapTest extends UnitTest {

    @Test
    void testMapExtract() {
        Map<String, String> m = new CaseInsensitiveMap<>(10, 0.75F);
        m.put("Content-Type", "The Value");
        String s = m.get("Content-type");
        assertNotNull(s, "Insenstive extract from map");
        assertEquals("The Value", s, "Insensitive extract value from map");
    }

    @Test
    void testMapInsert() {
        Map<String, String> m = new CaseInsensitiveMap<>();
        m.put("Content-Type", "The Value 1");
        m.put("Content-type", "The Value 2");
        assertEquals(1, m.size(), "Insenstive insert");
        assertEquals("The Value 2", m.get("CONTENT-TYPE"), "Insenstive last val overwrites");
    }

    @Test
    void testPutAll() {
        Map<String, String> m = new CaseInsensitiveMap<>();
        Map<String, String> p = new HashMap<>();
        p.put("Foo", "Bar");
        p.put("Bar", "Foo");
        m.putAll(p);
        assertEquals(2, m.size(), "Putall adds all elements");
    }

    @Test
    void testCopyConstructor() {
        Map<String, String> p = new HashMap<>();
        p.put("Foo", "Bar");
        p.put("Bar", "Foo");
        Map<String, String> m = new CaseInsensitiveMap<>(p);
        assertEquals(2, m.size(), "Copy constructor adds all elements");
    }

    @Test
    void testUsingNonStringKeys() {
        Map<Object, Object> m = new CaseInsensitiveMap<>(10);
        m.put("Foo", "Bar");
        assertTrue(m.containsKey("foo"), "ContainsKey must work on case-insensitivity");
        assertEquals("Bar", m.get("foo"), "Object map must use case-insensitivity for String keys");
        emissary.util.Pair pair = new emissary.util.Pair("Foo", "Bar");
        m.put(pair, "Baz");
        assertTrue(m.containsKey(pair), "Object map must hold non-String keys");
        assertEquals("Baz", m.get(pair), "Object map must still hold non-String keys");
    }
}
