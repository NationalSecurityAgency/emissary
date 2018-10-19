package emissary.server.mvc;

import javax.ws.rs.core.Application;

import emissary.core.Namespace;
import emissary.test.core.UnitTest;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.mvc.mustache.MustacheMvcFeature;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.AfterClass;

public class EndpointTestBase extends JerseyTest {

    @AfterClass
    public static void tearDownClass() {
        Namespace.clear();
    }

    // Handle setting up all the Jersey Test framework components
    @Override
    protected Application configure() {
        new UnitTest().setupSystemProperties();
        // Tells Jersey to use first available port, fixes address already in use exception
        forceSet(TestProperties.CONTAINER_PORT, "0");
        final ResourceConfig application = new ResourceConfig();
        application.register(MultiPartFeature.class);
        application.property(MustacheMvcFeature.TEMPLATE_BASE_PATH, "/templates");
        application.register(MustacheMvcFeature.class).packages("emissary.server.mvc");
        application.register(MustacheMvcFeature.class).packages("emissary.server.api");

        return application;
    }
}
