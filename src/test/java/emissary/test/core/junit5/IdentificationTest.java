package emissary.test.core.junit5;

import emissary.core.DataObjectFactory;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.place.IServiceProviderPlace;
import emissary.util.io.ResourceReader;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public abstract class IdentificationTest extends UnitTest {

    protected static Logger logger = LoggerFactory.getLogger(IdentificationTest.class);

    protected static IServiceProviderPlace place = null;

    @BeforeEach
    public void setUpPlace() throws Exception {
        place = createPlace();
    }

    @AfterEach
    public void tearDownPlace() {
        if (place != null) {
            place.shutDown();
            place = null;
        }
    }

    /**
     * Derived classes must implement this
     */
    public abstract IServiceProviderPlace createPlace() throws IOException;

    public static Stream<? extends Arguments> data() {
        return getMyTestParameterFiles(IdentificationTest.class);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testIdentificationPlace(String resource) {
        logger.debug("Running {} test on resource {}", place.getClass().getName(), resource);

        try (InputStream doc = new ResourceReader().getResourceAsStream(resource)) {
            byte[] data = IOUtils.toByteArray(doc);
            String expectedAnswer = resolveFormForResource(resource);
            IBaseDataObject payload = DataObjectFactory.getInstance(data, resource, Form.UNKNOWN);
            processPreHook(payload, resource);
            place.agentProcessHeavyDuty(payload);
            processPostHook(payload, resource);
            checkAnswersPreHook(payload, resource, expectedAnswer);
            checkAnswers(payload, resource, expectedAnswer);
            checkAnswersPostHook(payload, resource, expectedAnswer);
        } catch (Exception ex) {
            logger.error("Error running test {}", resource, ex);
            fail("Cannot run test " + resource, ex);
        }
    }

    /**
     * Resolves the expected form for the test resource.
     *
     * @param resource complete file path of the test resource
     * @return form expected for the resource
     */
    protected String resolveFormForResource(String resource) {
        return resource.replaceAll("^.*/([^/@]+)(@\\d+)?\\.dat$", "$1");
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
            if (expectedForms[i].indexOf("(") > 0) {
                assertEquals(expectedForms[i], result, "Current form is wrong in " + resource);
            } else {
                assertEquals(expectedForms[i], result.replaceAll("\\(.*\\)", ""), "Current form is wrong in " + resource);
            }
        }
    }
}
