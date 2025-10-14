package emissary.server.auth;

import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nullable;
import org.eclipse.jetty.security.AbstractLoginService;
import org.eclipse.jetty.security.RolePrincipal;
import org.eclipse.jetty.security.UserPrincipal;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.security.Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.security.auth.x500.X500Principal;

/**
 * PKI-based Login Service for Jetty that authenticates users based on client certificates (X.509).
 * <p>
 * This login service extracts the client certificate from the SSL/TLS session and validates it against configured
 * certificate Distinguished Names (DNs) and Subject Alternative Names (SANs). Users can be mapped to roles based on
 * their certificate properties.
 * </p>
 * <p>
 * Configuration can be done programmatically or via a properties file with the format:
 * 
 * <pre>
 * # Certificate DN to roles mapping
 * # Format: CN=username,OU=org,O=company,C=US = role1,role2,role3
 * CN=John Doe,OU=Engineering,O=Emissary,C=US = emissary,admin
 * CN=Jane Smith,OU=Operations,O=Emissary,C=US = emissary,support
 * </pre>
 */
public class PkiLoginService extends AbstractLoginService {

    private static final Logger LOG = LoggerFactory.getLogger(PkiLoginService.class);

    private final Map<String, Set<String>> certificateDnToRoles = new HashMap<>();
    private final List<Pattern> trustedDnPatterns = new ArrayList<>();
    private boolean allowAnyValidCertificate = false;
    private String defaultRole = "emissary";

    public PkiLoginService() {
        super();
    }

    public PkiLoginService(String name) {
        super();
        setName(name);
    }

    /**
     * Add a certificate DN to role mapping.
     *
     * @param distinguishedName the certificate DN (e.g., "CN=John Doe,OU=Engineering,O=Company,C=US")
     * @param roles the roles to assign to this certificate
     */
    public void addCertificateMapping(String distinguishedName, String... roles) {
        Set<String> roleSet = certificateDnToRoles.computeIfAbsent(normalizeDistinguishedName(distinguishedName), k -> new HashSet<>());
        Collections.addAll(roleSet, roles);
        LOG.debug("Added certificate mapping: {} -> {}", distinguishedName, roles);
    }

    /**
     * Add a trusted certificate DN pattern. Any certificate matching this pattern will be accepted.
     *
     * @param dnPattern regex pattern for matching certificate DNs
     */
    public void addTrustedDnPattern(String dnPattern) {
        trustedDnPatterns.add(Pattern.compile(dnPattern));
        LOG.debug("Added trusted DN pattern: {}", dnPattern);
    }

    /**
     * Set whether to allow any valid certificate that passes SSL validation. When true, any certificate trusted by the SSL
     * context will be authenticated.
     *
     * @param allow true to allow any valid certificate
     */
    public void setAllowAnyValidCertificate(boolean allow) {
        this.allowAnyValidCertificate = allow;
    }

    /**
     * Set the default role assigned to authenticated users when no specific mapping exists.
     *
     * @param role the default role
     */
    public void setDefaultRole(String role) {
        this.defaultRole = role;
    }

    @Override
    protected List<RolePrincipal> loadRoleInfo(UserPrincipal user) {
        String normalizedDn = normalizeDistinguishedName(user.getName());
        Set<String> roles = certificateDnToRoles.get(normalizedDn);

        if (roles != null && !roles.isEmpty()) {
            List<RolePrincipal> rolePrincipals = new ArrayList<>(roles.size());
            for (String role : roles) {
                rolePrincipals.add(new RolePrincipal(role));
            }
            return Collections.unmodifiableList(rolePrincipals);
        }

        // Check if DN matches any trusted patterns
        for (Pattern pattern : trustedDnPatterns) {
            if (pattern.matcher(normalizedDn).matches()) {
                return Collections.singletonList(new RolePrincipal(defaultRole));
            }
        }

        // If allowing any valid certificate, assign default role
        if (allowAnyValidCertificate) {
            return Collections.singletonList(new RolePrincipal(defaultRole));
        }

        return Collections.emptyList();
    }

    @Override
    protected UserPrincipal loadUserInfo(String username) {
        // PKI authentication doesn't use username/password lookups
        // Authentication is handled via certificate validation
        return new UserPrincipal(username, null);
    }

    /**
     * Helper method to get roles as list (for compatibility with tests and other code).
     *
     * @param user the user principal
     * @return immutable list of role names
     */
    public ImmutableList<String> getRoles(UserPrincipal user) {
        List<RolePrincipal> rolePrincipals = loadRoleInfo(user);
        return ImmutableList.copyOf(
                rolePrincipals.stream()
                        .map(RolePrincipal::getName)
                        .collect(Collectors.toList()));
    }

    /**
     * Extract the client certificate from the request and create a UserPrincipal. This method is called by the Jetty
     * authenticator.
     *
     * @param request the HTTP request
     * @return UserPrincipal if valid certificate found, null otherwise
     */
    @Nullable
    public UserPrincipal getUserPrincipalFromCertificate(Request request) {
        X509Certificate[] certificates = (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");

        if (certificates == null || certificates.length == 0) {
            LOG.debug("No client certificates found in request");
            return null;
        }

        X509Certificate clientCert = certificates[0];
        X500Principal principal = clientCert.getSubjectX500Principal();
        String dn = principal.getName();

        LOG.debug("Client certificate DN: {}", dn);

        // Check if this certificate is explicitly mapped
        String normalizedDn = normalizeDistinguishedName(dn);
        if (certificateDnToRoles.containsKey(normalizedDn)) {
            LOG.debug("Certificate DN found in mappings");
            return new UserPrincipal(dn, new CertificateCredential(clientCert));
        }

        // Check if DN matches any trusted patterns
        for (Pattern pattern : trustedDnPatterns) {
            if (pattern.matcher(normalizedDn).matches()) {
                LOG.debug("Certificate DN matches trusted pattern: {}", pattern.pattern());
                return new UserPrincipal(dn, new CertificateCredential(clientCert));
            }
        }

        // Check if allowing any valid certificate
        if (allowAnyValidCertificate) {
            LOG.debug("Accepting valid certificate (allowAnyValidCertificate=true)");
            return new UserPrincipal(dn, new CertificateCredential(clientCert));
        }

        LOG.warn("Client certificate not authorized: {}", dn);
        return null;
    }

    /**
     * Normalize a Distinguished Name for consistent comparison. Handles variations in whitespace and ordering.
     *
     * @param dn the DN to normalize
     * @return normalized DN
     */
    private static String normalizeDistinguishedName(String dn) {
        if (dn == null) {
            return "";
        }
        // Remove extra whitespace around commas and equals signs
        return dn.replaceAll("\\s*,\\s*", ",").replaceAll("\\s*=\\s*", "=").trim();
    }

    /**
     * Custom credential class for certificate-based authentication.
     */
    public static class CertificateCredential extends Credential {
        private static final long serialVersionUID = 1L;
        private final X509Certificate certificate;

        public CertificateCredential(X509Certificate certificate) {
            this.certificate = certificate;
        }

        public X509Certificate getCertificate() {
            return certificate;
        }

        @Override
        public boolean check(Object credentials) {
            // Certificate validation is done during SSL handshake
            // If we have a certificate here, it's already been validated
            return credentials instanceof X509Certificate;
        }
    }
}
