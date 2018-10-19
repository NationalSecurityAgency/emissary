package emissary.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Iterator;

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
        for (Iterator<String> i = Namespace.keySet().iterator(); i.hasNext();) {
            Namespace.unbind(i.next());
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
        Object o;
        try {
            o = Factory.create("java.lang.Throwable", Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(4));
        } catch (Error expected) {
            return;
        }
        fail("Factory vararg on bogus args " + o);
    }

    @Test
    public void testWithBogusSubclassArg() {
        Object[] args = {"Here is a string", Integer.valueOf(5)};
        Object o;
        try {
            o = Factory.create("java.lang.Throwable", args);
        } catch (Error expected) {
            return;
        }
        fail("Factory creation should fail " + o);
    }

    @Test
    public void testBogusConstructor() {
        Object[] args = {Integer.valueOf(5), Integer.valueOf(6), Integer.valueOf(7), Integer.valueOf(8)};
        Object o;
        try {
            o = Factory.create("java.lang.Integer", args);
        } catch (Error expected) {
            return;
        }
        fail("Factory creation should fail " + o);
    }

    @Test
    public void testCreateNoRegister() {
        Object[] args = {Integer.valueOf(5)};
        Object o = Factory.create("java.lang.Integer", args);
        assertNotNull("Factory creation", o);
        assertTrue("Type of object", o instanceof java.lang.Integer);
        for (Iterator<String> i = Namespace.keySet().iterator(); i.hasNext();) {
            fail("Namespace should be empty but has " + i.next());
        }
    }

    @Test
    public void testCreateAndRegister() {
        String key = "http://host.domain.com:8001/thePlace";
        Object[] args = {Integer.valueOf(5)};
        Object o = Factory.create("java.lang.Integer", args, key);
        assertNotNull("Factory creation", o);
        assertTrue("Type of object", o instanceof java.lang.Integer);
        try {
            Object o2 = Namespace.lookup(key);
            assertNotNull("Registered object", o2);
            assertTrue("Type of registered object", o2 instanceof java.lang.Integer);
            assertEquals("Creation matches registration", o, o2);
        } catch (NamespaceException e) {
            fail("Registragion failed: " + e);
        }
    }

    @Test
    public void testSelfBindingInstanceWithVarargs() {
        String key = "http://host.domain.com:8001/SelfBindingInstance";
        Object o = Factory.createV("emissary.core.FactoryTest$SelfBinder", key, key);
        try {
            Object o2 = Namespace.lookup(key);
            assertNotNull("Self registered object should be found", o2);
            assertTrue("Type of self registered object", o2 instanceof SelfBinder);
            assertEquals("Proper instance should be self bound", o2, o);
        } catch (NamespaceException e) {
            fail("Registration failed: " + e);
        }
        Namespace.unbind(key);
    }

    @Test
    public void testSelfBindingInstanceWithVarargsButNoFactoryKey() {
        String key = "http://host.domain.com:8001/SelfBindingInstance";
        Object o = Factory.create("emissary.core.FactoryTest$SelfBinder", key);
        try {
            Object o2 = Namespace.lookup(key);
            assertNotNull("Self registered object should be found", o2);
            assertTrue("Type of self registered object", o2 instanceof SelfBinder);
            assertEquals("Proper instance should be self bound", o2, o);
        } catch (NamespaceException e) {
            fail("Registration failed: " + e);
        }
        Namespace.unbind(key);
    }

    @Test
    public void testFactoryBindingInstanceWithVarargs() {
        String key = "http://host.domain.com:8001/FactoryBindingInstance";
        Object o = Factory.createV("emissary.core.FactoryTest$FactoryBinder", key, key);
        try {
            Object o2 = Namespace.lookup(key);
            assertNotNull("Factory registered object should be found", o2);
            assertTrue("Type of factory registered object", o2 instanceof FactoryBinder);
            assertEquals("Proper instance should be bound by factory", o2, o);
        } catch (NamespaceException e) {
            fail("Registration failed: " + e);
        }
        Namespace.unbind(key);
    }

    @Test
    public void testSelfBindingInstanceWithoutVarargs() {
        String key = "http://host.domain.com:8001/SelfBindingInstance";
        Object o = Factory.create("emissary.core.FactoryTest$SelfBinder", new Object[] {key}, key);
        try {
            Object o2 = Namespace.lookup(key);
            assertNotNull("Self registered object should be found", o2);
            assertTrue("Type of self registered object", o2 instanceof SelfBinder);
            assertEquals("Proper instance should be self bound", o2, o);
        } catch (NamespaceException e) {
            fail("Registration failed: " + e);
        }
        Namespace.unbind(key);
    }

    @Test
    public void testNonExistentClassName() {
        try {
            Factory.create("foo.bar.baz.Quux");
        } catch (Error expected) {
            return;
        }
        fail("It should be harder to create non-existent classes");
    }

    @Test
    public void testCreatingAnInterface() {
        try {
            Factory.create("emissary.core.IBaseDataObject");
        } catch (Error expected) {
            return;
        }
        fail("It should be harder to create instances of an interface");
    }

    @Test
    public void testCreatingAnAbstractClass() {
        try {
            Factory.create("emissary.id.IdPlace");
        } catch (Error expected) {
        }
    }

    @Test
    public void testCreatingFromAPrivateConstructor() {
        try {
            Factory.create("emissary.core.Factory");
        } catch (Error expected) {
        }
    }

    @Test
    public void testCreatingFromConstructorThatThrowsThrowable() {
        try {
            Factory.create("emissary.core.FactoryTest$DemoClassThatThrowsThrowableFromConstructor");
        } catch (Error expected) {
        }
    }

    @Test
    public void testFactoryBindingInstanceWithoutVarargs() {
        String key = "http://host.domain.com:8001/FactoryBindingInstance";
        Object o = Factory.create("emissary.core.FactoryTest$FactoryBinder", new Object[] {key}, key);
        try {
            Object o2 = Namespace.lookup(key);
            assertNotNull("Factory registered object should be found", o2);
            assertTrue("Type of factory registered object", o2 instanceof FactoryBinder);
            assertEquals("Proper instance should be factory bound", o2, o);
        } catch (NamespaceException e) {
            fail("Registration failed: " + e);
        }
        Namespace.unbind(key);
    }

    public static class SelfBinder {
        private String myKey;

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
        private String myKey;

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
