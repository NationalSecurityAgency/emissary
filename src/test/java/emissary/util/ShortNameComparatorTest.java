package emissary.util;

import emissary.core.BaseDataObject;
import emissary.core.DataObjectFactory;
import emissary.core.Family;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class ShortNameComparatorTest extends UnitTest {
    private final byte[] nobytes = new byte[0];
    private final String b = "foo";
    private final String ba = this.b + Family.SEP;

    @Test
    void testOrdering() {
        final List<IBaseDataObject> l = new ArrayList<>();

        fillList(l);
        l.sort(new ShortNameComparator());
        checkList(l);
    }

    @Test
    void testNonsenseNames() {
        final List<IBaseDataObject> l = new ArrayList<>();
        l.add(DataObjectFactory.getInstance(null, "foo-att-def"));
        l.add(DataObjectFactory.getInstance(null, "foo-att-abc"));
        l.sort(new ShortNameComparator());
        assertEquals("foo-att-abc", l.get(0).shortName(), "Bad components sort in alpha order");
    }

    private void fillList(final List<IBaseDataObject> l) {
        l.add(DataObjectFactory.getInstance(this.nobytes, this.ba + "1"));
        l.add(DataObjectFactory.getInstance(this.nobytes, this.ba + "3"));
        l.add(DataObjectFactory.getInstance(this.nobytes, this.b));
        l.add(DataObjectFactory.getInstance(this.nobytes, this.ba + "3" + Family.getSep(2)));
        l.add(DataObjectFactory.getInstance(this.nobytes, this.ba + "3" + Family.getSep(1)));
        l.add(DataObjectFactory.getInstance(this.nobytes, this.ba + "2"));
        l.add(DataObjectFactory.getInstance(this.nobytes, this.ba + "3" + Family.getSep(1) + Family.getSep(1)));
    }

    private void checkList(final List<IBaseDataObject> l) {
        assertEquals(this.b, l.get(0).shortName(), "Ordering of sort");
        assertEquals(this.ba + "1", l.get(1).shortName(), "Ordering of sort");
        assertEquals(this.ba + "2", l.get(2).shortName(), "Ordering of sort");
        assertEquals(this.ba + "3", l.get(3).shortName(), "Ordering of sort");
        assertEquals(this.ba + "3" + Family.getSep(1), l.get(4).shortName(), "Ordering of sort");
        assertEquals(this.ba + "3" + Family.getSep(1) + Family.getSep(1), l.get(5).shortName(), "Ordering of sort");
        assertEquals(this.ba + "3" + Family.getSep(2), l.get(6).shortName(), "Ordering of sort");
    }

    @Test
    void testImplComparator() {
        final List<IBaseDataObject> l = new ArrayList<>();

        fillList(l);
        l.sort(new ShortNameComparator());
        checkList(l);
    }

    @Test
    void testSubclassedComparator() {
        final String defaultPayloadClass = DataObjectFactory.getImplementingClass();
        DataObjectFactory.setImplementingClass(MyDataObject.class.getName());
        try {
            final List<IBaseDataObject> l = new ArrayList<>();

            fillList(l);
            l.sort(new ShortNameComparator());
            checkList(l);
        } catch (Exception ex) {
            fail("Cannot operate Comparator in subclass", ex);
        } finally {
            DataObjectFactory.setImplementingClass(defaultPayloadClass);
        }
    }

    // An extension of BaseDataObject for testing the
    // lower bounded generics on the comparator
    public static class MyDataObject extends BaseDataObject {
        static final long serialVersionUID = 7872122417333007868L;

        public MyDataObject(final byte[] data, final String name) {
            super(data, name);
        }
    }
}
