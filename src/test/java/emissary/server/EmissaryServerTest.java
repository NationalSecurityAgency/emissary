package emissary.server;

import jakarta.annotation.Nullable;
import org.eclipse.jetty.ee10.servlet.security.ConstraintMapping;
import org.eclipse.jetty.ee10.servlet.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.Constraint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmissaryServerTest {

    @Test
    void keepsHealthUnauthenticated() {
        final ConstraintSecurityHandler handler = EmissaryServer.buildSecurityHandler();
        final Constraint health = constraintFor(handler, "/api/health");
        assertNotNull(health);
        assertEquals(Constraint.Authorization.ALLOWED, health.getAuthorization());
    }

    @Test
    void requiresAuthenticationForEverythingElse() {
        final ConstraintSecurityHandler handler = EmissaryServer.buildSecurityHandler();
        final Constraint fallback = constraintFor(handler, "/*");
        assertNotNull(fallback);
        assertEquals(Constraint.Authorization.SPECIFIC_ROLE, fallback.getAuthorization());
        assertEquals("auth", fallback.getName());
        assertTrue(fallback.getRoles().contains("emissary"));
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
