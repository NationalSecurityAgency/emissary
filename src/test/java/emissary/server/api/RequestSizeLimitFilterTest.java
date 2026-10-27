package emissary.server.api;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter();
    private final ContainerRequestContext request = mock(ContainerRequestContext.class);
    private final UriInfo uriInfo = mock(UriInfo.class);

    @BeforeEach
    void setUp() {
        when(this.request.getMethod()).thenReturn("POST");
        when(this.request.getUriInfo()).thenReturn(this.uriInfo);
        when(this.uriInfo.getPath()).thenReturn("output/Place/sinks/JSON/trace");
    }

    @Test
    void rejectsDeclaredOversizedBodyWithoutReadingIt() throws Exception {
        when(this.request.getLength()).thenReturn((int) RequestSizeLimitFilter.MAX_REQUEST_BYTES + 1);

        this.filter.filter(this.request);

        verify(this.request).abortWith(any(Response.class));
        verify(this.request, never()).getEntityStream();
    }

    @Test
    void rejectsOversizedChunkedBody() throws Exception {
        when(this.request.getLength()).thenReturn(-1);
        when(this.request.getEntityStream())
                .thenReturn(new ByteArrayInputStream(new byte[(int) RequestSizeLimitFilter.MAX_REQUEST_BYTES + 1]));

        this.filter.filter(this.request);

        final ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(this.request).abortWith(response.capture());
        assertEquals(413, response.getValue().getStatus());
        verify(this.request, never()).setEntityStream(any());
    }

    @Test
    void replacesTheStreamWithTheBoundedBody() throws Exception {
        final byte[] body = "{}".getBytes(UTF_8);
        when(this.request.getLength()).thenReturn(body.length);
        when(this.request.getEntityStream()).thenReturn(new ByteArrayInputStream(body));

        this.filter.filter(this.request);

        final ArgumentCaptor<InputStream> stream = ArgumentCaptor.forClass(InputStream.class);
        verify(this.request).setEntityStream(stream.capture());
        assertArrayEquals(body, stream.getValue().readAllBytes());
        verify(this.request, never()).abortWith(any(Response.class));
    }

    @Test
    void ignoresUnrelatedPaths() throws Exception {
        when(this.uriInfo.getPath()).thenReturn("configuration/emissary.server.api.Configs");
        when(this.request.getLength()).thenReturn((int) RequestSizeLimitFilter.MAX_REQUEST_BYTES + 1);

        this.filter.filter(this.request);

        verify(this.request, never()).abortWith(any(Response.class));
        verify(this.request, never()).getEntityStream();
    }
}
