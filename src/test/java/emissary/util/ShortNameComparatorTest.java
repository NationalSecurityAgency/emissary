package emissary.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import emissary.core.BaseDataObject;
import emissary.core.DataObjectFactory;
import emissary.core.Family;
import emissary.core.IBaseDataObject;
import emissary.test.core.UnitTest;
import org.junit.Test;

public class ShortNameComparatorTest extends UnitTest {
    private byte[] nobytes = new byte[0];
    private String b = "foo";
    private String ba = this.b + Family.SEP;

    @Test
    public void testOrdering() {
        final List<IBaseDataObject> l = new ArrayList<IBaseDataObject>();

        fillList(l);
        Collections.sort(l, new ShortNameComparator());
        checkList(l);
    }

    @Test
    public void testNonsenseNames() {
        final List<IBaseDataObject> l = new ArrayList<IBaseDataObject>();
        l.add(DataObjectFactory.getInstance(new Object[] {null, "foo-att-def"}));
        l.add(DataObjectFactory.getInstance(new Object[] {null, "foo-att-abc"}));
        Collections.sort(l, new ShortNameComparator());
        assertEquals("Bad components sort in alpha order", "foo-att-abc", l.get(0).shortName());
    }

    private void fillList(final List<IBaseDataObject> l) {
        l.add(DataObjectFactory.getInstance(new Object[] {this.nobytes, this.ba + "1"}));
        l.add(DataObjectFactory.getInstance(new Object[] {this.nobytes, this.ba + "3"}));
        l.add(DataObjectFactory.getInstance(new Object[] {this.nobytes, this.b}));
        l.add(DataObjectFactory.getInstance(new Object[] {this.nobytes, this.ba + "3" + Family.sep(2)}));
        l.add(DataObjectFactory.getInstance(new Object[] {this.nobytes, this.ba + "3" + Family.sep(1)}));
        l.add(DataObjectFactory.getInstance(new Object[] {this.nobytes, this.ba + "2"}));
        l.add(DataObjectFactory.getInstance(new Object[] {this.nobytes, this.ba + "3" + Family.sep(1) + Family.sep(1)}));

    }

    private void checkList(final List<IBaseDataObject> l) {
        assertEquals("Ordering of sort", this.b, l.get(0).shortName());
        assertEquals("Ordering of sort", this.ba + "1", l.get(1).shortName());
        assertEquals("Ordering of sort", this.ba + "2", l.get(2).shortName());
        assertEquals("Ordering of sort", this.ba + "3", l.get(3).shortName());
        assertEquals("Ordering of sort", this.ba + "3" + Family.sep(1), l.get(4).shortName());
        assertEquals("Ordering of sort", this.ba + "3" + Family.sep(1) + Family.sep(1), l.get(5).shortName());
        assertEquals("Ordering of sort", this.ba + "3" + Family.sep(2), l.get(6).shortName());
    }

    @Test
    public void testImplComparator() {
        final List<IBaseDataObject> l = new ArrayList<IBaseDataObject>();

        fillList(l);
        Collections.sort(l, new ShortNameComparator());
        checkList(l);
    }

    @Test
    public void testSubclassedComparator() {
        final String defaultPayloadClass = DataObjectFactory.getImplementingClass();
        DataObjectFactory.setImplementingClass(MyDataObject.class.getName());
        try {
            final List<IBaseDataObject> l = new ArrayList<IBaseDataObject>();

            fillList(l);
            Collections.sort(l, new ShortNameComparator());
            checkList(l);
        } catch (Exception ex) {
            fail("Cannot operate Comparator in subclass: " + ex.getMessage());
        } finally {
            DataObjectFactory.setImplementingClass(defaultPayloadClass);
        }
    }

    // An extension of BaseDataObject for testing the
    // lower bounded generics on the comparator
    public static class MyDataObject extends BaseDataObject {
        static final long serialVersionUID = 7872122417333007868L;

        public MyDataObject() {
            super();
        }

        public MyDataObject(final byte[] data, final String name) {
            super(data, name);
        }
    }
}
