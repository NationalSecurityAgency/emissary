package emissary.server.auth;

import emissary.config.Configurator;

import com.google.common.collect.ImmutableList;
import org.eclipse.jetty.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PkiAuthenticationConfigTest {

    @TempDir
    Path tempDir;

    private Configurator mockConfigurator;

    @BeforeEach
    void setUp() {
        mockConfigurator = mock(Configurator.class);

        // Set up default return values - return the default parameter value
        when(mockConfigurator.findBooleanEntry(anyString(), anyBoolean())).thenAnswer(invocation -> invocation.getArgument(1));
        when(mockConfigurator.findStringEntry(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(mockConfigurator.findEntries(anyString())).thenReturn(new ArrayList<>());
    }

    @Test
    void testDefaultConfiguration() {
        PkiAuthenticationConfig config = new PkiAuthenticationConfig(mockConfigurator);

        assertFalse(config.isEnabled());
        assertFalse(config.isClientCertRequired());
        assertFalse(config.isAllowAnyValidCert());
        assertEquals("emissary", config.getDefaultRole());
        assertNotNull(config.getTrustedDnPatterns());
        assertTrue(config.getTrustedDnPatterns().isEmpty());
    }

    @Test
    void testEnabledConfiguration() {
        when(mockConfigurator.findBooleanEntry("PKI_AUTHENTICATION_ENABLED", false)).thenReturn(true);
        when(mockConfigurator.findBooleanEntry("PKI_CLIENT_CERT_REQUIRED", false)).thenReturn(true);
        when(mockConfigurator.findBooleanEntry("PKI_ALLOW_ANY_VALID_CERT", false)).thenReturn(true);
        when(mockConfigurator.findStringEntry("PKI_DEFAULT_ROLE", "emissary")).thenReturn("admin");

        PkiAuthenticationConfig config = new PkiAuthenticationConfig(mockConfigurator);

        assertTrue(config.isEnabled());
        assertTrue(config.isClientCertRequired());
        assertTrue(config.isAllowAnyValidCert());
        assertEquals("admin", config.getDefaultRole());
    }

    @Test
    void testTrustedDnPatterns() {
        when(mockConfigurator.findEntries("PKI_TRUSTED_DN_PATTERN")).thenReturn(
                Arrays.asList("^CN=.*,OU=Engineering,O=Test,C=US$", "^CN=.*,OU=Operations,O=Test,C=US$"));

        PkiAuthenticationConfig config = new PkiAuthenticationConfig(mockConfigurator);

        List<String> patterns = config.getTrustedDnPatterns();
        assertEquals(2, patterns.size());
        assertTrue(patterns.contains("^CN=.*,OU=Engineering,O=Test,C=US$"));
        assertTrue(patterns.contains("^CN=.*,OU=Operations,O=Test,C=US$"));
    }

    @Test
    void testConfigurePkiLoginService() throws IOException {
        when(mockConfigurator.findBooleanEntry("PKI_ALLOW_ANY_VALID_CERT", false)).thenReturn(true);
        when(mockConfigurator.findStringEntry("PKI_DEFAULT_ROLE", "emissary")).thenReturn("testRole");
        when(mockConfigurator.findEntries("PKI_TRUSTED_DN_PATTERN")).thenReturn(
                Arrays.asList("^CN=.*,OU=Test,O=Test,C=US$"));

        PkiAuthenticationConfig config = new PkiAuthenticationConfig(mockConfigurator);
        PkiLoginService loginService = new PkiLoginService("TestRealm");

        config.configure(loginService);

        // Verify that the login service was configured
        // We can test this by checking if a certificate matching the pattern gets the default role
        String testDn = "CN=Test User,OU=Test,O=Test,C=US";
        ImmutableList<String> roles = loginService.getRoles(new UserPrincipal(testDn, null));

        assertNotNull(roles);
        assertEquals(1, roles.size());
        assertEquals("testRole", roles.get(0));
    }

    @Test
    void testLoadUsersFromFile() throws IOException {
        // Create a temporary PKI users file
        Path usersFile = tempDir.resolve("pki-users.properties");
        String fileContent = "# Test PKI users file\n" +
                "CN=John Doe,OU=Engineering,O=Test,C=US = emissary,admin\n" +
                "CN=Jane Smith,OU=Operations,O=Test,C=US = emissary,support\n" +
                "\n" +
                "# Comment line\n" +
                "CN=Bob Manager,OU=Management,O=Test,C=US = manager,admin\n";
        Files.writeString(usersFile, fileContent);

        when(mockConfigurator.findStringEntry(eq("PKI_USERS_FILE"), isNull())).thenReturn(usersFile.toString());

        PkiAuthenticationConfig config = new PkiAuthenticationConfig(mockConfigurator);
        PkiLoginService loginService = new PkiLoginService("TestRealm");

        config.configure(loginService);

        // Verify that users were loaded
        ImmutableList<String> johnRoles = loginService.getRoles(
                new UserPrincipal("CN=John Doe,OU=Engineering,O=Test,C=US", null));
        assertNotNull(johnRoles);
        assertEquals(2, johnRoles.size());

        ImmutableList<String> janeRoles = loginService.getRoles(
                new UserPrincipal("CN=Jane Smith,OU=Operations,O=Test,C=US", null));
        assertNotNull(janeRoles);
        assertEquals(2, janeRoles.size());

        ImmutableList<String> bobRoles = loginService.getRoles(
                new UserPrincipal("CN=Bob Manager,OU=Management,O=Test,C=US", null));
        assertNotNull(bobRoles);
        assertEquals(2, bobRoles.size());
    }

    @Test
    void testLoadUsersFromFileWithInvalidLines() throws IOException {
        // Create a temporary PKI users file with some invalid lines
        Path usersFile = tempDir.resolve("pki-users-invalid.properties");
        String fileContent = "# Valid line\n" +
                "CN=Valid User,OU=Test,O=Test,C=US = emissary\n" +
                "\n" +
                "# Invalid lines (missing equals, empty values)\n" +
                "CN=No Roles,OU=Test,O=Test,C=US =\n" +
                "Invalid line without equals\n" +
                "= emissary\n" +
                "\n" +
                "# Another valid line\n" +
                "CN=Another User,OU=Test,O=Test,C=US = admin\n";
        Files.writeString(usersFile, fileContent);

        when(mockConfigurator.findStringEntry(eq("PKI_USERS_FILE"), isNull())).thenReturn(usersFile.toString());

        PkiAuthenticationConfig config = new PkiAuthenticationConfig(mockConfigurator);
        PkiLoginService loginService = new PkiLoginService("TestRealm");

        // Should not throw exception, just skip invalid lines
        config.configure(loginService);

        // Verify that valid users were still loaded
        ImmutableList<String> validUserRoles = loginService.getRoles(
                new UserPrincipal("CN=Valid User,OU=Test,O=Test,C=US", null));
        assertNotNull(validUserRoles);
        assertEquals(1, validUserRoles.size());
        assertEquals("emissary", validUserRoles.get(0));
    }

}
