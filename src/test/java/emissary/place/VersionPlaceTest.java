package emissary.place;

import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.core.ResourceException;
import emissary.test.core.junit5.UnitTest;
import emissary.util.GitRepositoryState;
import emissary.util.io.ResourceReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VersionPlaceTest extends UnitTest {
    @Nullable
    private IBaseDataObject payload;
    @Nullable
    private VersionPlace place;
    private Path gitRepositoryFile;
    private GitRepositoryState testGitRepoState;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        payload = DataObjectFactory.getInstance();
        gitRepositoryFile = Paths.get(new ResourceReader().getResource("emissary/util/test.git.properties").toURI());
        testGitRepoState = GitRepositoryState.getRepositoryState(gitRepositoryFile);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        place.shutDown();
        place = null;
        payload = null;
    }

    @Test
    void testAddVersionToPayload() throws ResourceException, IOException {
        // create the place, using the normal class cfg
        place = new MyVersionPlace();

        place.process(payload);
        assertEquals(testGitRepoState.getBuildVersion() + "-20240828141716", payload.getStringParameter("EMISSARY_VERSION"),
                "added version should contain the date.");
    }

    @Test
    void testAddVersionWithoutDate() throws ResourceException, IOException {
        // create the place with the test cfg, having INCLUDE_DATE = "false"
        InputStream is = new ResourceReader().getConfigDataAsStream(this.getClass());
        place = new MyVersionPlace(is);

        place.process(payload);
        assertFalse(payload.getStringParameter("EMISSARY_VERSION").contains("-20240828141716"), "the date should not be added to the version");
        assertEquals(testGitRepoState.getBuildVersion(), payload.getStringParameter("EMISSARY_VERSION"), "the version should be added");
    }

    @Test
    void testAddVersionHash() throws ResourceException, IOException {
        // create the place, using the normal class cfg
        place = new MyVersionPlace();

        place.process(payload);
        assertEquals(testGitRepoState.getCommitIdAbbrev(), payload.getStringParameter("EMISSARY_VERSION_HASH").substring(0, 7),
                "EMISSARY_VERSION_HASH should contain (at least) the abbreviated hash");
    }

    @Test
    void testVersionRegex() throws IOException, URISyntaxException {
        // uses different test gitRepoState properties file with version number that matches default regex
        // passes this file to getVersion() to verify version returned has no date
        Path regexTestFile = Paths.get(new ResourceReader().getResource("emissary/util/test.version.regex.git.properties").toURI());
        GitRepositoryState regexRepoState = GitRepositoryState.getRepositoryState(regexTestFile);

        // create the place, using the normal class cfg
        place = new MyVersionPlace();
        assertFalse(place.getVersion(regexRepoState).contains("-20240828141716"),
                "the date should not be added to the version due to the matching release (default) regex");
    }

    class MyVersionPlace extends VersionPlace {
        MyVersionPlace() throws IOException {
            super(new ResourceReader().getConfigDataAsStream(VersionPlace.class));
        }

        MyVersionPlace(InputStream is) throws IOException {
            super(is);
        }

        @Override
        GitRepositoryState initGitRepositoryState() {
            return GitRepositoryState.getRepositoryState(gitRepositoryFile);
        }
    }
}
