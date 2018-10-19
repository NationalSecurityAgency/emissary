package emissary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class CaseInsensitiveMapTest extends UnitTest {

    @Test
    public void testMapExtract() {
        Map<String, String> m = new CaseInsensitiveMap<String, String>(10, 0.75F);
        m.put("Content-Type", "The Value");
        String s = m.get("Content-type");
        assertNotNull("Insenstive extract from map", s);
        assertEquals("Insensitive extract value from map", "The Value", s);
    }

    @Test
    public void testMapInsert() {
        Map<String, String> m = new CaseInsensitiveMap<String, String>();
        m.put("Content-Type", "The Value 1");
        m.put("Content-type", "The Value 2");
        assertEquals("Insenstive insert", 1, m.size());
        assertEquals("Insenstive last val overwrites", "The Value 2", m.get("CONTENT-TYPE"));
    }

    @Test
    public void testPutAll() {
        Map<String, String> m = new CaseInsensitiveMap<String, String>();
        Map<String, String> p = new HashMap<String, String>();
        p.put("Foo", "Bar");
        p.put("Bar", "Foo");
        m.putAll(p);
        assertEquals("Putall adds all elements", 2, m.size());
    }

    @Test
    public void testCopyConstructor() {
        Map<String, String> p = new HashMap<String, String>();
        p.put("Foo", "Bar");
        p.put("Bar", "Foo");
        Map<String, String> m = new CaseInsensitiveMap<String, String>(p);
        assertEquals("Copy constructor adds all elements", 2, m.size());
    }

    @Test
    public void testUsingNonStringKeys() {
        Map<Object, Object> m = new CaseInsensitiveMap<Object, Object>(10);
        m.put("Foo", "Bar");
        assertTrue("ContainsKey must work on case-insensitivity", m.containsKey("foo"));
        assertEquals("Object map must use case-insensitivity for String keys", "Bar", m.get("foo"));
        emissary.util.Pair pair = new emissary.util.Pair("Foo", "Bar");
        m.put(pair, "Baz");
        assertTrue("Object map must hold non-String keys", m.containsKey(pair));
        assertEquals("Object map must still hold non-String keys", "Baz", m.get(pair));
    }
}
