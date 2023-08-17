package emissary.client;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;

import com.google.common.annotations.VisibleForTesting;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.util.Timeout;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.security.Password;
import org.glassfish.jersey.server.filter.CsrfProtectionFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;

/**
 * Base class of all the actions that use HttpClient.
 */
public class EmissaryClient {

    public static final String DEFAULT_CONTEXT = "emissary";
    public static final String JETTY_USER_FILE_PROPERTY_NAME = "emissary.jetty.users.file";
    public static final int DEFAULT_CONNECTION_TIMEOUT = (int) TimeUnit.MINUTES.toMillis(100L); // 2 X 50 min
    public static final int DEFAULT_CONNECTION_MANAGER_TIMEOUT = (int) TimeUnit.MINUTES.toMillis(2L);
    public static final int DEFAULT_SO_TIMEOUT = (int) TimeUnit.MINUTES.toMillis(1L);
    public static final int DEFAULT_RETRIES = 3;
    public static final String DEFAULT_USERNAME = "emissary";
    public static final String DEFAULT_PASSWORD = "password";
    public static final String CSRF_HEADER_PARAM = CsrfProtectionFilter.HEADER_NAME;

    private static final Logger LOGGER = LoggerFactory.getLogger(EmissaryClient.class);

    private static final PoolingHttpClientConnectionManager CONNECTION_MANAGER = HTTPConnectionFactory.getFactory().getDefaultConnectionManager();
    // some default objects to use
    private static final BasicCredentialsProvider CRED_PROV = new BasicCredentialsProvider();
    protected static final int ANY_PORT = -1;
    protected static final String ANY_HOST = null;

    private static CloseableHttpClient staticClient = null;
    private static RequestConfig staticRequestConfig = null;
    private static ConnectionConfig staticConnectionConfig = null;

    // static config variables
    public static String CONTEXT = DEFAULT_CONTEXT;
    protected static int retries = DEFAULT_RETRIES;
    protected static String username = DEFAULT_USERNAME;
    // How long to wait while establishing a connection (ms)
    protected static int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    // How long to wait for a connection from the pool (ms)
    protected static int connectionManagerTimeout = DEFAULT_CONNECTION_MANAGER_TIMEOUT;
    // How long to wait on a data read in a connection (ms)
    protected static int socketTimeout = DEFAULT_SO_TIMEOUT;
    // class is thread-safe
    protected static final AuthCache AUTH_CACHE = new BasicAuthCache();

    private CloseableHttpClient client;
    private RequestConfig requestConfig;
    private ConnectionConfig connectionConfig;

    static {
        configure();
    }

