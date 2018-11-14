package emissary.id;

import java.io.IOException;
import java.util.Collection;

import emissary.test.core.IdentificationTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TikaFilePlaceTest extends IdentificationTest {

    static {
        PLACE_UNDER_TEST = "emissary.id.TikaFilePlace";
    }

    @Parameterized.Parameters
    public static Collection<?> data() {
        return getMyTestParameterFiles(TikaFilePlaceTest.class);
    }

    public TikaFilePlaceTest(final String resource) throws IOException {
        super(resource);
    }
}
