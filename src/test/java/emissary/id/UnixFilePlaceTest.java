package emissary.id;

import java.io.IOException;
import java.util.Collection;

import emissary.place.IServiceProviderPlace;
import emissary.test.core.IdentificationTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UnixFilePlaceTest extends IdentificationTest {

    @Parameterized.Parameters
    public static Collection<?> data() {
        return getMyTestParameterFiles(UnixFilePlaceTest.class);
    }

    public UnixFilePlaceTest(final String resource) throws IOException {
        super(resource);
    }

    @Override
    public IServiceProviderPlace createPlace() throws IOException {
        return new UnixFilePlace();
    }
}
