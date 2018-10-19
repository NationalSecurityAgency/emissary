package emissary.transform;

import java.io.IOException;
import java.util.Collection;

import emissary.place.IServiceProviderPlace;
import emissary.test.core.ExtractionTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class JavascriptEscapePlaceTest extends ExtractionTest {

    @Parameterized.Parameters
    public static Collection<?> data() {
        return getMyTestParameterFiles(JavascriptEscapePlaceTest.class);
    }

    public JavascriptEscapePlaceTest(String resource) throws IOException {
        super(resource);
    }


    @Override
    public IServiceProviderPlace createPlace() throws IOException {

        return new JavascriptEscapePlace();
    }

}
