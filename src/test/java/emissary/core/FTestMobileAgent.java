package emissary.core;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import emissary.place.IServiceProviderPlace;
import emissary.test.core.FunctionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FTestMobileAgent extends FunctionalTest {

    @Before
    public void testSetUp() throws Exception {
        @SuppressWarnings("unused")
        final Logger[] testLoggers =
                new Logger[] {LoggerFactory.getLogger(emissary.admin.Startup.class), LoggerFactory.getLogger(emissary.core.MobileAgent.class)};


    }

    @After
    public void testTearDown() throws Exception {
        demolishServer();
    }

    @Test
    public void testEmptyFormHandlingWhenAllowed() throws Exception {
        final MyAgent m = new MyAgent();
        final IServiceProviderPlace place = addPlace("http://localhost:8213/FakeEmptyPlace", "emissary.core.FTestMobileAgent$FakeEmptyPlace");

        final IBaseDataObject d = DataObjectFactory.getInstance();
        d.setCurrentForm("PETERPAN");
        m.callAtPlace(place, d);
        assertEquals("Place is allowed to consume the form stack " + "if it implements EmptyFormPlace - " + d.getAllCurrentForms(), 0,
                d.currentFormSize());
        place.shutDown();
        m.killAgent();
    }

    @Test
    public void testEmptyFormHandlingWhenNotAllowed() throws Exception {
        final MyAgent m = new MyAgent();

        final IServiceProviderPlace place = addPlace("http://localhost:8213/FakePlace", "emissary.core.FTestMobileAgent$FakePlace");
        final IBaseDataObject d = DataObjectFactory.getInstance();
        d.setCurrentForm("XYZZY");
        m.callAtPlace(place, d);
        assertEquals("Place is not allowed to consume the form stack " + "if it does not implment EmptyFormPlace", 1, d.currentFormSize());
        assertEquals("Place is not allowed to consume the form stack " + "if it does not implment EmptyFormPlace", Form.ERROR, d.currentForm());
        place.shutDown();
        m.killAgent();
    }

    @Test
    public void testEmptyFormHandlingWhenNotAllowedAndExceptionIsThrown() throws Exception {
        final MyAgent m = new MyAgent();

        final IServiceProviderPlace place = addPlace("http://localhost:8213/FakePlace", "emissary.core.FTestMobileAgent$FakePlace");
        ((FakePlace) place).throwExceptionDuringProcessing(true);
        final IBaseDataObject d = DataObjectFactory.getInstance();
        d.setCurrentForm("XYZZY");
        m.callAtPlace(place, d);
        assertEquals("Place is not allowed to consume the form stack " + "if it does not implment EmptyFormPlace", 1, d.currentFormSize());
        assertEquals("Place is not allowed to consume the form stack " + "if it does not implment EmptyFormPlace", Form.ERROR, d.currentForm());
        place.shutDown();
        m.killAgent();
    }


    static class MyAgent extends HDMobileAgent {
        private static final long serialVersionUID = -5289758527010060429L;

        public void callAtPlace(final IServiceProviderPlace place, final IBaseDataObject payload) {
            atPlace(place, payload);
        }
    }

    public static class FakePlace extends emissary.place.ServiceProviderPlace {
        private boolean forcedException = false;

        public FakePlace(final String a, final String b, final String c) throws IOException {}

        public void throwExceptionDuringProcessing(final boolean value) {
            this.forcedException = value;
        }

        @Override
        public void process(final IBaseDataObject d) throws ResourceException {
            d.replaceCurrentForm(null); // clear it
            if (this.forcedException) {
                throw new ResourceException("This is a forced exception");
            }
        }
    }

    public static class FakeEmptyPlace extends emissary.place.ServiceProviderPlace implements emissary.place.EmptyFormPlace {
        public FakeEmptyPlace(final String a, final String b, final String c) throws IOException {}

        @Override
        public void process(final IBaseDataObject d) {
            d.replaceCurrentForm(null); // clear it
        }
    }
}
