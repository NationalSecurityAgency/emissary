package emissary.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FactoryTest extends UnitTest {
    @Override
    @Before
    public void setUp() throws Exception {

    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        for (String s : Namespace.keySet()) {
            Namespace.unbind(s);
        }
    }

    @Test
    public void testNullArg() {
        Object[] args = {"Here is a string", null};
        Object o = Factory.create("java.lang.Throwable", args);
        assertNotNull("Factory creation", o);
        assertTrue("Type of object", o instanceof java.lang.Throwable);
    }

    @Test
    public void testWithSubclassArg() {
        NamespaceException ex = new NamespaceException("This is a test");
        Object[] args = {"Here is a string", ex};
        Object o = Factory.create("java.lang.Throwable", args);
        assertNotNull("Factory creation", o);
        assertTrue("Type of object", o instanceof java.lang.Throwable);
    }

    @Test
    public void testWithVarArgs() {
        Object o = Factory.create("java.lang.Throwable", "Here is a string");
        assertNotNull("Factory vararg creation", o);
        assertTrue("Type of object on vararg create", o instanceof java.lang.Throwable);
    }

    @Test
    public void testWithVarArgsAndNull() {
        Object o = Factory.create("java.lang.Throwable", "Here is a string", null);
        assertNotNull("Factory vararg with null", o);
        assertTrue("Type of object on vararg with null", o instanceof java.lang.Throwable);
    }

    @Test
    public void testWithVarArgsAndBogusCtor() {
        assertThrows(Error.class, () -> Factory.create("java.lang.Throwable", 1, 2, 3, 4));
    }

    @Test
    public void testWithBogusSubclassArg() {
        Object[] args = {"Here is a string", 5};
        assertThrows(Error.class, () -> Factory.create("java.lang.Throwable", args));
    }

    @Test
    public void testBogusConstructor() {
        Object[] args = {5, 6, 7, 8};
        assertThrows(Error.class, () -> Factory.create("java.lang.Integer", args));
    }

    @Test
    public void testCreateNoRegister() {
        Object[] args = {5};
        Object o = Factory.create("java.lang.Integer", args);
        assertNotNull("Factory creation", o);
        assertTrue("Type of object", o instanceof java.lang.Integer);
        for (String s : Namespace.keySet()) {
            fail("Namespace should be empty but has " + s);
        }
    }

    @Test
    public void testCreateAndRegister() throws NamespaceException {
        String key = "http://host.domain.com:8001/thePlace";
        Object[] args = {5};
        Object o = Factory.create("java.lang.Integer", args, key);
        assertNotNull("Factory creation", o);
        assertTrue("Type of object", o instanceof java.lang.Integer);
        Object o2 = Namespace.lookup(key);
        assertNotNull("Registered object", o2);
        assertTrue("Type of registered object", o2 instanceof java.lang.Integer);
        assertEquals("Creation matches registration", o, o2);
    }

    @Test
    public void testSelfBindingInstanceWithVarargs() throws NamespaceException {
        String key = "http://host.domain.com:8001/SelfBindingInstance";
        Object o = Factory.createV("emissary.core.FactoryTest$SelfBinder", key, key);
        Object o2 = Namespace.lookup(key);
        assertNotNull("Self registered object should be found", o2);
        assertTrue("Type of self registered object", o2 instanceof SelfBinder);
        assertEquals("Proper instance should be self bound", o2, o);
        Namespace.unbind(key);
    }

    @Test
    public void testSelfBindingInstanceWithVarargsButNoFactoryKey() throws NamespaceException {
        String key = "http://host.domain.com:8001/SelfBindingInstance";
        Object o = Factory.create("emissary.core.FactoryTest$SelfBinder", key);
        Object o2 = Namespace.lookup(key);
        assertNotNull("Self registered object should be found", o2);
        assertTrue("Type of self registered object", o2 instanceof SelfBinder);
        assertEquals("Proper instance should be self bound", o2, o);
        Namespace.unbind(key);
    }

    @Test
    public void testFactoryBindingInstanceWithVarargs() throws NamespaceException {
        String key = "http://host.domain.com:8001/FactoryBindingInstance";
        Object o = Factory.createV("emissary.core.FactoryTest$FactoryBinder", key, key);
        Object o2 = Namespace.lookup(key);
        assertNotNull("Factory registered object should be found", o2);
        assertTrue("Type of factory registered object", o2 instanceof FactoryBinder);
        assertEquals("Proper instance should be bound by factory", o2, o);
        Namespace.unbind(key);
    }

    @Test
    public void testSelfBindingInstanceWithoutVarargs() throws NamespaceException {
        String key = "http://host.domain.com:8001/SelfBindingInstance";
        Object o = Factory.create("emissary.core.FactoryTest$SelfBinder", new Object[] {key}, key);
        Object o2 = Namespace.lookup(key);
        assertNotNull("Self registered object should be found", o2);
        assertTrue("Type of self registered object", o2 instanceof SelfBinder);
        assertEquals("Proper instance should be self bound", o2, o);
        Namespace.unbind(key);
    }

    @Test
    public void testNonExistentClassName() {
        assertThrows(Error.class, () -> Factory.create("foo.bar.baz.Quux"));
    }

    @Test
    public void testCreatingAnInterface() {
        assertThrows(Error.class, () -> Factory.create("emissary.core.IBaseDataObject"));
    }

    @Test
    public void testCreatingAnAbstractClass() {
        assertThrows(Error.class, () -> Factory.create("emissary.id.IdPlace"));
    }

    @Test
    public void testCreatingFromAPrivateConstructor() {
        Factory.create("emissary.core.Factory");
    }

    @Test
    public void testCreatingFromConstructorThatThrowsThrowable() {
        assertThrows(Error.class, () -> Factory.create("emissary.core.FactoryTest$DemoClassThatThrowsThrowableFromConstructor"));
    }

    @Test
    public void testFactoryBindingInstanceWithoutVarargs() throws NamespaceException {
        String key = "http://host.domain.com:8001/FactoryBindingInstance";
        Object o = Factory.create("emissary.core.FactoryTest$FactoryBinder", new Object[] {key}, key);
        Object o2 = Namespace.lookup(key);
        assertNotNull("Factory registered object should be found", o2);
        assertTrue("Type of factory registered object", o2 instanceof FactoryBinder);
        assertEquals("Proper instance should be factory bound", o2, o);
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
