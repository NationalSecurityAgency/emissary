package emissary.id;

import java.io.IOException;
import java.util.Collection;

import emissary.place.IServiceProviderPlace;
import emissary.test.core.IdentificationTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TikaFilePlaceTest extends IdentificationTest {

    @Parameterized.Parameters
    public static Collection<?> data() {
        return getMyTestParameterFiles(TikaFilePlaceTest.class);
    }

    public TikaFilePlaceTest(final String resource) throws IOException {
        super(resource);
    }

    @Override
    public IServiceProviderPlace createPlace() throws IOException {
        return new TikaFilePlace();
    }
}
