package emissary.core.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.UnitTest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class BDODeserialzerIteratorTest extends UnitTest {

    String[] vals = new String[] {"ONE", "TWO", "THREE"};

    @Test
    public void testIterator() throws Exception {

        ArrayList<IBaseDataObject> list = new ArrayList<>();

        IBaseDataObject bdo = DataObjectFactory.getInstance(new byte[1024], "testfile", "testformandtype");
        bdo.putParameter("PARAM", vals[0]);
        list.add(bdo);

        bdo = DataObjectFactory.getInstance(new byte[1024], "testfile-att-1", "testformandtype");
        bdo.putParameter("PARAM", vals[1]);
        list.add(bdo);

        bdo = DataObjectFactory.getInstance(new byte[1024], "testfile-att-2", "testformandtype");
        bdo.putParameter("PARAM", vals[2]);
        list.add(bdo);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Kryo k = BDODeserialzerIterator.KRYO.get();
        Output output = new Output(out);
        k.writeObject(output, list);
        k.writeObject(output, list);
        k.writeObject(output, list);
        output.close();
        BDODeserialzerIterator instance = new BDODeserialzerIterator(new ByteArrayInputStream(out.toByteArray()));
        int count = 0;
        while (instance.hasNext()) {
            List<IBaseDataObject> result = instance.next();
            Assert.assertTrue("Should have 3 bdos", result.size() == 3);
            for (int i = 0; i < result.size(); i++) {
                bdo = result.get(i);
                Assert.assertTrue(bdo.getStringParameter("PARAM").equals(vals[i]));
                Assert.assertTrue(bdo.data().length == 1024);
            }
            count++;
        }
        Assert.assertTrue("Should have 3 family trees", count == 3);
    }

}
