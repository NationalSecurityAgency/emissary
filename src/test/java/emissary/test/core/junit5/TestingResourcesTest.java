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

    static final Class<?> DATA_FILE_CLASS = HtmlEscapePlaceTest.class;
    static final Class<?> ANSWER_FILE_CLASS = TestingResourcesTest.class;

    public static Stream<? extends Arguments> data() {
        return getMyTestParameterFiles(DATA_FILE_CLASS);
    }

    public TestingResourcesTest() {
        useAlternateAnswerFileSource(ANSWER_FILE_CLASS);
    }

    @Override
    public IServiceProviderPlace createPlace() throws IOException {
        return new HtmlEscapePlace();
    }
}
