package emissary.core.view;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.MetadataDictionary;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.core.blob.IDataContainer;
import emissary.core.blob.MemoryDataContainer;
import emissary.util.ByteUtil;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized.class)
@SuppressWarnings("deprecation")
public class AllViewManagerTest {

    @Parameters(name = "{index}: {0}")
    public static Iterable<Object[]> getClassesToTest() {
        return Arrays.asList(new Object[][] {
                {ViewManager.class},
                {LegacyViewManagerWrapper.class}

        });
    }

    private Class<? extends IViewManager> classToTest;
    private IViewManager b;

    public AllViewManagerTest(Class<? extends IViewManager> classToTest) throws Exception {
        this.classToTest = classToTest;
    }

    @Before
    public void setup() throws Exception {
        if (classToTest == LegacyViewManagerWrapper.class) {
            b = IViewManager.wrap(new FakeLegacyManager());
        } else {
            b = classToTest.newInstance();
        }
    }

    @Test
    public void testAlternateViewCount() {
        this.b.addAlternateView("FOO", "abcd".getBytes());
        assertEquals("Number of alternate views failed", 1, this.b.getNumAlternateViews());
        this.b.addAlternateView("BAR", "abcd".getBytes());
        assertEquals("Number of alternate views failed to increment", 2, this.b.getNumAlternateViews());
    }


    @Test
    public void testAltViewRemapping() {
        try {
            final byte[] configData = ("RENAME_PROPERTIES = \"FLUBBER\"\n" + "RENAME_FOO =\"BAR\"\n").getBytes();

            final ByteArrayInputStream str = new ByteArrayInputStream(configData);
            final Configurator conf = ConfigUtil.getConfigInfo(str);
            MetadataDictionary.initialize(MetadataDictionary.DEFAULT_NAMESPACE_NAME, conf);
            this.b.addAlternateView("PROPERTIES", configData);
            this.b.addAlternateView("FOO", configData, 20, 10);
            assertNotNull("Remapped alt view retrieved by original name", this.b.getAlternateView("PROPERTIES"));
            assertNotNull("Remapped alt view retrieved by new name", this.b.getAlternateView("FLUBBER"));
            assertNotNull("Remapped alt view slice retrieved by original name", this.b.getAlternateView("FOO"));
            assertNotNull("Remapped alt view slice retrieved by new name", this.b.getAlternateView("BAR"));
            final Set<String> avnames = this.b.getAlternateViewNames();
            assertTrue("Alt view names contains remapped name", avnames.contains("FLUBBER"));
            assertTrue("Alt view slice names contains remapped name", avnames.contains("BAR"));

            // Delete by orig name
            this.b.addAlternateView("FOO", null, 20, 10);
            assertTrue("View removed by orig name", this.b.getAlternateViewNames().size() == 1);
            // Delete by mapped name
            this.b.addAlternateViewContainer("FLUBBER", null);
            assertTrue("View removed by orig name", this.b.getAlternateViewNames().size() == 0);
        } catch (Exception ex) {
            fail("Could not configure test: " + ex.getMessage());
        } finally {
            // Clean up
            Namespace.unbind(MetadataDictionary.DEFAULT_NAMESPACE_NAME);
        }
    }

    @Test
    public void testNonExistentAltViews() {
        assertNull("No such view", this.b.getAlternateView("NOSUCHVIEW"));
    }

    @Test
    public void testNonExistentAltViewBuffer() {
        assertNull("Byte buffer on no such view", this.b.getAlternateViewBuffer("NOSUCHVIEW"));
    }

    @Test
    public void testAltViews() {
        this.b.addAlternateView("TESTVIEW1", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW2", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW3", "alternate view".getBytes());

        this.b.addAlternateViewContainer("TESTVIEW2", null);
        assertNull("Null view after removal", this.b.getAlternateView("TESTVIEW2"));
        assertNull("Empty byte buffer after removal", this.b.getAlternateViewBuffer("TESTVIEW2"));
    }

    @Test
    public void testAltViewSlice() {
        this.b.addAlternateView("TESTVIEW1", "abcdefghij".getBytes(), 3, 4);
        assertEquals("Alt view slice must use proper data", "defg", new String(this.b.getAlternateView("TESTVIEW1")));
    }

    @Test
    public void testSetOfAltViewNames() {
        this.b.addAlternateView("TESTVIEW1", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW2", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW3", "alternate view".getBytes());
        final Set<String> vnames = this.b.getAlternateViewNames();
        assertEquals("Count of view names", 3, vnames.size());

        List<String> source = new ArrayList<String>(vnames);
        List<String> sorted = new ArrayList<String>(vnames);
        Collections.sort(sorted);
        assertEquals("Views are sorted", sorted, source);
    }

