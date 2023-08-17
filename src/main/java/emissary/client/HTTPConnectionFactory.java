package emissary.client;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.util.PkiUtil;

import com.google.common.annotations.VisibleForTesting;
import org.apache.hc.client5.http.impl.DefaultClientConnectionReuseStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.annotation.Nullable;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Emissary HTTP Connection Factory. This is a singleton class that allows for the central configuration of an Apache
 * HTTP Client Connection manager and also provides a method for building default HTTP Clients. This object can be
 * configured by providing an HTTPConnectionFactory.cfg with the following:<br>
 *
 * <pre>
 * // Standard SSL Properties
 * javax.net.ssl.trustStore = "[Path to trust store]"
 * javax.net.ssl.trustStoreType = "[Trust Store type, defaults to JKS]"
 * javax.net.ssl.trustStorePassword = "[Trust store password OR path to file, see below]"
 * javax.net.ssl.keyStore = "[Path to key store]"
 * javax.net.ssl.keyStoreType = "[Key Store type, defaults to JKS]"
 * javax.net.ssl.keyStorePassword = "[Key store password OR path to file, see below]"
 * </pre>
 * <p>
 * Password configs: For the key or trust store options, if the values are prepended with "file://", this class will
 * attempt to load the password from the file on the path. This is intended to be a single line text file. This is
 * provided to allow for passwords to be placed in limited access files and directories and to eliminate the need to
 * pass these options in JVM System properties which are easily found.
 */
public class HTTPConnectionFactory {

    static final String CFG_TRUST_STORE = "javax.net.ssl.trustStore";
    static final String CFG_TRUST_STORE_TYPE = "javax.net.ssl.trustStoreType";
    static final String CFG_TRUST_STORE_PW = "javax.net.ssl.trustStorePassword";
    static final String CFG_KEY_STORE = "javax.net.ssl.keyStore";
    static final String CFG_KEY_STORE_TYPE = "javax.net.ssl.keyStoreType";
    static final String CFG_KEY_STORE_PW = "javax.net.ssl.keyStorePassword";
    static final String CFG_HTTP_KEEPALIVE = "http.keepAlive";
    static final String CFG_HTTP_MAXCONNS = "http.maxConnections";
    static final String CFG_HTTP_AGENT = "http.agent";
    static final String CFG_NOOP_VERIFIER = "https.useNoopHostnameVerifier";
    static final String CFG_SSLCONTEXT_TYPE = "emissary.sslcontext.type";
    static final String DEFAULT_HTTP_AGENT = "emissary";
    static final int DFLT_MAXCONNS = 200;
    static final boolean DFLT_KEEPALIVE = true;
    static final String DFLT_STORE_TYPE = "JKS";
    static final String DFLT_CONTEXT_TYPE = "TLS";
    // meaningful constants
    private static final String HTTP = "http";
    private static final String HTTPS = "https";

    private static final Logger log = Logger.getLogger(HTTPConnectionFactory.class);

    // singleton
    private static final HTTPConnectionFactory FACTORY = new HTTPConnectionFactory();

    final PoolingHttpClientConnectionManager connMan;

    private ConnectionReuseStrategy connReuseStrategy = DefaultClientConnectionReuseStrategy.INSTANCE;

    int maxConns = DFLT_MAXCONNS;

    String userAgent = DEFAULT_HTTP_AGENT;

    private HTTPConnectionFactory() {
        this(null);
    }

