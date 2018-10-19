package emissary.id;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.core.ResourceException;
import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IdPlaceTest extends UnitTest {

    MyIdPlace place;

    @Before
    public void setupPlace() throws Exception {
        place = new MyIdPlace();
    }

    @After
    public void teardownPlace() throws Exception {
        super.tearDown();
        place.shutDown();
        place = null;
    }

    @Test
    public void testFormRenaming() {
        assertEquals("Rename form when specified in config", "IN_WITH_THE_NEW", place.checkRenamedForm("OUT_WITH_THE_OLD"));
        assertEquals("Do not change form when not specified as a rename", "XYZZY", place.checkRenamedForm("XYZZY"));
    }

    @Test
    public void testUnknownFormProcessing() throws Exception {
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is a test".getBytes());
        payload.setCurrentForm("UNKNOWN");
        place.process(payload);
        assertEquals("Form is unknown when it is not know or remapped or ignored", "UNKNOWN", payload.currentForm());
        assertEquals("Form is only unknown when it is not known or remapped or ignored", 1, payload.currentFormSize());
    }

    @Test
    public void testIgnoredFormProcessing() throws Exception {
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is a test".getBytes());
        payload.setCurrentForm("UNKNOWN");
        payload.setParameter("THE_ANSWER", "SHOVEL");
        place.process(payload);
        assertEquals("Form is unchanged when ignored", "UNKNOWN", payload.currentForm());
        assertEquals("Form is only unchanged when it is ignored", 1, payload.currentFormSize());
    }

    @Test
    public void testRemappedFinalFormProcessing() throws Exception {
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is a test".getBytes());
        payload.setCurrentForm("UNKNOWN");
        payload.setParameter("THE_ANSWER", "OUT_WITH_THE_OLD");
        place.process(payload);
        assertEquals("Form is remapped when known and final", "IN_WITH_THE_NEW", payload.currentForm());
        assertEquals("Form is remapped only when known and final", 1, payload.currentFormSize());
    }

    @Test
    public void testRemappedNonFinalFormProcessing() throws Exception {
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is a test".getBytes());
        payload.setCurrentForm("UNKNOWN");
        payload.setParameter("THE_ANSWER", "SOMETHING_BORROWED");
        place.process(payload);
        assertEquals("Form is has unknown on top when known and non-final", "UNKNOWN", payload.currentForm());
        assertEquals("Form is remapped and topped when known and non-final", 2, payload.currentFormSize());
        assertEquals("Form is remapped when known and non-final", "SOMETHING_BLUE", payload.currentFormAt(1));
    }


    static class MyIdPlace extends IdPlace {

        public MyIdPlace() throws IOException {}

        @Override
        public void process(IBaseDataObject payload) throws ResourceException {
            if (payload.hasParameter("THE_ANSWER")) {
                this.setCurrentForm(payload, payload.getStringParameter("THE_ANSWER"));
            }
        }

        public String checkRenamedForm(String s) {
            return renamedForm(s);
        }
    }
}
