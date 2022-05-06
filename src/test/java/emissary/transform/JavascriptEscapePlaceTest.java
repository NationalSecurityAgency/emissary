package emissary.transform;

import java.io.IOException;
import java.util.stream.Stream;

import emissary.place.IServiceProviderPlace;
import emissary.test.core.ExtractionTest;
import org.junit.jupiter.params.provider.Arguments;

public class JavascriptEscapePlaceTest extends ExtractionTest {

    public static Stream<? extends Arguments> data() {
        return getMyTestParameterFiles(JavascriptEscapePlaceTest.class);
    }

    @Override
    public IServiceProviderPlace createPlace() throws IOException {
        return new JavascriptEscapePlace();
    }
}
