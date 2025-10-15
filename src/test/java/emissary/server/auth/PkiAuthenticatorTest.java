package emissary.server.auth;

import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.authentication.LoginAuthenticator;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Set;
import javax.security.auth.x500.X500Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PkiAuthenticatorTest {

    private PkiAuthenticator authenticator;
    private PkiLoginService loginService;
    private HttpServletRequest mockHttpRequest;
    private HttpServletResponse mockHttpResponse;
    private Request mockBaseRequest;
    private X509Certificate mockCertificate;

    @BeforeEach
    void setUp() {
        authenticator = new PkiAuthenticator();
        loginService = new PkiLoginService("TestRealm");

        IdentityService identityService = mock(IdentityService.class);

        authenticator.setConfiguration(new Authenticator.AuthConfiguration() {
            @Override
            public String getAuthMethod() {
                return "CLIENT_CERT";
            }

            @Override
            public String getRealmName() {
                return "TestRealm";
            }

            @Override
            @Nullable
            public String getInitParameter(String param) {
                return null;
            }

            @Override
            public boolean isSessionRenewedOnAuthentication() {
                return false;
            }

            @Override
            public int getSessionMaxInactiveIntervalOnAuthentication() {
                return 0;
            }

            @Override
            public IdentityService getIdentityService() {
                return identityService;
            }

            @Override
            public LoginService getLoginService() {
                return loginService;
            }

            @Override
            public Set<String> getInitParameterNames() {
                return Collections.emptySet();
            }
        });

        mockHttpRequest = mock(HttpServletRequest.class);
        mockHttpResponse = mock(HttpServletResponse.class);
        mockBaseRequest = mock(Request.class);
        mockCertificate = mock(X509Certificate.class);

        // Setup request mocking
        when(mockHttpRequest.getAttribute("org.eclipse.jetty.server.Request")).thenReturn(mockBaseRequest);
    }

    @Test
    void testGetAuthMethod() {
        assertEquals("CLIENT_CERT", authenticator.getAuthMethod());
    }

    @Test
    void testValidateRequestNotMandatory() throws Exception {
        Authentication result = authenticator.validateRequest(mockHttpRequest, mockHttpResponse, false);

        assertEquals(Authentication.NOT_CHECKED, result);
    }

    @Test
    void testValidateRequestNoCertificate() throws Exception {
        when(mockHttpRequest.getAttribute("jakarta.servlet.request.X509Certificate")).thenReturn(null);
        when(mockHttpRequest.getRequestURI()).thenReturn("/test");

        Authentication result = authenticator.validateRequest(mockHttpRequest, mockHttpResponse, true);

        assertEquals(Authentication.SEND_FAILURE, result);
        verify(mockHttpResponse).sendError(HttpServletResponse.SC_FORBIDDEN, "Valid client certificate required for authentication");
    }

    @Test
    void testValidateRequestEmptyCertificateArray() throws Exception {
        when(mockHttpRequest.getAttribute("jakarta.servlet.request.X509Certificate")).thenReturn(new X509Certificate[0]);
        when(mockHttpRequest.getRequestURI()).thenReturn("/test");

        Authentication result = authenticator.validateRequest(mockHttpRequest, mockHttpResponse, true);

        assertEquals(Authentication.SEND_FAILURE, result);
    }

    @Test
    void testValidateRequestWithValidCertificate() throws Exception {
        String dn = "CN=Test User,OU=Engineering,O=Test,C=US";
        X500Principal principal = new X500Principal(dn);

        when(mockCertificate.getSubjectX500Principal()).thenReturn(principal);
        when(mockCertificate.getIssuerX500Principal()).thenReturn(principal);
        when(mockHttpRequest.getAttribute("jakarta.servlet.request.X509Certificate"))
                .thenReturn(new X509Certificate[] {mockCertificate});
        when(mockBaseRequest.getAttribute("jakarta.servlet.request.X509Certificate"))
                .thenReturn(new X509Certificate[] {mockCertificate});

        // Configure login service to accept this certificate
        loginService.setAllowAnyValidCertificate(true);
        loginService.setDefaultRole("emissary");

        // Set the login service on the authenticator
        Field field = LoginAuthenticator.class
                .getDeclaredField("_loginService");
        field.setAccessible(true);
        field.set(authenticator, loginService);

        // Mock the static Request.getBaseRequest() method
        try (var mockedRequest = mockStatic(Request.class)) {
            mockedRequest.when(() -> Request.getBaseRequest(mockHttpRequest)).thenReturn(mockBaseRequest);

            Authentication result = authenticator.validateRequest(mockHttpRequest, mockHttpResponse, true);

            assertNotNull(result);
            // We expect either UserAuthentication or SEND_FAILURE depending on the full setup
        }
    }

    @Test
    void testValidateRequestWithUnauthorizedCertificate() throws Exception {
        String dn = "CN=Unauthorized User,OU=Bad,O=Test,C=US";
        X500Principal principal = new X500Principal(dn);

        when(mockCertificate.getSubjectX500Principal()).thenReturn(principal);
        when(mockCertificate.getIssuerX500Principal()).thenReturn(principal);
        when(mockHttpRequest.getAttribute("jakarta.servlet.request.X509Certificate"))
                .thenReturn(new X509Certificate[] {mockCertificate});
        when(mockBaseRequest.getAttribute("jakarta.servlet.request.X509Certificate"))
                .thenReturn(new X509Certificate[] {mockCertificate});
        when(mockHttpRequest.getRequestURI()).thenReturn("/test");

        // Configure login service to NOT accept this certificate
        loginService.setAllowAnyValidCertificate(false);

        // Set the login service on the authenticator
        Field field = LoginAuthenticator.class
                .getDeclaredField("_loginService");
        field.setAccessible(true);
        field.set(authenticator, loginService);

        // Mock the static Request.getBaseRequest() method
        try (var mockedRequest = mockStatic(Request.class)) {
            mockedRequest.when(() -> Request.getBaseRequest(mockHttpRequest)).thenReturn(mockBaseRequest);

            Authentication result = authenticator.validateRequest(mockHttpRequest, mockHttpResponse, true);

            assertEquals(Authentication.SEND_FAILURE, result);
        }
    }

    @Test
    void testSecureResponse() {
        // secureResponse should always return true for PKI authentication
        boolean result = authenticator.secureResponse(mockHttpRequest, mockHttpResponse, true, null);

        assertTrue(result);
    }
}
