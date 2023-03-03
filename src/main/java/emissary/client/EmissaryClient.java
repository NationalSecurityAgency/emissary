package emissary.client;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;

import com.google.common.annotations.VisibleForTesting;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.util.EntityUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.security.Password;
import org.glassfish.jersey.server.filter.CsrfProtectionFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
    public static final int DEFAULT_CONNECTION_MANAGER_TIMEOUT = (int) TimeUnit.MINUTES.toMicros(2L);
    public static final int DEFAULT_SO_TIMEOUT = (int) TimeUnit.MINUTES.toMillis(1L);
    public static final int DEFAULT_RETRIES = 3;
    public static final String DEFAULT_USERNAME = "emissary";
    public static final String DEFAULT_PASSWORD = "password";
    public static final String CSRF_HEADER_PARAM = CsrfProtectionFilter.HEADER_NAME;

    private static final Logger LOGGER = LoggerFactory.getLogger(EmissaryClient.class);

    private static final PoolingHttpClientConnectionManager CONNECTION_MANAGER = HTTPConnectionFactory.getFactory().getDefaultConnectionManager();
    // some default objects to use
    private static final CredentialsProvider CRED_PROV = new BasicCredentialsProvider();

    private static CloseableHttpClient staticClient = null;
    private static RequestConfig staticRequestConfig = null;

    // static config variables
    public static String CONTEXT = DEFAULT_CONTEXT;
    protected static int retries = DEFAULT_RETRIES;
    protected static String username = DEFAULT_USERNAME;
    protected static String realm = AuthScope.ANY_REALM;
    // How long to wait while establishing a connection (ms)
    protected static int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    // How long to wait for a connection from the pool (ms)
    protected static int connectionManagerTimeout = DEFAULT_CONNECTION_MANAGER_TIMEOUT;
    // How long to wait on a data read in a connection (ms)
    protected static int socketTimeout = DEFAULT_SO_TIMEOUT;
    // class is thread-safe
    protected static final AuthCache AUTH_CACHE = new BasicAuthCache();

    private CloseableHttpClient client = null;
    private RequestConfig requestConfig = null;

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
            connectionTimeout = (int) Math.min(c.findLongEntry("connectionTimeout", DEFAULT_CONNECTION_TIMEOUT), Integer.MAX_VALUE);
            connectionManagerTimeout =
                    (int) Math.min(c.findLongEntry("connectionManagerTimeout", DEFAULT_CONNECTION_MANAGER_TIMEOUT), Integer.MAX_VALUE);
            socketTimeout = (int) Math.min(c.findLongEntry("soTimeout", DEFAULT_SO_TIMEOUT), Integer.MAX_VALUE);
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
            final String decodedPassword = pass != null && pass.startsWith("OBF:") ? Password.deobfuscate(pass) : pass;
            final Credentials cred = new UsernamePasswordCredentials(username, decodedPassword);
            CRED_PROV.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, realm), cred);
        } catch (IOException iox) {
            LOGGER.error("Cannot read {} in EmissaryClient, defaulting credentials", System.getProperty(JETTY_USER_FILE_PROPERTY_NAME));
            final Credentials cred = new UsernamePasswordCredentials(username, DEFAULT_PASSWORD);
            final AuthScope authScope = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, realm);
            CRED_PROV.setCredentials(authScope, cred);
        }

        staticRequestConfig =
                RequestConfig.custom().setConnectTimeout(connectionTimeout)
                        .setConnectionRequestTimeout(connectionManagerTimeout)
                        .setSocketTimeout(socketTimeout)
                        .setTargetPreferredAuthSchemes(Collections.singleton(AuthSchemes.DIGEST))
                        .setProxyPreferredAuthSchemes(Collections.singleton(AuthSchemes.DIGEST))
                        .build();

        staticClient =
                HttpClientBuilder.create().setConnectionManager(CONNECTION_MANAGER).setDefaultCredentialsProvider(CRED_PROV)
                        .setDefaultRequestConfig(staticRequestConfig).build();

    }

    public EmissaryClient() {
        this(staticClient, staticRequestConfig);
    }

    public EmissaryClient(RequestConfig requestConfig) {
        this(staticClient, requestConfig);
    }

    public EmissaryClient(CloseableHttpClient client) {
        this(client, staticRequestConfig);
    }

    public EmissaryClient(CloseableHttpClient client, RequestConfig requestConfig) {
        this.client = client;
        this.requestConfig = requestConfig;
    }

    public EmissaryResponse send(final HttpRequestBase method) {
        return send(method, null);
    }

    /**
     * Sends a request to the web server. The request can be any HttpMethod. Adds the specified cookie to the Http State
     *
     * @param method the method to be sent
     * @param cookie a cookie to set on the request
     */
    public EmissaryResponse send(final HttpRequestBase method, @Nullable final Cookie cookie) {
        LOGGER.debug("Sending {} to {}", method.getMethod(), method.getURI());
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
            BasicHttpResponse response = new BasicHttpResponse(method.getProtocolVersion(), HttpStatus.INTERNAL_SERVER_ERROR_500, e.getMessage());
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

    public void setConnectionTimeout(int timeout) {
        if (timeout > 0) {
            requestConfig = RequestConfig.copy(requestConfig).setConnectTimeout(timeout).build();
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

    public HttpRequestBase setCsrfHeader(HttpRequestBase request, String csrfToken) {
        return setCsrfHeader(request, CSRF_HEADER_PARAM, csrfToken);
    }

    public HttpRequestBase setCsrfHeader(HttpRequestBase request, String csrfParam, String csrfToken) {
        request.addHeader(csrfParam, csrfToken);
        return request;
    }

}