    @VisibleForTesting
    HTTPConnectionFactory(@Nullable final Configurator config) {
        Registry<ConnectionSocketFactory> registry = null;
        try {
            final Configurator cfg = config == null ? ConfigUtil.getConfigInfo(HTTPConnectionFactory.class) : config;
            // if someone doesn't want keep alives...
            if (!cfg.findBooleanEntry(CFG_HTTP_KEEPALIVE, DFLT_KEEPALIVE)) {
                this.connReuseStrategy = (httpRequest, httpResponse, httpContext) -> false;
            }
            this.maxConns = cfg.findIntEntry(CFG_HTTP_MAXCONNS, DFLT_MAXCONNS);
            this.userAgent = cfg.findStringEntry(CFG_HTTP_AGENT, DEFAULT_HTTP_AGENT);
            final SSLContext sslContext = build(cfg);
            // mainly for using in test environments where cert name may not match host name
            final HostnameVerifier v = cfg.findBooleanEntry(CFG_NOOP_VERIFIER, false) ? new NoopHostnameVerifier() : new DefaultHostnameVerifier();
            registry =
                    RegistryBuilder.<ConnectionSocketFactory>create().register(HTTP, PlainConnectionSocketFactory.getSocketFactory())
                            .register(HTTPS, new SSLConnectionSocketFactory(sslContext, v)).build();
        } catch (IOException | GeneralSecurityException ex) {
            log.error("Error configuring HTTPConnectionFactory. The connection factory will use HTTP Client default settings", ex);
        }
        if (registry == null) {
            this.connMan = new PoolingHttpClientConnectionManager();
        } else {
            this.connMan = new PoolingHttpClientConnectionManager(registry);
        }

        this.connMan.setMaxTotal(this.maxConns);
    }

    /**
     * This method will attempt to configure an SSLSocketFactory using configuration parameters from the
     * HTTPConnectionFactory.cfg.
     *
     * @param cfg The configurator.
     * @return the SSLContext
     * @throws IOException If there is some I/O problem.
     * @throws GeneralSecurityException If there is some security problem.
     */
    SSLContext build(final Configurator cfg) throws IOException, GeneralSecurityException {
        final char[] kpChar = PkiUtil.loadPW(cfg.findStringEntry(CFG_KEY_STORE_PW));
        final char[] tsChar = PkiUtil.loadPW(cfg.findStringEntry(CFG_TRUST_STORE_PW));

        final KeyStore keyStore =
                PkiUtil.buildStore(cfg.findStringEntry(CFG_KEY_STORE), kpChar, cfg.findStringEntry(CFG_KEY_STORE_TYPE, DFLT_STORE_TYPE));
        final KeyStore trustStore =
                PkiUtil.buildStore(cfg.findStringEntry(CFG_TRUST_STORE), tsChar, cfg.findStringEntry(CFG_TRUST_STORE_TYPE, DFLT_STORE_TYPE));
        if ((trustStore == null) && (keyStore == null)) {
            log.debug("Trust Store and Key Store are null. Using JDK default SSLContext");
            return SSLContext.getDefault();
        }

        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, kpChar);

        final SSLContext sc = SSLContext.getInstance(cfg.findStringEntry(CFG_SSLCONTEXT_TYPE, DFLT_CONTEXT_TYPE));
        sc.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        return sc;
    }


    /**
     * Return the configured connection manager with TLS SSL if configured.
     *
     * @return the connection manager
     */
    public PoolingHttpClientConnectionManager getDefaultConnectionManager() {
        return this.connMan;
    }

    /**
     * Returns a CloseableHttpClient using the configuration options of the factory singleton. Detailed information:
     * <ul>
     * <li>The connection manager will be set
     * <li>The Client will have the connection manager marked as shared to preserve cached connections
     * <li>The Client will use the configured reuse strategy (HTTP Keep Alive)
     * </ul>
     *
     * @return a CloseableHttpClient
     */
    public CloseableHttpClient buildDefaultClient() {
        return HttpClientBuilder.create().setConnectionManager(this.connMan).setConnectionManagerShared(true).setUserAgent(this.userAgent)
                .setConnectionReuseStrategy(this.connReuseStrategy).build();
    }

    /**
     * Returns the Factory
     *
     * @return the connection factory
     */
    public static HTTPConnectionFactory getFactory() {
        return FACTORY;
    }
}
