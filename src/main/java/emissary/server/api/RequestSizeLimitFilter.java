package emissary.server.api;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Rejects oversized trace requests before their bodies are deserialized.
 *
 * <p>
 * A declared length is rejected immediately. Requests without a declared length, such as chunked requests, are read
 * into a bounded buffer so that the limit cannot be bypassed. The stream is then replaced for downstream readers.
 */
@Provider
@PreMatching
@Priority(Priorities.ENTITY_CODER)
public class RequestSizeLimitFilter implements ContainerRequestFilter {

    public static final long MAX_REQUEST_BYTES = 1_048_576L;

    @Override
    public void filter(final ContainerRequestContext request) throws IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !isTraceRequest(request)) {
            return;
        }
        if (request.getLength() > MAX_REQUEST_BYTES) {
            reject(request);
            return;
        }
        final InputStream entityStream = request.getEntityStream();
        if (entityStream == null) {
            return;
        }
        final byte[] body = entityStream.readNBytes((int) MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            reject(request);
            return;
        }
        request.setEntityStream(new ByteArrayInputStream(body));
    }

    private static boolean isTraceRequest(final ContainerRequestContext request) {
        String path = request.getUriInfo().getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path.startsWith("output/");
    }

    private static void reject(final ContainerRequestContext request) {
        request.abortWith(Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "Request body exceeds the maximum of " + MAX_REQUEST_BYTES + " bytes"))
                .build());
    }
}
