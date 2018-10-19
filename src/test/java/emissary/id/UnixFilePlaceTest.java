package emissary.id;

import java.io.IOException;
import java.util.Collection;

import emissary.test.core.IdentificationTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UnixFilePlaceTest extends IdentificationTest {

    static {
        PLACE_UNDER_TEST = "emissary.id.UnixFilePlace";
    }

    @Parameterized.Parameters
    public static Collection<?> data() {
        return getMyTestParameterFiles(UnixFilePlaceTest.class);
    }

    public UnixFilePlaceTest(final String resource) throws IOException {
        super(resource);
    }
}