    @Test
    public void testMapOfAltViews() {
        this.b.addAlternateView("TESTVIEW1", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW2", "alternate view".getBytes());
        this.b.addAlternateView("TESTVIEW3", "alternate view".getBytes());
        final Map<String, byte[]> v = this.b.getAlternateViews();
        assertEquals("Count of views", 3, v.size());

        List<String> source = new ArrayList<String>(v.keySet());
        List<String> sorted = new ArrayList<String>(v.keySet());
        Collections.sort(sorted);
        assertEquals("Views are sorted", sorted, source);
    }

    @Test
    public void testAppendAltView() {
        this.b.addAlternateView("T1", "alternate view".getBytes());
        this.b.appendAlternateView("T1", " more stuff".getBytes());
        assertEquals("Appended alternate view contents", "alternate view more stuff", new String(this.b.getAlternateView("T1")));
    }

    @Test
    public void testAppendAltViewOnEmpty() {
        this.b.appendAlternateView("T1", "more stuff".getBytes());
        assertEquals("Appended alternate view contents", "more stuff", new String(this.b.getAlternateView("T1")));
    }

    @Test
    public void testAppendAltViewSlice() {
        this.b.addAlternateView("T1", "alternate view".getBytes());
        this.b.appendAlternateView("T1", "xx more stuff xx".getBytes(), 2, 11);
        assertEquals("Appended alternate view contents", "alternate view more stuff", new String(this.b.getAlternateView("T1")));
    }

    @Test
    public void testAppendAltViewSliceOnEmpty() {
        this.b.appendAlternateView("T1", "xx more stuff xx".getBytes(), 3, 10);
        assertEquals("Appended alternate view contents", "more stuff", new String(this.b.getAlternateView("T1")));
    }

    @Test
    public void testAddContainerEmpty() throws Exception {
        IDataContainer cont = b.addAlternateView("Fish");
        cont.setData("Wombat".getBytes(StandardCharsets.UTF_8));
        assertEquals("Wombat", new String(b.getAlternateViewContainer("Fish").data(), UTF_8));
        assertEquals("Wombat", new String(b.getAlternateView("Fish"), UTF_8));
    }

    @Test
    public void testAddContainerExisting() throws Exception {
        IDataContainer cont = new MemoryDataContainer();
        cont.setData("Gecko".getBytes(StandardCharsets.UTF_8));
        b.addAlternateViewContainer("Fish", cont);
        assertEquals("Gecko", new String(b.getAlternateViewContainer("Fish").data(), UTF_8));
        assertEquals("Gecko", new String(b.getAlternateView("Fish"), UTF_8));
    }

    @Test
    public void testRemoveView() throws Exception {
        IDataContainer cont = new MemoryDataContainer();
        cont.setData("Tanuki".getBytes(StandardCharsets.UTF_8));
        b.addAlternateViewContainer("Fish", cont);
        assertEquals("Tanuki", new String(b.getAlternateViewContainer("Fish").data(), UTF_8));
        assertEquals("Tanuki", new String(b.getAlternateView("Fish"), UTF_8));
        b.removeView("Fish");
        assertNull(b.getAlternateViewContainer("Fish"));
        assertNull(b.getAlternateView("Fish"));
    }

    @Test
    public void testAddContainerStream() throws Exception {
        IDataContainer cont = b.addAlternateView("Fish");
        try (OutputStream is = Channels.newOutputStream(cont.channel())) {
            is.write("Sloth".getBytes(UTF_8));
        }
        assertEquals("Sloth", new String(b.getAlternateViewContainer("Fish").data(), UTF_8));
    }

    @Test
    public void testMutateViaMapPersists() throws Exception {
        b.addAlternateView("Fish", "Wombat".getBytes(UTF_8));
        try (OutputStream os = Channels.newOutputStream(b.getAlternateViewContainers().get("Fish").channel())) {
            os.write("Narwhal".getBytes(UTF_8));
        }
        assertEquals("Narwhal", new String(b.getAlternateViewContainer("Fish").data(), UTF_8));
    }

    private static class FakeLegacyManager implements IOriginalViewManager {
        /**
         * 
         */
        private static final long serialVersionUID = -2062450351760720172L;
        private Map<String, byte[]> multipartAlternative = new TreeMap<>();

        @Override
        public int getNumAlternateViews() {
            return this.multipartAlternative.size();

        }

        @Override
        public byte[] getAlternateView(String s) {
            try {
                final MetadataDictionary dict = MetadataDictionary.lookup();
                return this.multipartAlternative.get(dict.map(s));
            } catch (NamespaceException ex) {
                return this.multipartAlternative.get(s);
            }
        }

        @Override
        public ByteBuffer getAlternateViewBuffer(String s) {
            final byte[] viewdata = getAlternateView(s);
            if (viewdata == null) {
                return null;
            }
            return ByteBuffer.wrap(viewdata);
        }

        @Override
        public void addAlternateView(String name, byte[] data) {
            String mappedName = name;
            try {
                final MetadataDictionary dict = MetadataDictionary.lookup();
                mappedName = dict.map(name);
            } catch (NamespaceException ex) {
                // ignore
            }
            if (data == null) {
                this.multipartAlternative.remove(mappedName);
            } else {
                this.multipartAlternative.put(mappedName, data);
            }
        }

        @Override
        public void addAlternateView(String name, byte[] data, int offset, int length) {
            String mappedName = name;
            try {
                final MetadataDictionary dict = MetadataDictionary.lookup();
                mappedName = dict.map(name);
            } catch (NamespaceException ex) {
                // ignore
            }
            if (data == null || length <= 0) {
                this.multipartAlternative.remove(mappedName);
            } else {
                final byte[] mpa = new byte[length];
                System.arraycopy(data, offset, mpa, 0, length);
                this.multipartAlternative.put(mappedName, mpa);
            }
        }

        @Override
        public void appendAlternateView(String name, byte[] data) {
            appendAlternateView(name, data, 0, data.length);
        }

        @Override
        public void appendAlternateView(String name, byte[] data, int offset, int length) {
            final byte[] av = getAlternateView(name);
            if (av != null) {
                addAlternateView(name, ByteUtil.glue(av, 0, av.length - 1, data, offset, offset + length - 1));
            } else {
                addAlternateView(name, data, offset, length);
            }
        }

        @Override
        public Set<String> getAlternateViewNames() {
            return new TreeSet<>(this.multipartAlternative.keySet());
        }

        @Override
        public Map<String, byte[]> getAlternateViews() {
            return this.multipartAlternative;
        }
    }
}
