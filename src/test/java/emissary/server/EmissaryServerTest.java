package emissary.server;

import jakarta.annotation.Nullable;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmissaryServerTest {

    @Test
    void keepsHealthUnauthenticated() {
        final ConstraintSecurityHandler handler = EmissaryServer.buildSecurityHandler();
        final Constraint health = constraintFor(handler, "/api/health");
        assertNotNull(health);
        assertFalse(health.getAuthenticate());
    }

    @Test
    void requiresAuthenticationForEverythingElse() {
        final ConstraintSecurityHandler handler = EmissaryServer.buildSecurityHandler();
        final Constraint fallback = constraintFor(handler, "/*");
        assertNotNull(fallback);
        assertTrue(fallback.getAuthenticate());
        assertEquals("auth", fallback.getName());
    }

    @Nullable
    private static Constraint constraintFor(final ConstraintSecurityHandler handler, final String path) {
        return handler.getConstraintMappings().stream()
                .filter(mapping -> path.equals(mapping.getPathSpec()))
                .map(ConstraintMapping::getConstraint)
                .findFirst()
                .orElse(null);
    }
}
