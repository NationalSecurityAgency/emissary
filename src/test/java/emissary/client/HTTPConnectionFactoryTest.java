package emissary.client;

import static emissary.client.HTTPConnectionFactory.CFG_KEY_STORE;
import static emissary.client.HTTPConnectionFactory.CFG_KEY_STORE_PW;
import static emissary.client.HTTPConnectionFactory.CFG_KEY_STORE_TYPE;
import static emissary.client.HTTPConnectionFactory.CFG_TRUST_STORE;
import static emissary.client.HTTPConnectionFactory.CFG_TRUST_STORE_PW;
import static emissary.client.HTTPConnectionFactory.CFG_TRUST_STORE_TYPE;
import static emissary.client.HTTPConnectionFactory.DFLT_STORE_TYPE;

import javax.net.ssl.SSLContext;

import emissary.config.Configurator;
import emissary.config.ServiceConfigGuide;
import emissary.test.core.UnitTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 *
 */
public class HTTPConnectionFactoryTest extends UnitTest {

    private Configurator cfg;
    private static String projectBase = System.getenv("PROJECT_BASE"); // set in surefire config

    @Before
    @Override
    public void setUp() {
        this.cfg = Mockito.spy(new ServiceConfigGuide());
    }

    /**
     * Test of build method, of class HTTPConnectionFactory.
     */
    @Test
    public void testBuild() throws Exception {
        addKeystoreProps(this.cfg);
        addTrustStoreProps(this.cfg);
        final HTTPConnectionFactory instance = new HTTPConnectionFactory(this.cfg);

        Mockito.verify(this.cfg, Mockito.times(1)).findStringEntry(CFG_KEY_STORE);
        Mockito.verify(this.cfg, Mockito.times(1)).findStringEntry(CFG_KEY_STORE_PW);
        Mockito.verify(this.cfg, Mockito.times(1)).findStringEntry(CFG_KEY_STORE_TYPE, DFLT_STORE_TYPE);
        Mockito.verify(this.cfg, Mockito.times(1)).findStringEntry(CFG_TRUST_STORE);
        Mockito.verify(this.cfg, Mockito.times(1)).findStringEntry(CFG_TRUST_STORE_PW);
        Mockito.verify(this.cfg, Mockito.times(1)).findStringEntry(CFG_TRUST_STORE_TYPE, DFLT_STORE_TYPE);

        Assert.assertTrue(instance.maxConns == 200L);

        this.cfg = new ServiceConfigGuide();

        final SSLContext dflt = instance.build(this.cfg);

        Assert.assertTrue(SSLContext.getDefault() == dflt);
    }

    @Test
    public void loadPWFromFile() throws Exception {
        addKeystoreProps(this.cfg);
        addTrustStoreProps(this.cfg);

        this.cfg.removeEntry(CFG_KEY_STORE_PW, "password");
        this.cfg.addEntry(CFG_KEY_STORE_PW, "file://" + projectBase + "/test-classes/emissary/util/web/password.file");

        this.cfg.removeEntry(CFG_TRUST_STORE_PW, "password");
        this.cfg.addEntry(CFG_TRUST_STORE_PW, "file://" + projectBase + "/test-classes/emissary/util/web/password.file");

        final HTTPConnectionFactory instance = new HTTPConnectionFactory(this.cfg);

        final SSLContext fromConfig = instance.build(this.cfg);

        Assert.assertTrue(SSLContext.getDefault() != fromConfig);
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
