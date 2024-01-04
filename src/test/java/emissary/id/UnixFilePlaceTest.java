package emissary.id;

import emissary.core.DataObjectFactory;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.place.IServiceProviderPlace;
import emissary.test.core.junit5.IdentificationTest;
import emissary.test.core.junit5.LogbackTester;
import emissary.util.io.ResourceReader;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UnixFilePlaceTest extends IdentificationTest {

    public static Stream<? extends Arguments> data() {
        return getMyTestParameterFiles(UnixFilePlaceTest.class);
    }


    @Override
    public IServiceProviderPlace createPlace() throws IOException {
        return new UnixFilePlace();
    }

    @Test
    void testMultiStepRuleAtBottomOfMagicFile() throws Exception {

        String resource = "emissary/id/UnixFilePlaceTest/UNKNOWN@2.dat";
        try (LogbackTester logbackTester = new LogbackTester(UnixFilePlace.class.getName())) {
            try (InputStream doc = new ResourceReader().getResourceAsStream(resource)) {
                byte[] data = IOUtils.toByteArray(doc);
                IBaseDataObject payload = DataObjectFactory.getInstance(data, resource, Form.UNKNOWN);

                place.agentProcessHeavyDuty(payload);
                assertTrue(StringUtils.isBlank(payload.getProcessingError()), "Expected no processing error");
            }
            // the initialized values passed in here indicate that no exception has been thrown
            logbackTester.checkLogList(Collections.emptyList());
        }
    }

}
