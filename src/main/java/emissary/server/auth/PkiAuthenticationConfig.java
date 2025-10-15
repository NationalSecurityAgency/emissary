package emissary.server.auth;

import emissary.config.Configurator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration helper for PKI authentication settings.
 * <p>
 * This class loads PKI authentication configuration from Emissary configuration files and applies it to a
 * {@link PkiLoginService}.
 * </p>
 * <p>
 * Configuration options:
 * 
 * <pre>
 * # Enable PKI authentication (default: false)
 * PKI_AUTHENTICATION_ENABLED = "true"
 *
 * # Require client certificates for SSL connections (default: false)
 * PKI_CLIENT_CERT_REQUIRED = "true"
 *
 * # Allow any valid certificate that passes SSL validation (default: false)
 * PKI_ALLOW_ANY_VALID_CERT = "false"
 *
 * # Default role for authenticated PKI users (default: "emissary")
 * PKI_DEFAULT_ROLE = "emissary"
 *
 * # Path to PKI users mapping file (optional)
 * PKI_USERS_FILE = "/path/to/pki-users.properties"
 *
 * # Trusted certificate DN patterns (regex) - users matching these patterns are authenticated
 * PKI_TRUSTED_DN_PATTERN = "^CN=.*,OU=Engineering,O=Emissary,C=US$"
 * PKI_TRUSTED_DN_PATTERN = "^CN=.*,OU=Operations,O=Emissary,C=US$"
 * </pre>
 * <p>
 * PKI users file format:
 * 
 * <pre>
 * # Format: certificate_dn = role1,role2,role3
 * CN=John Doe,OU=Engineering,O=Emissary,C=US = emissary,admin
 * CN=Jane Smith,OU=Operations,O=Emissary,C=US = emissary,support
 * </pre>
 */
public class PkiAuthenticationConfig {

    private static final Logger LOG = LoggerFactory.getLogger(PkiAuthenticationConfig.class);

    private static final String CFG_PKI_ENABLED = "PKI_AUTHENTICATION_ENABLED";
    private static final String CFG_PKI_CLIENT_CERT_REQUIRED = "PKI_CLIENT_CERT_REQUIRED";
    private static final String CFG_PKI_ALLOW_ANY_VALID = "PKI_ALLOW_ANY_VALID_CERT";
    private static final String CFG_PKI_DEFAULT_ROLE = "PKI_DEFAULT_ROLE";
    private static final String CFG_PKI_USERS_FILE = "PKI_USERS_FILE";
    private static final String CFG_PKI_TRUSTED_DN_PATTERN = "PKI_TRUSTED_DN_PATTERN";

    private final boolean enabled;
    private final boolean clientCertRequired;
    private final boolean allowAnyValidCert;
    private final String defaultRole;
    private final String usersFile;
    private final List<String> trustedDnPatterns;

    /**
     * Load PKI authentication configuration.
     *
     * @param configurator the configuration source
     */
    public PkiAuthenticationConfig(Configurator configurator) {
        this.enabled = configurator.findBooleanEntry(CFG_PKI_ENABLED, false);
        this.clientCertRequired = configurator.findBooleanEntry(CFG_PKI_CLIENT_CERT_REQUIRED, false);
        this.allowAnyValidCert = configurator.findBooleanEntry(CFG_PKI_ALLOW_ANY_VALID, false);
        this.defaultRole = configurator.findStringEntry(CFG_PKI_DEFAULT_ROLE, "emissary");
        this.usersFile = configurator.findStringEntry(CFG_PKI_USERS_FILE, null);
        this.trustedDnPatterns = configurator.findEntries(CFG_PKI_TRUSTED_DN_PATTERN);

        LOG.info("PKI Authentication Config - Enabled: {}, ClientCertRequired: {}, AllowAnyValid: {}",
                enabled, clientCertRequired, allowAnyValidCert);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isClientCertRequired() {
        return clientCertRequired;
    }

    public boolean isAllowAnyValidCert() {
        return allowAnyValidCert;
    }

    public String getDefaultRole() {
        return defaultRole;
    }

    public String getUsersFile() {
        return usersFile;
    }

    public List<String> getTrustedDnPatterns() {
        return trustedDnPatterns;
    }

    /**
     * Configure a PkiLoginService based on this configuration.
     *
     * @param loginService the service to configure
     * @throws IOException if there's an error reading the users file
     */
    public void configure(PkiLoginService loginService) throws IOException {
        loginService.setAllowAnyValidCertificate(allowAnyValidCert);
        loginService.setDefaultRole(defaultRole);

        // Add trusted DN patterns
        for (String pattern : trustedDnPatterns) {
            loginService.addTrustedDnPattern(pattern);
        }

        // Load users from file if specified
        if (usersFile != null && !usersFile.isEmpty()) {
            loadUsersFromFile(loginService, usersFile);
        }
    }

    /**
     * Load PKI user mappings from a properties file. Format: certificate_dn = role1,role2,role3
     *
     * @param loginService the service to configure
     * @param filePath path to the users file
     * @throws IOException if there's an error reading the file
     */
    private static void loadUsersFromFile(PkiLoginService loginService, String filePath) throws IOException {
        LOG.info("Loading PKI users from file: {}", filePath);
        int count = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Parse line: dn = role1,role2,role3
                // DNs contain '=' signs (e.g., CN=John Doe), so we need to find the separator '='
                // that comes after the DN. We look for " = " pattern (space-equals-space).
                int equalsIndex = line.indexOf(" = ");
                if (equalsIndex <= 0) {
                    LOG.warn("Invalid format in PKI users file at line {}: {}", lineNumber, line);
                    continue;
                }

                String dn = line.substring(0, equalsIndex).trim();
                String rolesStr = line.substring(equalsIndex + 3).trim(); // +3 to skip " = "

                if (dn.isEmpty() || rolesStr.isEmpty()) {
                    LOG.warn("Empty DN or roles in PKI users file at line {}: {}", lineNumber, line);
                    continue;
                }

                String[] roles = Arrays.stream(rolesStr.split(","))
                        .map(String::trim)
                        .filter(r -> !r.isEmpty())
                        .toArray(String[]::new);

                if (roles.length > 0) {
                    loginService.addCertificateMapping(dn, roles);
                    count++;
                    LOG.debug("Loaded PKI mapping: {} -> {}", dn, String.join(", ", roles));
                }
            }
        }

        LOG.info("Loaded {} PKI user mappings from {}", count, filePath);
    }
}
