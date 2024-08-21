package emissary.place;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.core.ResourceException;
import emissary.test.core.junit5.UnitTest;
import emissary.util.Version;
import emissary.util.io.ResourceReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class VersionPlaceTest extends UnitTest {
    private IBaseDataObject payload;
    private VersionPlace place;
    private Version version;
    private String versionDate;

    @Override
    @BeforeEach
    public void setUp() throws IOException {
        payload = DataObjectFactory.getInstance();
        version = new Version();
        versionDate = version.getTimestamp().replaceAll("\\D", "");
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        place.shutDown();
        place = null;
        payload = null;
        versionDate = null;
    }

    @Test
    void testAddVersionToPayload() throws ResourceException, IOException {
        // create the place, using the normal class cfg
        place = new VersionPlace();

        place.process(payload);
        assertEquals(payload.getStringParameter("EMISSARY_VERSION"), version.getVersion() + "-" + versionDate,
                "added version should contain the date.");
    }

    @Test
    void testAddVersionWithoutDate() throws ResourceException, IOException {
        // create the place with the test cfg, having INCLUDE_DATE = "false"
        InputStream is = new ResourceReader().getConfigDataAsStream(this.getClass());
        place = new VersionPlace(is);

        place.process(payload);
        assertFalse(payload.getStringParameter("EMISSARY_VERSION").contains(versionDate), "the date should not be added to the version");
        assertEquals(payload.getStringParameter("EMISSARY_VERSION"), version.getVersion(), "the version should be added");
    }
}
