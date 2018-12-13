package emissary.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import emissary.core.kryo.BDODeserialzerIterator;
import emissary.core.kryo.EmissaryKryoFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * 
 */
public class BDOKryoSerializerTest {

    Kryo kryo = EmissaryKryoFactory.buildKryo();
    ByteArrayOutputStream baos = new ByteArrayOutputStream(1024 * 1024 * 1);
    static final byte[] data = "Some test data in bytes".getBytes();
    static final byte[] altdata = "alt view data in bytes".getBytes();
    static final String fname = "TEST_FILE.dat";

    public BDOKryoSerializerTest() {

    }

    @BeforeClass
    public static void setUpClass() {}

    @AfterClass
    public static void tearDownClass() {}

    @Before
    public void setUp() {
        baos.reset();
    }

    @After
    public void tearDown() {}

    /**
     * Test read/write methods of class BDOKryoSerializer.
     */
    @Test
    public void testWriteRead() throws Exception {

        Output output = new Output(baos);

        BaseDataObject test = new BaseDataObject(data, fname);
        UUID internal = test.getInternalId();
        long time = test.getCreationTimestamp().getTime();
        test.addAlternateView("ALT", altdata);
        test.putParameter("TLD_PARAM", "Good Value");
        test.setFontEncoding("someEncoding");
        test.setBirthOrder(1);
        BaseDataObject er = new BaseDataObject(null, "ExtractedRecord-att-1");
        er.searchCurrentForm("EXTRACTED_RECORD");
        er.setClassification("FOO");
        er.putParameter("SOME_PARAM", "SOME VAL");
        test.addExtractedRecord(er);
        kryo.writeObject(output, test);
        output.close();

        ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
        Input input = new Input(in);
        BaseDataObject bdo = kryo.readObject(input, BaseDataObject.class);
        assertTrue(bdo.getInternalId().equals(internal));
        assertTrue(time == bdo.getCreationTimestamp().getTime());
        assertTrue(fname.equals(bdo.getFilename()));
        assertArrayEquals(data, bdo.data());
        assertTrue(bdo.getExtractedRecordCount() == 1);
    }

    /**
     * Test of read method, of class BDOKryoSerializer.
     */
    @Test
    public void testBadParameter() {

        Output output = new Output(baos);

        BaseDataObject test = new BaseDataObject(data, fname);

        UUID internal = test.getInternalId();
        test.addAlternateView("ALT", altdata);
        test.putParameter("BAD_PARAM", new NonSerializableClass());
        test.putParameter("GOOD_PARAM", "Good");
        kryo.writeObject(output, test);
        output.close();

        ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
        Input input = new Input(in);
        BaseDataObject bdo = kryo.readObject(input, BaseDataObject.class);
        assertTrue(bdo.getInternalId().equals(internal));
        assertTrue(fname.equals(bdo.getFilename()));
        assertArrayEquals(data, bdo.data());
        assertTrue(bdo.getParameterKeys().size() == 2);
        assertFalse(bdo.getStringParameter("BAD_PARAM").equals("Not possible"));
        assertTrue(bdo.getStringParameter("GOOD_PARAM").equals("Good"));

    }

    @Test
    public void testListIBDO() {
        List<IBaseDataObject> ibdos = new ArrayList<>();
        BaseDataObject parent = new BaseDataObject(data, fname);
        parent.putParameter("PARENT_PARAM", "one param");
        parent.setNumChildren(5);
        ibdos.add(parent);
        for (int i = 0; i < 5; i++) {
            BaseDataObject child = new BaseDataObject(altdata, fname + "-att-" + (i + 1));
            child.putParameter("CHILD_PARAM", "child param " + i);
            child.setBirthOrder(i + 1);
            child.setNumSiblings(4);
            ibdos.add(child);
        }
        Output output = new Output(baos);
        kryo.writeObject(output, ibdos);
        output.close();

        ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
        Input input = new Input(in);
        @SuppressWarnings("unchecked")
        ArrayList<IBaseDataObject> bdos = kryo.readObject(input, ArrayList.class);
        assertTrue(bdos.size() == 6);
        for (int i = 0; i < bdos.size(); i++) {
            IBaseDataObject object = bdos.get(i);
            if (i == 0) {
                assertTrue(object.getNumChildren() == 5);
            } else {
                assertTrue(object.getNumSiblings() == 4);
                // subtract one for parent
                assertTrue(object.getStringParameter("CHILD_PARAM").equals("child param " + (i - 1)));
            }

        }
    }


    @Test
    public void testDeserializerIterator() {
        List<IBaseDataObject> ibdos = new ArrayList<>();
        BaseDataObject parent = new BaseDataObject(data, fname);
        BaseDataObject parent2 = new BaseDataObject(data, fname + "2");
        parent.putParameter("PARENT_PARAM", "one param");
        parent.setNumChildren(5);
        ibdos.add(parent);
        for (int i = 0; i < 5; i++) {
            BaseDataObject child = new BaseDataObject(altdata, fname + "-att-" + (i + 1));
            child.putParameter("CHILD_PARAM", "child param " + i);
            child.setBirthOrder(i + 1);
            child.setNumSiblings(4);
            ibdos.add(child);
        }
        ibdos.add(parent2);
        Output output = new Output(baos);
        kryo.writeObject(output, ibdos);
        output.close();

        ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
        BDODeserialzerIterator it = new BDODeserialzerIterator(in);

        while (it.hasNext()) {
            List<IBaseDataObject> tree = it.next();
            assertTrue(tree.size() == 7);
        }
        it.close();
    }


    @Test(expected = NoSuchElementException.class)
    public void testException() {
        Output output = new Output(baos);
        output.close();

        ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
        BDODeserialzerIterator it = Mockito.spy(new BDODeserialzerIterator(in));

        Mockito.doReturn(true).when(it).hasNext();

        if (it.hasNext()) {
            it.next();
        }

    }

    private static class NonSerializableClass {
        String foo = "Not possible";
    }
}
