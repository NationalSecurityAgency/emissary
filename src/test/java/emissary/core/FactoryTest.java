package emissary.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import emissary.test.core.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FactoryTest extends UnitTest {
    @Override
    @BeforeEach
    public void setUp() throws Exception {

    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        for (String s : Namespace.keySet()) {
            Namespace.unbind(s);
        }
    }

    @Test
    void testNullArg() {
        Object[] args = {"Here is a string", null};
        Object o = Factory.create("java.lang.Throwable", args);
        assertNotNull(o, "Factory creation");
        assertTrue(o instanceof java.lang.Throwable, "Type of object");
    }

    @Test
    void testWithSubclassArg() {
        NamespaceException ex = new NamespaceException("This is a test");
        Object[] args = {"Here is a string", ex};
        Object o = Factory.create("java.lang.Throwable", args);
        assertNotNull(o, "Factory creation");
        assertTrue(o instanceof java.lang.Throwable, "Type of object");
    }

    @Test
    void testWithVarArgs() {
        Object o = Factory.create("java.lang.Throwable", "Here is a string");
        assertNotNull(o, "Factory vararg creation");
        assertTrue(o instanceof java.lang.Throwable, "Type of object on vararg create");
    }

    @Test
    void testWithVarArgsAndNull() {
        Object o = Factory.create("java.lang.Throwable", "Here is a string", null);
        assertNotNull(o, "Factory vararg with null");
        assertTrue(o instanceof java.lang.Throwable, "Type of object on vararg with null");
    }

    @Test
    void testWithVarArgsAndBogusCtor() {
        assertThrows(Error.class, () -> Factory.create("java.lang.Throwable", 1, 2, 3, 4));
    }

    @Test
    void testWithBogusSubclassArg() {
        Object[] args = {"Here is a string", 5};
        assertThrows(Error.class, () -> Factory.create("java.lang.Throwable", args));
    }

    @Test
    void testBogusConstructor() {
        Object[] args = {5, 6, 7, 8};
        assertThrows(Error.class, () -> Factory.create("java.lang.Integer", args));
    }

    @Test
    void testCreateNoRegister() {
        Object[] args = {5};
        Object o = Factory.create("java.lang.Integer", args);
        assertNotNull(o, "Factory creation");
        assertTrue(o instanceof java.lang.Integer, "Type of object");
        for (String s : Namespace.keySet()) {
            fail("Namespace should be empty but has " + s);
        }
    }

    @Test
    void testCreateAndRegister() throws NamespaceException {
        String key = "http://host.domain.com:8001/thePlace";
        Object[] args = {5};
        Object o = Factory.create("java.lang.Integer", args, key);
        assertNotNull(o, "Factory creation");
        assertTrue(o instanceof java.lang.Integer, "Type of object");
        Object o2 = Namespace.lookup(key);
        assertNotNull(o2, "Registered object");
        assertTrue(o2 instanceof java.lang.Integer, "Type of registered object");
        assertEquals(o, o2, "Creation matches registration");
    }

    @Test
    void testSelfBindingInstanceWithVarargs() throws NamespaceException {
        String key = "http://host.domain.com:8001/SelfBindingInstance";
        Object o = Factory.createV("emissary.core.FactoryTest$SelfBinder", key, key);
        Object o2 = Namespace.lookup(key);
        assertNotNull(o2, "Self registered object should be found");
        assertTrue(o2 instanceof SelfBinder, "Type of self registered object");
        assertEquals(o2, o, "Proper instance should be self bound");
        Namespace.unbind(key);
    }

    @Test
    void testSelfBindingInstanceWithVarargsButNoFactoryKey() throws NamespaceException {
        String key = "http://host.domain.com:8001/SelfBindingInstance";
        Object o = Factory.create("emissary.core.FactoryTest$SelfBinder", key);
        Object o2 = Namespace.lookup(key);
        assertNotNull(o2, "Self registered object should be found");
        assertTrue(o2 instanceof SelfBinder, "Type of self registered object");
        assertEquals(o2, o, "Proper instance should be self bound");
        Namespace.unbind(key);
    }

    @Test
    void testFactoryBindingInstanceWithVarargs() throws NamespaceException {
        String key = "http://host.domain.com:8001/FactoryBindingInstance";
        Object o = Factory.createV("emissary.core.FactoryTest$FactoryBinder", key, key);
        Object o2 = Namespace.lookup(key);
        assertNotNull(o2, "Factory registered object should be found");
        assertTrue(o2 instanceof FactoryBinder, "Type of factory registered object");
        assertEquals(o2, o, "Proper instance should be bound by factory");
        Namespace.unbind(key);
    }

    @Test
    void testSelfBindingInstanceWithoutVarargs() throws NamespaceException {
        String key = "http://host.domain.com:8001/SelfBindingInstance";
        Object o = Factory.create("emissary.core.FactoryTest$SelfBinder", new Object[] {key}, key);
        Object o2 = Namespace.lookup(key);
        assertNotNull(o2, "Self registered object should be found");
        assertTrue(o2 instanceof SelfBinder, "Type of self registered object");
        assertEquals(o2, o, "Proper instance should be self bound");
        Namespace.unbind(key);
    }

    @Test
    void testNonExistentClassName() {
        assertThrows(Error.class, () -> Factory.create("foo.bar.baz.Quux"));
    }

    @Test
    void testCreatingAnInterface() {
        assertThrows(Error.class, () -> Factory.create("emissary.core.IBaseDataObject"));
    }

    @Test
    void testCreatingAnAbstractClass() {
        assertThrows(Error.class, () -> Factory.create("emissary.id.IdPlace"));
    }

    @Test
    void testCreatingFromAPrivateConstructor() {
        assertDoesNotThrow(() -> Factory.create("emissary.core.Factory"));
    }

    @Test
    void testCreatingFromConstructorThatThrowsThrowable() {
        assertThrows(Error.class, () -> Factory.create("emissary.core.FactoryTest$DemoClassThatThrowsThrowableFromConstructor"));
    }

    @Test
    void testFactoryBindingInstanceWithoutVarargs() throws NamespaceException {
        String key = "http://host.domain.com:8001/FactoryBindingInstance";
        Object o = Factory.create("emissary.core.FactoryTest$FactoryBinder", new Object[] {key}, key);
        Object o2 = Namespace.lookup(key);
        assertNotNull(o2, "Factory registered object should be found");
        assertTrue(o2 instanceof FactoryBinder, "Type of factory registered object");
        assertEquals(o2, o, "Proper instance should be factory bound");
        Namespace.unbind(key);
    }

    public static class SelfBinder {
        private final String myKey;

        public SelfBinder(String myKey) {
            this.myKey = myKey;
            // Register myself with the namespace
            Namespace.bind(myKey, this);
        }

        public String getKey() {
            return myKey;
        }
    }

    public static class FactoryBinder {
        private final String myKey;

        public FactoryBinder(String myKey) {
            this.myKey = myKey;
        }

        public String getKey() {
            return myKey;
        }
    }

    static class DemoClassThatThrowsThrowableFromConstructor {
        public DemoClassThatThrowsThrowableFromConstructor() throws Throwable {
            throw new Throwable("Bogus");
        }
    }
}
