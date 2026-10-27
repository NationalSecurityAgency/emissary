package emissary.server;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Adds browser hardening headers to server responses.
 *
 * <p>
 * The CSP intentionally limits only framing, plugins, base URIs, and form targets so existing inline console scripts
 * keep working. Dynamic API and console responses are marked no-store; static assets remain cacheable.
 */
public class SecurityHeadersFilter implements Filter {

    public static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    public static final String X_FRAME_OPTIONS = "X-Frame-Options";
    public static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    public static final String REFERRER_POLICY = "Referrer-Policy";
    public static final String CACHE_CONTROL = "Cache-Control";
    public static final String PRAGMA = "Pragma";

    public static final String CSP_VALUE = "frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'";

    private boolean noStore;

    @Override
    public void init(final FilterConfig filterConfig) {
        this.noStore = Boolean.parseBoolean(filterConfig.getInitParameter("noStore"));
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse) {
            final HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setHeader(X_CONTENT_TYPE_OPTIONS, "nosniff");
            httpResponse.setHeader(X_FRAME_OPTIONS, "DENY");
            httpResponse.setHeader(CONTENT_SECURITY_POLICY, CSP_VALUE);
            httpResponse.setHeader(REFERRER_POLICY, "no-referrer");
            if (this.noStore) {
                httpResponse.setHeader(CACHE_CONTROL, "no-store");
                httpResponse.setHeader(PRAGMA, "no-cache");
            }
        }
        chain.doFilter(request, response);
    }
}
