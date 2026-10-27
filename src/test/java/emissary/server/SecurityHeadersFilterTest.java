package emissary.server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityHeadersFilterTest {

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final FilterChain chain = mock(FilterChain.class);

    @Test
    void addsHardeningHeadersAndLeavesStaticResponsesCacheable() throws Exception {
        final SecurityHeadersFilter filter = new SecurityHeadersFilter();
        filter.init(mock(FilterConfig.class));

        filter.doFilter(this.request, this.response, this.chain);

        verify(this.response).setHeader(SecurityHeadersFilter.X_CONTENT_TYPE_OPTIONS, "nosniff");
        verify(this.response).setHeader(SecurityHeadersFilter.X_FRAME_OPTIONS, "DENY");
        verify(this.response).setHeader(SecurityHeadersFilter.CONTENT_SECURITY_POLICY, SecurityHeadersFilter.CSP_VALUE);
        verify(this.response).setHeader(SecurityHeadersFilter.REFERRER_POLICY, "no-referrer");
        verify(this.response, never()).setHeader(SecurityHeadersFilter.CACHE_CONTROL, "no-store");
        verify(this.response, never()).setHeader(SecurityHeadersFilter.PRAGMA, "no-cache");
        verify(this.chain).doFilter(this.request, this.response);
    }

    @Test
    void disablesCachingWhenConfigured() throws Exception {
        final FilterConfig config = mock(FilterConfig.class);
        when(config.getInitParameter("noStore")).thenReturn("true");
        final SecurityHeadersFilter filter = new SecurityHeadersFilter();
        filter.init(config);

        filter.doFilter(this.request, this.response, this.chain);

        verify(this.response).setHeader(SecurityHeadersFilter.CACHE_CONTROL, "no-store");
        verify(this.response).setHeader(SecurityHeadersFilter.PRAGMA, "no-cache");
        verify(this.chain).doFilter(this.request, this.response);
    }
}
