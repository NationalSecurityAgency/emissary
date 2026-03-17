package emissary.server.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PauseTest {

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
    void adminCanPause() {
        Pause pause = new Pause();
        try (Response r = pause.pause(requestWithRole("admin"))) {
            // 200 or 500 (no server bound) — but NOT 403
            assertEquals(false, r.getStatus() == 403);
        }
    }

    @Test
    void emissaryCanPause() {
        Pause pause = new Pause();
        try (Response r = pause.pause(requestWithRole("emissary"))) {
            assertEquals(false, r.getStatus() == 403);
        }
    }

    @Test
    void everyoneCannotPause() {
        Pause pause = new Pause();
        try (Response r = pause.pause(requestWithRole("everyone"))) {
            assertEquals(403, r.getStatus());
        }
    }

    @Test
    void supportCannotPause() {
        Pause pause = new Pause();
        try (Response r = pause.pause(requestWithRole("support"))) {
            assertEquals(403, r.getStatus());
        }
    }

    @Test
    void managerCannotPause() {
        Pause pause = new Pause();
        try (Response r = pause.pause(requestWithRole("manager"))) {
            assertEquals(403, r.getStatus());
        }
    }

    @Test
    void adminCanUnpause() {
        Pause pause = new Pause();
        try (Response r = pause.unpause(requestWithRole("admin"))) {
            assertEquals(false, r.getStatus() == 403);
        }
    }

    @Test
    void emissaryCanUnpause() {
        Pause pause = new Pause();
        try (Response r = pause.unpause(requestWithRole("emissary"))) {
            assertEquals(false, r.getStatus() == 403);
        }
    }

    @Test
    void everyoneCannotUnpause() {
        Pause pause = new Pause();
        try (Response r = pause.unpause(requestWithRole("everyone"))) {
            assertEquals(403, r.getStatus());
        }
    }
}
