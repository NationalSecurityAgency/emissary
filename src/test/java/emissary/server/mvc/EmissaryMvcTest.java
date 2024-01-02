package emissary.server.mvc;

import emissary.core.Namespace;
import emissary.directory.DirectoryEntry;
import emissary.directory.DirectoryEntryList;
import emissary.directory.IDirectoryPlace;
import emissary.util.Version;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmissaryMvcTest extends EndpointTestBase {

    @Test
    void dumpDirectory() {
        DirectoryEntryList list = new DirectoryEntryList();
        list.add(new DirectoryEntry("TEST", "Testing", 90, 10));

        IDirectoryPlace dir = mock(IDirectoryPlace.class);
        when(dir.getEntryKeys()).thenReturn(Collections.singleton("THE_KEY"));
        when(dir.getEntryList("THE_KEY")).thenReturn(list);

        Namespace.bind("DirectoryPlace", dir);
        // test template is src/test/resources/dump_directory.mustache
        try (Response response = target("DumpDirectory.action").request().get()) {
            assertEquals(200, response.getStatus());
            String out = response.readEntity(String.class);
            assertTrue(out.contains("THE_KEY"));
            assertTrue(out.contains("<td>TEST</td>"));
            assertTrue(out.contains("<td class=\"num\">90</td>"));
            assertTrue(out.contains("<td class=\"num\">10</td>"));
            assertTrue(out.contains("<td class=\"num\">9090</td>"));
            assertTrue(out.contains("<td class=\"num\">00:00:00</td>"));
        } finally {
            Namespace.unbind("DirectoryPlace");
        }
    }

    @Test
    void env() {
        // test template is src/test/resources/environment.mustache
        try (Response response = target("Environment.action").request().get()) {
            assertEquals(200, response.getStatus());
            String out = response.readEntity(String.class);
            assertTrue(out.contains("<h2>Properties</h2>"));
            assertTrue(out.contains("emissary.node.name: localhost"));
            assertTrue(out.contains("emissary.node.port: 8001"));
            assertTrue(out.contains("emissary.node.scheme: http"));
            assertTrue(out.contains("emissary.node.service.type: server"));
            assertTrue(out.contains("<h2>Environment</h2>"));
            assertTrue(out.contains("PROJECT_BASE:"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void namespace() {
        Namespace.bind("TestDirectoryPlace", new DirectoryEntry("TEST", "TESTSERVICEPLACE", "TRANSFORM", "http://localhost:8001/TestServicePlace",
                "emissary.test.TestServicePlace", 90, 10));
        // cannot use JettyTest because request.getContextPath() throws null pointer, so mock it
        NamespaceAction action = new NamespaceAction();
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getContextPath()).thenReturn("/emissary");
        try {
            Map<String, Object> out = action.getNamespace(request);
            assertNotNull(out);
            assertEquals(1, out.size());
            Object namespaceInfoSet = out.get("namespaces");
            assertTrue(namespaceInfoSet instanceof Set);
            AtomicBoolean found = new AtomicBoolean(false);
            ((Set<NamespaceAction.NamespaceInfo>) namespaceInfoSet).forEach(info -> {
                if ("TestDirectoryPlace".equals(info.key)) {
                    assertEquals("TestDirectoryPlace", info.key);
                    assertEquals("TEST.TESTSERVICEPLACE.TRANSFORM.http://localhost:8001/TestServicePlace$9090 (emissary.test.TestServicePlace)",
                            info.value.toString());
                    assertEquals("emissary.directory.DirectoryEntry", info.valueClassName);
                    assertTrue(info.isDir);
                    found.set(true);
                }
            });
            if (!found.get()) {
                fail("TestDirectoryPlace was not in the NamespaceInfo LinkedHashSet");
            }
        } finally {
            Namespace.unbind("TestDirectoryPlace");
        }
    }

    @Test
    void threaddump() {
        try (Response response = target("Threaddump.action").request().get()) {
            assertEquals(200, response.getStatus());
            String out = response.readEntity(String.class);
            assertTrue(out.contains("Emissary Version:</strong> " + new Version().getVersion()));
            assertTrue(out.contains("JVM Version:</strong> " + System.getProperty("java.vm.version")));
            assertTrue(out.contains("JVM Name:</strong> " + System.getProperty("java.vm.name")));
            assertTrue(out.contains("Deadlocked Threads:"));
            assertTrue(out.contains("Thread Dump:"));
        }
    }

    @Test
    void transferdirectory() {
        DirectoryEntryList list = new DirectoryEntryList();
        list.add(new DirectoryEntry("TEST", "TESTSERVICEPLACE", "TRANSFORM", "http://localhost:8001/TestServicePlace",
                "emissary.test.TestServicePlace", 90, 10));

        IDirectoryPlace idir = mock(IDirectoryPlace.class);
        when(idir.getKey()).thenReturn("TEST_KEY");
        when(idir.getEntryKeys()).thenReturn(Collections.singleton("TestDataId"));
        when(idir.getEntryList("TestDataId")).thenReturn(list);

        Namespace.bind("DirectoryPlace", idir);

        try (Response response = target("TransferDirectory.action").request().get()) {
            assertEquals(200, response.getStatus());
            String out = response.readEntity(String.class);
            assertTrue(out.contains("<directory location=\"TEST_KEY\">"));
            assertTrue(out.contains("<entryList dataid=\"TestDataId\">"));
            assertTrue(out.contains("<key>TEST.TESTSERVICEPLACE.TRANSFORM.http://localhost:8001/TestServicePlace</key>"));
            assertTrue(out.contains("<description>emissary.test.TestServicePlace</description>"));
            assertTrue(out.contains("<cost>90</cost>"));
            assertTrue(out.contains("<quality>10</quality>"));
            assertTrue(out.contains("<expense>9090</expense>"));
        } finally {
            Namespace.unbind("DirectoryPlace");
        }
    }

    @Test
    void unpause() {
        // test template is src/test/resources/environment.mustache
        try (Response response = target("Unpause.action").request().get()) {
            assertEquals(200, response.getStatus());
            String out = response.readEntity(String.class);
            assertTrue(out.contains("Unpausing server..."));
        }
    }

    @Test
    void pause() {
        // test template is src/test/resources/environment.mustache
        try (Response response = target("Pause.action").request().get()) {
            assertEquals(200, response.getStatus());
            String out = response.readEntity(String.class);
            assertTrue(out.contains("Pausing server..."));
        }
    }

    @Test
    void shutdown() {
        // test template is src/test/resources/environment.mustache
        try (Response response = target("Shutdown.action").request().get()) {
            assertEquals(200, response.getStatus());
            String out = response.readEntity(String.class);
            assertTrue(out.contains("Starting shutdown..."));
        }
    }
}
