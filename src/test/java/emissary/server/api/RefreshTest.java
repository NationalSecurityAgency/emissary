package emissary.server.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefreshTest {

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
    void adminCanInvalidate() {
        Refresh refresh = new Refresh();
        try (Response r = refresh.invalidatePlaces(requestWithRole("admin"))) {
            assertEquals(false, r.getStatus() == 403);
        }
    }

    @Test
    void emissaryCanInvalidate() {
        Refresh refresh = new Refresh();
        try (Response r = refresh.invalidatePlaces(requestWithRole("emissary"))) {
            assertEquals(false, r.getStatus() == 403);
        }
    }

    @Test
    void everyoneCannotInvalidate() {
        Refresh refresh = new Refresh();
        try (Response r = refresh.invalidatePlaces(requestWithRole("everyone"))) {
            assertEquals(403, r.getStatus());
        }
    }

    @Test
    void supportCannotInvalidate() {
        Refresh refresh = new Refresh();
        try (Response r = refresh.invalidatePlaces(requestWithRole("support"))) {
            assertEquals(403, r.getStatus());
        }
    }

    @Test
    void adminCanRefresh() {
        Refresh refresh = new Refresh();
        try (Response r = refresh.refreshPlaces(requestWithRole("admin"))) {
            assertEquals(false, r.getStatus() == 403);
        }
    }

    @Test
    void emissaryCanRefresh() {
        Refresh refresh = new Refresh();
        try (Response r = refresh.refreshPlaces(requestWithRole("emissary"))) {
            assertEquals(false, r.getStatus() == 403);
        }
    }

    @Test
    void everyoneCannotRefresh() {
        Refresh refresh = new Refresh();
        try (Response r = refresh.refreshPlaces(requestWithRole("everyone"))) {
            assertEquals(403, r.getStatus());
        }
    }

    @Test
    void managerCannotRefresh() {
        Refresh refresh = new Refresh();
        try (Response r = refresh.refreshPlaces(requestWithRole("manager"))) {
            assertEquals(403, r.getStatus());
        }
    }
}
