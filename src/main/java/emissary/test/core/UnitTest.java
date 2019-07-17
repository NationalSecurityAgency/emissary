package emissary.test.core;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.List;

import emissary.command.ServerCommand;
import emissary.config.ConfigUtil;
import emissary.core.EmissaryException;
import emissary.util.io.ResourceReader;
import org.jdom2.Document;
import org.jdom2.input.SAXBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class of all the unit tests
 */
public class UnitTest {
    /**
     * A test rule that overwrites the failed method in junit and prints info about the failure to System.err. Keeps you
     * from having to go to the junit report files to see what was wrong
     */
    public class DumpFailures extends TestWatcher {
        @Override
        protected void failed(Throwable e, Description description) {
            System.err.println("" + description.getDisplayName() + " failed " + e.getMessage());
            super.failed(e, description);
        }
    };

    // Now use it for all tests, will have to chain if using both DumpFailures and Retry
    @Rule
    public DumpFailures dump = new DumpFailures();

    /**
     * Test rule to retry a failed test. If this is happening, it is likely that the test is bad so try to fix that.
     */
    public class Retry implements TestRule {
        private int retryCount;

        public Retry(int retryCount) {
            this.retryCount = retryCount;
        }

        public Statement apply(Statement base, Description description) {
            return statement(base, description);
        }

        private Statement statement(final Statement base, final Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    Throwable caughtThrowable = null;

                    // implement retry logic here
                    for (int i = 0; i < retryCount; i++) {
                        try {
                            base.evaluate();
                            return;
                        } catch (Throwable t) {
                            caughtThrowable = t;
                            System.err.println(description.getDisplayName() + ": run " + (i + 1) + " failed");
                        }
                    }
                    System.err.println(description.getDisplayName() + ": giving up after " + retryCount + " failures");
                    throw caughtThrowable;
                }
            };
        }
    }

    protected Package utPackage = UnitTest.class.getPackage();
    protected Package thisPackage = null;

    // Runtime typed logger
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    // Config pointers
    protected String origConfigPkg = null;

    // TODO: remove this and see what breaks
    protected String TMPDIR = System.getProperty("java.io.tmpdir", ".");

    /**
     * Create a UnitTest
     */
    public UnitTest() {
        configure();
    }

    /**
     * Create a named unit test
     */
    public UnitTest(String name) {
        configure();
    }

    public synchronized void setupSystemProperties() {
        // mostly just to get the system properties set
        // synchronized since multiple threads are testing at the same time
        String projectBase = System.getenv("PROJECT_BASE");
        if (projectBase == null) {
            fail("PROJECT_BASE is not set");
        }
        // setup the environment stuff
        try {
            ServerCommand.parse(ServerCommand.class, "-m", "cluster").setupCommand();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            e.printStackTrace();
            fail("Unable to setup Emissary environment");
        }
    }

    /**
     * Configure the test stuff
     * <p>
     * Beware though, if you use @BeforeClass this will not have had a change to run. So you can do something like
     * 
     * new UnitTest().setupSystemProperties();
     * 
     * in the @BeforeClass. See FlexibleDateTimeParserTest for an example
     */
    protected void configure() {
        thisPackage = this.getClass().getPackage();
        setupSystemProperties();
    }

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {
        restoreConfig();
        assertMaxNonSystemThreadCount(1);
    }

    public void assertMaxNonSystemThreadCount(int max) {
        emissary.util.ThreadDump td = new emissary.util.ThreadDump();
        ThreadInfo[] ti = td.getThreadInfo(true);
        if (ti.length > max) {
            StringBuilder sb = new StringBuilder();
            for (ThreadInfo t : ti) {
                sb.append(t.getThreadName()).append(" ");
            }
            assertTrue("Not expecting " + ti.length + " threads from " + this.getClass().getName() + ": " + sb, max <= ti.length);
        }
    }

    /**
     * Get all test resources (*.dat) for this class
     */
    protected List<String> getMyTestResources() {
        ResourceReader rr = new ResourceReader();
        return rr.findDataResourcesFor(this.getClass());
    }

    /**
     * Get all test resources (*.dat) for this class in a format suitable for the Junit Parameterized runner
     */
    public static List<Object[]> getMyTestParameterFiles(Class<?> clz) {
        ResourceReader rr = new ResourceReader();
        List<String> rs = rr.findDataResourcesFor(clz);
        List<Object[]> al = new ArrayList<Object[]>();
        for (String r : rs) {
            String[] s = {r};
            al.add(s);
        }
        return al;
    }

    /**
     * Get all xml resources (*.xml) for this class
     */
    protected List<String> getMyXmlResources() {
        ResourceReader rr = new ResourceReader();
        return rr.findXmlResourcesFor(this.getClass());
    }

    /**
     * Pause for the specified number of millis without throwing any exceptions when it doesn't work
     *
     * @param millis how long to pause
     */
    protected void pause(long millis) {
        Thread.yield();
        try {
            Thread.sleep(millis);
        } catch (Exception ex) {
            // empty exception block
            logger.debug("Thread interrupted", ex);
        }
    }

    /**
     * Set up configuration
     *
     * @param configPath The path to use for config.dir, or null if the value should not be changed.
     * @param pkg use this.pkg for config.pkg
     */
    protected void setConfig(final String configPath, boolean pkg) throws EmissaryException {
        // TODO: refactor this. Changing the pkg affected toResourceName, which could have the
        // same negative affect as change the config dir property. Then get rid of this
        // origConfigDir = System.getProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        origConfigPkg = System.getProperty(ConfigUtil.CONFIG_PKG_PROPERTY);
        if (configPath != null) {
            throw new UnsupportedOperationException("We no longer use a tmp directory, fix this");
            // System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, configPath);
        }
        if (pkg) {
            System.setProperty(ConfigUtil.CONFIG_PKG_PROPERTY, thisPackage.getName());
        }
        emissary.config.ConfigUtil.initialize();
    }

    /**
     * Restore config dir and pkg to original values
     */
    protected void restoreConfig() throws EmissaryException {
        // Restore config paths
        // if (origConfigDir != null) {
        // System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, origConfigDir);
        // origConfigDir = null;
        // }
        if (origConfigPkg != null) {
            System.setProperty(ConfigUtil.CONFIG_PKG_PROPERTY, origConfigPkg);
            origConfigPkg = null;
        }

        emissary.config.ConfigUtil.initialize();
    }

    /**
     * Get an JDOM XML document corresponding to a test resource
     */
    protected Document getAnswerDocumentFor(String resource) {
        int datPos = resource.lastIndexOf(ResourceReader.DATA_SUFFIX);
        if (datPos == -1) {
            logger.debug("Resource is not a DATA file {}", resource);
            return null;
        }

        String aname = resource.substring(0, datPos) + ResourceReader.XML_SUFFIX;
        SAXBuilder builder = new SAXBuilder(org.jdom2.input.sax.XMLReaders.NONVALIDATING);
        Document answerDoc = null;
        try (InputStream is = new ResourceReader().getResourceAsStream(aname)) {
            answerDoc = builder.build(is);
        } catch (Exception ex) {
            logger.debug("No answer document provided for {}", aname, ex);
            return null;
        }
        return answerDoc;
    }


}
