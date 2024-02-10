package emissary.id;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.core.ResourceException;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdPlaceTest extends UnitTest {

    @Nullable
    MyIdPlace place;

    @BeforeEach
    public void setupPlace() throws Exception {
        place = new MyIdPlace();
    }

    @AfterEach
    public void teardownPlace() throws Exception {
        super.tearDown();
        place.shutDown();
        place = null;
    }

    @Test
    void testFormRenaming() {
        assertEquals("IN_WITH_THE_NEW", place.checkRenamedForm("OUT_WITH_THE_OLD"), "Rename form when specified in config");
        assertEquals("XYZZY", place.checkRenamedForm("XYZZY"), "Do not change form when not specified as a rename");
    }

    @Test
    void testUnknownFormProcessing() throws Exception {
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is a test".getBytes());
        payload.setCurrentForm("UNKNOWN");
        place.process(payload);
        assertEquals("UNKNOWN", payload.currentForm(), "Form is unknown when it is not know or remapped or ignored");
        assertEquals(1, payload.currentFormSize(), "Form is only unknown when it is not known or remapped or ignored");
    }

    @Test
    void testIgnoredFormProcessing() throws Exception {
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is a test".getBytes());
        payload.setCurrentForm("UNKNOWN");
        payload.setParameter("THE_ANSWER", "SHOVEL");
        place.process(payload);
        assertEquals("UNKNOWN", payload.currentForm(), "Form is unchanged when ignored");
        assertEquals(1, payload.currentFormSize(), "Form is only unchanged when it is ignored");
    }

    @Test
    void testRemappedFinalFormProcessing() throws Exception {
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is a test".getBytes());
        payload.setCurrentForm("UNKNOWN");
        payload.setParameter("THE_ANSWER", "OUT_WITH_THE_OLD");
        place.process(payload);
        assertEquals("IN_WITH_THE_NEW", payload.currentForm(), "Form is remapped when known and final");
        assertEquals(1, payload.currentFormSize(), "Form is remapped only when known and final");
    }

    @Test
    void testRemappedNonFinalFormProcessing() throws Exception {
        IBaseDataObject payload = DataObjectFactory.getInstance();
        payload.setData("This is a test".getBytes());
        payload.setCurrentForm("UNKNOWN");
        payload.setParameter("THE_ANSWER", "SOMETHING_BORROWED");
        place.process(payload);
        assertEquals("UNKNOWN", payload.currentForm(), "Form is has unknown on top when known and non-final");
        assertEquals(2, payload.currentFormSize(), "Form is remapped and topped when known and non-final");
        assertEquals("SOMETHING_BLUE", payload.currentFormAt(1), "Form is remapped when known and non-final");
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
