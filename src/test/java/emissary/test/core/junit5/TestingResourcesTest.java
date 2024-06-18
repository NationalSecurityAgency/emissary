package emissary.test.core.junit5;

import emissary.place.IServiceProviderPlace;
import emissary.transform.HtmlEscapePlace;
import emissary.transform.HtmlEscapePlaceTest;

import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.util.stream.Stream;

/**
 * Explicitly meant to test data and answer files from different directories. This test uses {@link HtmlEscapePlaceTest}
 * sample.dat with the sample.xml in {@link TestingResourcesTest} directory.
 */
public class TestingResourcesTest extends ExtractionTest {

    public static Stream<? extends Arguments> data() {
        return getMyTestParameterFiles(HtmlEscapePlaceTest.class);
    }

    @Override
    public IServiceProviderPlace createPlace() throws IOException {
        return new HtmlEscapePlace();
    }


}
