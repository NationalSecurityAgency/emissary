package emissary.server.auth;

import com.google.common.collect.ImmutableList;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.security.ServerAuthException;
import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.security.UserPrincipal;
import org.eclipse.jetty.security.authentication.LoginAuthenticator;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.X509Certificate;

/**
 * PKI Authenticator for Jetty that authenticates requests based on client certificates.
 * <p>
 * This authenticator extracts the X.509 client certificate from the SSL/TLS session and validates it using the
 * configured {@link PkiLoginService}. Unlike traditional username/password authentication, PKI authentication occurs
 * automatically during the SSL handshake, making it transparent to the user.
 * </p>
 * <p>
 * The authenticator expects certificates to be available in the request attribute
 * "jakarta.servlet.request.X509Certificate" which is set by the Jetty SSL connector when client certificate
 * authentication is required.
 * </p>
 */
public class PkiAuthenticator extends LoginAuthenticator {

    private static final Logger LOG = LoggerFactory.getLogger(PkiAuthenticator.class);

    public static final String PKI_AUTH_METHOD = "CLIENT_CERT";

    @Override
    public String getAuthMethod() {
        return PKI_AUTH_METHOD;
    }

    @Override
    public Authentication validateRequest(ServletRequest servletRequest, ServletResponse servletResponse, boolean mandatory)
            throws ServerAuthException {

        if (!mandatory) {
            return Authentication.NOT_CHECKED;
        }

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Extract client certificates from the request
        X509Certificate[] certificates = (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");

        if (certificates == null || certificates.length == 0) {
            LOG.debug("No client certificate provided for request to {}", request.getRequestURI());
            return sendChallenge(response);
        }

        X509Certificate clientCertificate = certificates[0];
        LOG.debug("Client certificate found: Subject={}, Issuer={}",
                clientCertificate.getSubjectX500Principal().getName(),
                clientCertificate.getIssuerX500Principal().getName());

        // Use PkiLoginService to validate and load user
        if (_loginService instanceof PkiLoginService) {
            PkiLoginService pkiLoginService = (PkiLoginService) _loginService;
            Request baseRequest = Request.getBaseRequest(request);

            UserPrincipal userPrincipal = pkiLoginService.getUserPrincipalFromCertificate(baseRequest);

            if (userPrincipal != null) {
                // Load roles for the user
                ImmutableList<String> roles = pkiLoginService.getRoles(userPrincipal);

                if (roles != null && !roles.isEmpty()) {
                    LOG.debug("Successfully authenticated user: {} with roles: {}",
                            userPrincipal.getName(), String.join(", ", roles));

                    // Create UserAuthentication with the principal and roles
                    return new UserAuthentication(getAuthMethod(), pkiLoginService.login(userPrincipal.getName(), null, request));
                }
                LOG.warn("Certificate is valid but no roles assigned: {}", userPrincipal.getName());
            } else {
                LOG.warn("Certificate validation failed - certificate not authorized");
            }
        } else {
            LOG.error("PkiAuthenticator requires PkiLoginService, but got: {}",
                    _loginService != null ? _loginService.getClass().getName() : "null");
        }

        return sendChallenge(response);
    }

    /**
     * Send authentication challenge. For PKI authentication, we can't request a certificate after the SSL handshake, so we
     * send a 403 Forbidden response.
     *
     * @param response the HTTP response
     * @return Authentication.SEND_FAILURE
     */
    private static Authentication sendChallenge(HttpServletResponse response) {
        try {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Valid client certificate required for authentication");
        } catch (Exception e) {
            LOG.error("Error sending authentication challenge", e);
        }
        return Authentication.SEND_FAILURE;
    }

    @Override
    public boolean secureResponse(ServletRequest request, ServletResponse response, boolean mandatory, Authentication.User validatedUser) {
        // No additional response security needed for PKI authentication
        return true;
    }
}
