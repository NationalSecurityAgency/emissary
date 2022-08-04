package emissary.client;

import static emissary.client.HTTPConnectionFactory.CFG_KEY_STORE;
import static emissary.client.HTTPConnectionFactory.CFG_KEY_STORE_PW;
import static emissary.client.HTTPConnectionFactory.CFG_KEY_STORE_TYPE;
import static emissary.client.HTTPConnectionFactory.CFG_TRUST_STORE;
import static emissary.client.HTTPConnectionFactory.CFG_TRUST_STORE_PW;
import static emissary.client.HTTPConnectionFactory.CFG_TRUST_STORE_TYPE;
import static emissary.client.HTTPConnectionFactory.DFLT_STORE_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import javax.net.ssl.SSLContext;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.test.core.UnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HTTPConnectionFactoryTest extends UnitTest {

    private Configurator cfg;
    private static final String projectBase = System.getenv("PROJECT_BASE"); // set in surefire config

    @BeforeEach
    @Override
    public void setUp() {
        this.cfg = Mockito.spy(new ServiceConfigGuide());
    }

    /**
     * Test of build method, of class HTTPConnectionFactory.
     */
    @Test
    void testBuild() throws Exception {
        addKeystoreProps(this.cfg);
        addTrustStoreProps(this.cfg);
        final HTTPConnectionFactory instance = new HTTPConnectionFactory(this.cfg);

        Mockito.verify(this.cfg, Mockito.times(1)).findStringEntry(CFG_KEY_STORE);
        Mockito.verify(this.cfg, Mockito.times(1)).findStringEntry(CFG_KEY_STORE_PW);
        Mockito.verify(this.cfg, Mockito.times(1)).findStringEntry(CFG_KEY_STORE_TYPE, DFLT_STORE_TYPE);
        Mockito.verify(this.cfg, Mockito.times(1)).findStringEntry(CFG_TRUST_STORE);
        Mockito.verify(this.cfg, Mockito.times(1)).findStringEntry(CFG_TRUST_STORE_PW);
        Mockito.verify(this.cfg, Mockito.times(1)).findStringEntry(CFG_TRUST_STORE_TYPE, DFLT_STORE_TYPE);

        assertEquals(200L, instance.maxConns);

        this.cfg = new ServiceConfigGuide();

        final SSLContext dflt = instance.build(this.cfg);

        assertSame(SSLContext.getDefault(), dflt);
    }

    @Test
    void loadPWFromFile() throws Exception {
        addKeystoreProps(this.cfg);
        addTrustStoreProps(this.cfg);

        this.cfg.removeEntry(CFG_KEY_STORE_PW, "password");
        this.cfg.addEntry(CFG_KEY_STORE_PW, "file://" + projectBase + "/test-classes/emissary/util/web/password.file");

        this.cfg.removeEntry(CFG_TRUST_STORE_PW, "password");
        this.cfg.addEntry(CFG_TRUST_STORE_PW, "file://" + projectBase + "/test-classes/emissary/util/web/password.file");

        final HTTPConnectionFactory instance = new HTTPConnectionFactory(this.cfg);

        final SSLContext fromConfig = instance.build(this.cfg);

        assertNotSame(SSLContext.getDefault(), fromConfig);
    }

    /**
     * Read a known environment variable configured during setup {@link UnitTest#setupSystemProperties()}
     *
     * @throws Exception thrown when an error occurs
     */
    @Test
    void loadPWFromEnv() throws Exception {
        char[] pw = HTTPConnectionFactory.loadPW("${PROJECT_BASE}");
        if (pw == null) {
            Assertions.fail("Failed to read environment variable");
        }
        Assertions.assertEquals(projectBase, String.valueOf(pw));
    }

    private static void addKeystoreProps(final Configurator cfg) {
        cfg.addEntry(CFG_KEY_STORE, projectBase + "/test-classes/certs/testkeystore.jks");
        cfg.addEntry(CFG_KEY_STORE_PW, "password");
        cfg.addEntry(CFG_KEY_STORE_TYPE, "JKS");
    }

    private static void addTrustStoreProps(final Configurator cfg) {
        cfg.addEntry(CFG_TRUST_STORE, projectBase + "/test-classes/certs/testtruststore.jks");
        cfg.addEntry(CFG_TRUST_STORE_PW, "password");
        cfg.addEntry(CFG_TRUST_STORE_TYPE, "JKS");
    }
}
