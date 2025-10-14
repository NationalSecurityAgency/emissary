package emissary.server.auth;

import com.google.common.collect.ImmutableList;
import org.eclipse.jetty.security.UserPrincipal;
import org.eclipse.jetty.server.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PkiLoginServiceTest {

    private PkiLoginService loginService;
    private X509Certificate mockCertificate;
    private Request mockRequest;

    @BeforeEach
    void setUp() {
        loginService = new PkiLoginService("TestRealm");
        mockCertificate = mock(X509Certificate.class);
        mockRequest = mock(Request.class);
    }

    @Test
    void testAddCertificateMapping() {
        String dn = "CN=John Doe,OU=Engineering,O=Emissary,C=US";
        loginService.addCertificateMapping(dn, "emissary", "admin");

        UserPrincipal user = new UserPrincipal(dn, null);
        ImmutableList<String> roles = loginService.getRoles(user);

        assertNotNull(roles);
        assertEquals(2, roles.size());
        // Check that both roles are present
        assertTrue(roles.contains("emissary"));
        assertTrue(roles.contains("admin"));
    }

    @Test
    void testTrustedDnPattern() {
        String pattern = "^CN=.*,OU=Engineering,O=Emissary,C=US$";
        loginService.addTrustedDnPattern(pattern);
        loginService.setDefaultRole("engineer");

        String matchingDn = "CN=Jane Smith,OU=Engineering,O=Emissary,C=US";
        UserPrincipal user = new UserPrincipal(matchingDn, null);
        ImmutableList<String> roles = loginService.getRoles(user);

        assertNotNull(roles);
        assertEquals(1, roles.size());
        assertEquals("engineer", roles.get(0));
    }

    @Test
    void testTrustedDnPatternNoMatch() {
        String pattern = "^CN=.*,OU=Engineering,O=Emissary,C=US$";
        loginService.addTrustedDnPattern(pattern);
        loginService.setDefaultRole("engineer");

        String nonMatchingDn = "CN=Bob Jones,OU=Operations,O=Emissary,C=US";
        UserPrincipal user = new UserPrincipal(nonMatchingDn, null);
        ImmutableList<String> roles = loginService.getRoles(user);

        assertNotNull(roles);
        assertEquals(0, roles.size()); // No roles because DN doesn't match pattern
    }

    @Test
    void testAllowAnyValidCertificate() {
        loginService.setAllowAnyValidCertificate(true);
        loginService.setDefaultRole("emissary");

        String anyDn = "CN=Any User,OU=Any Dept,O=Any Org,C=US";
        UserPrincipal user = new UserPrincipal(anyDn, null);
        ImmutableList<String> roles = loginService.getRoles(user);

        assertNotNull(roles);
        assertEquals(1, roles.size());
        assertEquals("emissary", roles.get(0));
    }

    @Test
    void testGetUserPrincipalFromCertificate() {
        String dn = "CN=Test User,OU=Test,O=TestOrg,C=US";
        X500Principal principal = new X500Principal(dn);

        when(mockCertificate.getSubjectX500Principal()).thenReturn(principal);
        when(mockRequest.getAttribute("jakarta.servlet.request.X509Certificate"))
                .thenReturn(new X509Certificate[] {mockCertificate});

        loginService.setAllowAnyValidCertificate(true);

        UserPrincipal userPrincipal = loginService.getUserPrincipalFromCertificate(mockRequest);

        assertNotNull(userPrincipal);
        assertEquals(dn, userPrincipal.getName());
    }

    @Test
    void testGetUserPrincipalFromCertificateNoCertificate() {
        when(mockRequest.getAttribute("jakarta.servlet.request.X509Certificate"))
                .thenReturn(null);

        UserPrincipal userPrincipal = loginService.getUserPrincipalFromCertificate(mockRequest);

        assertNull(userPrincipal);
    }

    @Test
    void testGetUserPrincipalFromCertificateEmptyArray() {
        when(mockRequest.getAttribute("jakarta.servlet.request.X509Certificate"))
                .thenReturn(new X509Certificate[0]);

        UserPrincipal userPrincipal = loginService.getUserPrincipalFromCertificate(mockRequest);

        assertNull(userPrincipal);
    }

    @Test
    void testGetUserPrincipalFromCertificateWithExplicitMapping() {
        String dn = "CN=Mapped User,OU=Test,O=TestOrg,C=US";
        X500Principal principal = new X500Principal(dn);

        loginService.addCertificateMapping(dn, "admin", "support");

        when(mockCertificate.getSubjectX500Principal()).thenReturn(principal);
        when(mockRequest.getAttribute("jakarta.servlet.request.X509Certificate"))
                .thenReturn(new X509Certificate[] {mockCertificate});

        UserPrincipal userPrincipal = loginService.getUserPrincipalFromCertificate(mockRequest);

        assertNotNull(userPrincipal);
        assertEquals(dn, userPrincipal.getName());
    }

    @Test
    void testGetUserPrincipalFromCertificateNotAuthorized() {
        String dn = "CN=Unauthorized User,OU=Test,O=TestOrg,C=US";
        X500Principal principal = new X500Principal(dn);

        // Don't set allowAnyValidCertificate, don't add mappings or patterns
        loginService.setAllowAnyValidCertificate(false);

        when(mockCertificate.getSubjectX500Principal()).thenReturn(principal);
        when(mockRequest.getAttribute("jakarta.servlet.request.X509Certificate"))
                .thenReturn(new X509Certificate[] {mockCertificate});

        UserPrincipal userPrincipal = loginService.getUserPrincipalFromCertificate(mockRequest);

        assertNull(userPrincipal); // Should be null because not authorized
    }

    @Test
    void testCertificateCredential() {
        PkiLoginService.CertificateCredential credential =
                new PkiLoginService.CertificateCredential(mockCertificate);

        assertEquals(mockCertificate, credential.getCertificate());
        assertTrue(credential.check(mockCertificate), "Credential should validate with certificate");
        assertFalse(credential.check("not a certificate"), "Credential should not validate with non-certificate");
    }

    @Test
    void testDistinguishedNameNormalization() {
        // Test that DNs with different whitespace are treated the same
        String dn1 = "CN=User,OU=Test,O=Org,C=US";
        String dn2 = "CN=User, OU=Test, O=Org, C=US"; // extra spaces
        String dn3 = "CN = User , OU = Test , O = Org , C = US"; // spaces around equals

        loginService.addCertificateMapping(dn1, "test");

        UserPrincipal user2 = new UserPrincipal(dn2, null);
        UserPrincipal user3 = new UserPrincipal(dn3, null);

        // All should map to the same role because of normalization
        ImmutableList<String> roles2 = loginService.getRoles(user2);
        ImmutableList<String> roles3 = loginService.getRoles(user3);

        assertNotNull(roles2);
        assertNotNull(roles3);
        assertEquals(1, roles2.size());
        assertEquals(1, roles3.size());
        assertEquals("test", roles2.get(0));
        assertEquals("test", roles3.get(0));
    }
}
