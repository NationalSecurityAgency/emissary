package emissary.test.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import emissary.core.DataObjectFactory;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.place.IServiceProviderPlace;
import emissary.util.io.ResourceReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public abstract class IdentificationTest extends UnitTest {
    protected static Logger logger = LoggerFactory.getLogger(IdentificationTest.class);
    protected static IServiceProviderPlace place = null;

    @Parameterized.Parameters
    public static Collection<?> data() {
        return getMyTestParameterFiles(IdentificationTest.class);
    }

    protected String resource;

    /**
     * Called by the Parameterized Runner
     */
    public IdentificationTest(String resource) throws IOException {
        super(resource);
        this.resource = resource;
    }

    @Before
    public void setUpPlace() throws Exception {
        place = createPlace();
    }

    /**
     * Derived classes must implement this
     */
    public abstract IServiceProviderPlace createPlace() throws IOException;

    @After
    public void tearDownPlace() {
        if (place != null) {
            place.shutDown();
            place = null;
        }
    }

    @Test
    public void testIdentificationPlace() {
        logger.debug("Running {} test on resource {}", place.getClass().getName(), resource);

        try (InputStream doc = new ResourceReader().getResourceAsStream(resource)) {
            byte[] data = new byte[doc.available()];
            doc.read(data);
            String expectedAnswer = resource.replaceAll("^.*/([^/@]+)(@\\d+)?\\.dat$", "$1");
            IBaseDataObject payload = DataObjectFactory.getInstance(data, resource, Form.UNKNOWN);
            processPreHook(payload, resource);
            place.agentProcessHeavyDuty(payload);
            processPostHook(payload, resource);
            checkAnswersPreHook(payload, resource, expectedAnswer);
            checkAnswers(payload, resource, expectedAnswer);
            checkAnswersPostHook(payload, resource, expectedAnswer);
        } catch (Exception ex) {
            logger.error("Error running test {}", resource, ex);
            fail("Cannot run test " + resource + ": " + ex);
        }
    }

    protected void processPreHook(IBaseDataObject payload, String resource) {
        // Nothing to do here
    }

    protected void processPostHook(IBaseDataObject payload, String resource) {
        // Nothing to do here
    }

    protected void checkAnswersPreHook(IBaseDataObject payload, String resource, String expected) {
        // Nothing to do here
    }

    protected void checkAnswersPostHook(IBaseDataObject payload, String resource, String expected) {
        // Nothing to do here
    }

    protected void checkAnswers(IBaseDataObject payload, String resource, String expected) {
        String[] expectedForms = expected.split("#");
        for (int i = 0; i < expectedForms.length; i++) {
            String result = payload.currentFormAt(i).replaceAll("^LANG-", "");
            if (expectedForms[i].indexOf("(") > 0)
                assertEquals("Current form is wrong in " + resource, expectedForms[i], result);
            else
                assertEquals("Current form is wrong in " + resource, expectedForms[i], result.replaceAll("\\(.*\\)", ""));
        }
    }
}