    @VisibleForTesting
    protected static void configure() {
        LOGGER.debug("Configuring EmissaryClient");

        // parse configs
        try {
            final Configurator c = ConfigUtil.getConfigInfo(EmissaryClient.class);
            retries = c.findIntEntry("retries", DEFAULT_RETRIES);
            username = c.findStringEntry("username", DEFAULT_USERNAME);
            connectionTimeout = c.findIntEntry("connectionTimeout", DEFAULT_CONNECTION_TIMEOUT);
            connectionManagerTimeout = c.findIntEntry("connectionManagerTimeout", DEFAULT_CONNECTION_MANAGER_TIMEOUT);
            socketTimeout = c.findIntEntry("soTimeout", DEFAULT_SO_TIMEOUT);
            CONTEXT = c.findStringEntry("context", DEFAULT_CONTEXT);
        } catch (IOException iox) {
            LOGGER.warn("Cannot read EmissaryClient properties, configuring defaults: {}", iox.getMessage());
            retries = DEFAULT_RETRIES;
            username = DEFAULT_USERNAME;
            connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
            connectionManagerTimeout = DEFAULT_CONNECTION_MANAGER_TIMEOUT;
            socketTimeout = DEFAULT_SO_TIMEOUT;
            CONTEXT = DEFAULT_CONTEXT;
        }

        // Read the jetty user realm formatted property file for the password
        // Value for password remains unchanged if there is a problem
        try {
            String userPropertiesFile = System.getProperty(JETTY_USER_FILE_PROPERTY_NAME);
            if (null == userPropertiesFile) {
                LOGGER.debug("System property '{}' not set, using default jetty-users.properties", JETTY_USER_FILE_PROPERTY_NAME);
                userPropertiesFile = "jetty-users.properties";
            }
            LOGGER.debug("Reading password from {}", userPropertiesFile);
            final Properties props = ConfigUtil.getPropertyInfo(userPropertiesFile);
            String pass = DEFAULT_PASSWORD;
            final String value = props.getProperty(username, pass);
            if (value != null && value.indexOf(',') != -1) {
                pass = value.substring(0, value.indexOf(',')).trim();
            } else if (pass.equals(value)) {
                LOGGER.error("Error reading password from {}", userPropertiesFile);
            }
            // Supply default credentials for anyone we want to connect to
            final String decodedPassword = pass.startsWith("OBF:") ? Password.deobfuscate(pass) : pass;
            final Credentials cred = new UsernamePasswordCredentials(username, decodedPassword.toCharArray());
            CRED_PROV.setCredentials(new AuthScope(ANY_HOST, ANY_PORT), cred);
        } catch (IOException iox) {
            LOGGER.error("Cannot read {} in EmissaryClient, defaulting credentials", System.getProperty(JETTY_USER_FILE_PROPERTY_NAME));
            final Credentials cred = new UsernamePasswordCredentials(username, DEFAULT_PASSWORD.toCharArray());
            final AuthScope authScope = new AuthScope(ANY_HOST, ANY_PORT);
            CRED_PROV.setCredentials(authScope, cred);
        }

        staticRequestConfig =
                RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionManagerTimeout))
                        .setTargetPreferredAuthSchemes(Collections.singleton(StandardAuthScheme.DIGEST))
                        .setProxyPreferredAuthSchemes(Collections.singleton(StandardAuthScheme.DIGEST))
                        .build();

        staticConnectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectionTimeout))
                .setSocketTimeout(Timeout.ofMilliseconds(socketTimeout)).build();

        CONNECTION_MANAGER.setDefaultConnectionConfig(staticConnectionConfig);

        staticClient =
                HttpClientBuilder.create().setConnectionManager(CONNECTION_MANAGER).setDefaultCredentialsProvider(CRED_PROV)
                        .setDefaultRequestConfig(staticRequestConfig).build();

    }

    public EmissaryClient() {
        this(staticClient, staticRequestConfig, staticConnectionConfig);
    }

    public EmissaryClient(RequestConfig requestConfig, ConnectionConfig connectionConfig) {
        this(staticClient, requestConfig, connectionConfig);
    }

    public EmissaryClient(CloseableHttpClient client) {
        this(client, staticRequestConfig, staticConnectionConfig);
    }

    public EmissaryClient(CloseableHttpClient client, RequestConfig requestConfig, ConnectionConfig connectionConfig) {
        this.client = client;
        this.requestConfig = requestConfig;
        this.connectionConfig = connectionConfig;
    }

    public EmissaryResponse send(final HttpUriRequestBase method) {
        return send(method, null);
    }

    /**
     * Sends a request to the web server. The request can be any HttpMethod. Adds the specified cookie to the Http State
     *
     * @param method the method to be sent
     * @param cookie a cookie to set on the request
     */
    public EmissaryResponse send(final HttpUriRequestBase method, @Nullable final Cookie cookie) {
        try {
            LOGGER.debug("Sending {} to {}", method.getMethod(), method.getUri());
        } catch (URISyntaxException e) {
            LOGGER.debug("Sending {} and failed to retrieve URI", method.getMethod());
        }
        EmissaryResponse er;

        HttpClientContext localContext = HttpClientContext.create();
        localContext.setAttribute(HttpClientContext.AUTH_CACHE, EmissaryClient.AUTH_CACHE);

        if (cookie != null) {
            localContext.getCookieStore().addCookie(cookie);
        }

        try {
            // This is thread safe. The client is instantiated in a static block above
            // with a connection pool. Calling new EmissaryClient().send allows you
            // to use a different context and request config per request
            method.setConfig(requestConfig);
            CloseableHttpClient thisClient = getHttpClient();
            try (CloseableHttpResponse response = thisClient.execute(method, localContext)) {
                HttpEntity entity = response.getEntity();
                er = new EmissaryResponse(response);
                EntityUtils.consume(entity);
            }
            return er;
        } catch (IOException e) {
            LOGGER.debug("Problem processing request:", e);
            BasicClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.INTERNAL_SERVER_ERROR_500, e.getMessage());
            response.setEntity(EntityBuilder.create().setText(e.getClass() + ": " + e.getMessage()).setContentEncoding(MediaType.TEXT_PLAIN).build());
            return new EmissaryResponse(response);
        }
    }

    protected CloseableHttpClient getHttpClient() {
        return client;
    }

    protected RequestConfig getRequestConfig() {
        return requestConfig;
    }

    protected ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }


    public void setConnectionTimeout(int timeout) {
        if (timeout > 0) {
            connectionConfig = ConnectionConfig.copy(connectionConfig).setConnectTimeout(Timeout.ofMilliseconds(timeout)).build();
        } else {
            LOGGER.warn("Tried to set timeout to {}", timeout);
        }
    }

    protected String getCsrfToken() {
        return DEFAULT_CONTEXT;
    }

    public HttpPost createHttpPost(String uri, String context, String endpoint) {
        return createHttpPost(uri + context + endpoint, getCsrfToken());
    }

    public HttpPost createHttpPost(String uri) {
        return createHttpPost(uri, getCsrfToken());
    }

    public HttpPost createHttpPost(String uri, String csrfToken) {
        HttpPost method = new HttpPost(uri);
        setCsrfHeader(method, csrfToken);
        return method;
    }

    public HttpUriRequestBase setCsrfHeader(HttpUriRequestBase request, String csrfToken) {
        return setCsrfHeader(request, CSRF_HEADER_PARAM, csrfToken);
    }

    public HttpUriRequestBase setCsrfHeader(HttpUriRequestBase request, String csrfParam, String csrfToken) {
        request.addHeader(csrfParam, csrfToken);
        return request;
    }

}
