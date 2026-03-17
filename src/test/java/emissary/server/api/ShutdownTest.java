package emissary.server.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShutdownTest {

    private static HttpServletRequest requestWithRole(String... roles) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        for (String role : new String[] {"admin", "emissary", "support", "everyone", "manager"}) {
            boolean hasRole = false;
            for (String r : roles) {
                if (r.equals(role)) {
                    hasRole = true;
                    break;
                }
            }
            when(req.isUserInRole(role)).thenReturn(hasRole);
        }
        return req;
    }

    @Test
    void adminRoleIsAllowed() {
        // We only test the role guard: use a subclass to intercept before System.exit
        Shutdown guarded = new Shutdown() {
            @Override
            protected Response shutdown(HttpServletRequest request, boolean force) {
                if (!request.isUserInRole("admin") && !request.isUserInRole("emissary")) {
                    return Response.status(Response.Status.FORBIDDEN).entity("Insufficient privileges").build();
                }
                return Response.ok("allowed").build();
            }
        };
        try (Response r = guarded.shutdownNow(requestWithRole("admin"))) {
            assertEquals(200, r.getStatus());
        }
    }

    @Test
    void emissaryRoleIsAllowed() {
        Shutdown guarded = new Shutdown() {
            @Override
            protected Response shutdown(HttpServletRequest request, boolean force) {
                if (!request.isUserInRole("admin") && !request.isUserInRole("emissary")) {
                    return Response.status(Response.Status.FORBIDDEN).entity("Insufficient privileges").build();
                }
                return Response.ok("allowed").build();
            }
        };
        try (Response r = guarded.shutdownNow(requestWithRole("emissary"))) {
            assertEquals(200, r.getStatus());
        }
    }

    @Test
    void lowPrivilegeRoleIsForbidden() {
        Shutdown shutdown = new Shutdown();
        try (Response r = shutdown.shutdownNow(requestWithRole("everyone"))) {
            assertEquals(403, r.getStatus());
        }
    }

    @Test
    void supportRoleIsForbidden() {
        Shutdown shutdown = new Shutdown();
        try (Response r = shutdown.shutdownNow(requestWithRole("support"))) {
            assertEquals(403, r.getStatus());
        }
    }

    @Test
    void managerRoleIsForbidden() {
        Shutdown shutdown = new Shutdown();
        try (Response r = shutdown.shutdownNow(requestWithRole("manager"))) {
            assertEquals(403, r.getStatus());
        }
    }

    @Test
    void forceShutdownForbiddenForLowPrivilege() {
        Shutdown shutdown = new Shutdown();
        try (Response r = shutdown.forceShutdown(requestWithRole("everyone"))) {
            assertEquals(403, r.getStatus());
        }
    }
}
