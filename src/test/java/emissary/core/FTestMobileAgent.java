package emissary.core;

import emissary.admin.Startup;
import emissary.place.EmptyFormPlace;
import emissary.place.IServiceProviderPlace;
import emissary.place.ServiceProviderPlace;
import emissary.test.core.junit5.FunctionalTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FTestMobileAgent extends FunctionalTest {

    @BeforeEach
    public void testSetUp() throws Exception {
        @SuppressWarnings("unused")
        final Logger[] testLoggers =
                new Logger[] {LoggerFactory.getLogger(Startup.class), LoggerFactory.getLogger(MobileAgent.class)};


    }

    @AfterEach
    public void testTearDown() {
        demolishServer();
    }

    @Test
    void testEmptyFormHandlingWhenAllowed() {
        final MyAgent m = new MyAgent();
        final IServiceProviderPlace place = addPlace("http://localhost:8213/FakeEmptyPlace", "emissary.core.FTestMobileAgent$FakeEmptyPlace");

        final IBaseDataObject d = DataObjectFactory.getInstance();
        d.setCurrentForm("PETERPAN");
        m.callAtPlace(place, d);
        assertEquals(0,
                d.currentFormSize(),
                "Place is allowed to consume the form stack " + "if it implements EmptyFormPlace - " + d.getAllCurrentForms());
        place.shutDown();
        m.killAgent();
    }

    @Test
    void testEmptyFormHandlingWhenNotAllowed() {
        final MyAgent m = new MyAgent();

        final IServiceProviderPlace place = addPlace("http://localhost:8213/FakePlace", "emissary.core.FTestMobileAgent$FakePlace");
        final IBaseDataObject d = DataObjectFactory.getInstance();
        d.setCurrentForm("XYZZY");
        m.callAtPlace(place, d);
        assertEquals(1, d.currentFormSize(), "Place is not allowed to consume the form stack " + "if it does not implment EmptyFormPlace");
        assertEquals(Form.ERROR, d.currentForm(), "Place is not allowed to consume the form stack " + "if it does not implment EmptyFormPlace");
        place.shutDown();
        m.killAgent();
    }

    @Test
    void testEmptyFormHandlingWhenNotAllowedAndExceptionIsThrown() {
        final MyAgent m = new MyAgent();

        final IServiceProviderPlace place = addPlace("http://localhost:8213/FakePlace", "emissary.core.FTestMobileAgent$FakePlace");
        ((FakePlace) place).throwExceptionDuringProcessing(true);
        final IBaseDataObject d = DataObjectFactory.getInstance();
        d.setCurrentForm("XYZZY");
        m.callAtPlace(place, d);
        assertEquals(1, d.currentFormSize(), "Place is not allowed to consume the form stack " + "if it does not implment EmptyFormPlace");
        assertEquals(Form.ERROR, d.currentForm(), "Place is not allowed to consume the form stack " + "if it does not implment EmptyFormPlace");
        place.shutDown();
        m.killAgent();
    }


    static class MyAgent extends HDMobileAgent {
        private static final long serialVersionUID = -5289758527010060429L;

        public void callAtPlace(final IServiceProviderPlace place, final IBaseDataObject payload) {
            atPlace(place, payload);
        }
    }

    public static class FakePlace extends ServiceProviderPlace {
        private boolean forcedException = false;

        @SuppressWarnings("PMD.UnusedFormalParameter")
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

    public static class FakeEmptyPlace extends ServiceProviderPlace implements EmptyFormPlace {
        @SuppressWarnings("PMD.UnusedFormalParameter")
        public FakeEmptyPlace(final String a, final String b, final String c) throws IOException {}

        @Override
        public void process(final IBaseDataObject d) {
            d.replaceCurrentForm(null); // clear it
        }
    }
}
