package emissary.id;

import java.io.IOException;
import java.util.stream.Stream;

import emissary.place.IServiceProviderPlace;
import emissary.test.core.junit5.IdentificationTest;
import org.junit.jupiter.params.provider.Arguments;

class TikaFilePlaceTest extends IdentificationTest {

    public static Stream<? extends Arguments> data() {
        return getMyTestParameterFiles(TikaFilePlaceTest.class);
    }

    @Override
    public IServiceProviderPlace createPlace() throws IOException {
        return new TikaFilePlace();
    }
}
