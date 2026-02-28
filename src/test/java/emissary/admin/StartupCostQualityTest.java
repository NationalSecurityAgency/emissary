package emissary.admin;

import emissary.directory.DirectoryEntry;
import emissary.directory.DirectoryPlace;
import emissary.directory.IDirectoryPlace;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StartupCostQualityTest extends UnitTest {

    private static DirectoryEntry makeEntry(String key, String serviceType, int cost, int quality) {
        DirectoryEntry entry = mock(DirectoryEntry.class);
        when(entry.getKey()).thenReturn(key);
        when(entry.getServiceType()).thenReturn(serviceType);
        when(entry.getCost()).thenReturn(cost);
        when(entry.getQuality()).thenReturn(quality);
        return entry;
    }

    @Test
    void duplicateCostAndQualityReturnsFalse() {
        DirectoryEntry a = makeEntry("http://host1:8001/FOO.TRANSFORM.SomePlace.http", "TRANSFORM", 50, 90);
        DirectoryEntry b = makeEntry("http://host2:8001/FOO.TRANSFORM.OtherPlace.http", "TRANSFORM", 50, 90);

        try (MockedStatic<DirectoryPlace> mocked = Mockito.mockStatic(DirectoryPlace.class)) {
            IDirectoryPlace dirPlace = mock(IDirectoryPlace.class);
            when(dirPlace.getEntries()).thenReturn(List.of(a, b));
            mocked.when(DirectoryPlace::lookup).thenReturn(dirPlace);

            assertFalse(Startup.verifyCostQualityUniqueness());
        }
    }

    @Test
    void differentCostSameQualityReturnsTrue() {
        DirectoryEntry a = makeEntry("http://host1:8001/FOO.TRANSFORM.SomePlace.http", "TRANSFORM", 50, 90);
        DirectoryEntry b = makeEntry("http://host2:8001/FOO.TRANSFORM.OtherPlace.http", "TRANSFORM", 60, 90);

        try (MockedStatic<DirectoryPlace> mocked = Mockito.mockStatic(DirectoryPlace.class)) {
            IDirectoryPlace dirPlace = mock(IDirectoryPlace.class);
            when(dirPlace.getEntries()).thenReturn(List.of(a, b));
            mocked.when(DirectoryPlace::lookup).thenReturn(dirPlace);

            assertTrue(Startup.verifyCostQualityUniqueness());
        }
    }

    @Test
    void sameCostDifferentQualityReturnsTrue() {
        DirectoryEntry a = makeEntry("http://host1:8001/FOO.TRANSFORM.SomePlace.http", "TRANSFORM", 50, 90);
        DirectoryEntry b = makeEntry("http://host2:8001/FOO.TRANSFORM.OtherPlace.http", "TRANSFORM", 50, 80);

        try (MockedStatic<DirectoryPlace> mocked = Mockito.mockStatic(DirectoryPlace.class)) {
            IDirectoryPlace dirPlace = mock(IDirectoryPlace.class);
            when(dirPlace.getEntries()).thenReturn(List.of(a, b));
            mocked.when(DirectoryPlace::lookup).thenReturn(dirPlace);

            assertTrue(Startup.verifyCostQualityUniqueness());
        }
    }

    @Test
    void duplicatesOnlyWithinSameServiceTypeReturnsTrue() {
        // Same cost+quality but different SERVICE_TYPE — not a routing conflict
        DirectoryEntry a = makeEntry("http://host1:8001/FOO.TRANSFORM.SomePlace.http", "TRANSFORM", 50, 90);
        DirectoryEntry b = makeEntry("http://host2:8001/FOO.ANALYZE.OtherPlace.http", "ANALYZE", 50, 90);

        try (MockedStatic<DirectoryPlace> mocked = Mockito.mockStatic(DirectoryPlace.class)) {
            IDirectoryPlace dirPlace = mock(IDirectoryPlace.class);
            when(dirPlace.getEntries()).thenReturn(List.of(a, b));
            mocked.when(DirectoryPlace::lookup).thenReturn(dirPlace);

            assertTrue(Startup.verifyCostQualityUniqueness());
        }
    }

    @Test
    void emptyEntriesReturnsTrue() {
        try (MockedStatic<DirectoryPlace> mocked = Mockito.mockStatic(DirectoryPlace.class)) {
            IDirectoryPlace dirPlace = mock(IDirectoryPlace.class);
            when(dirPlace.getEntries()).thenReturn(List.of());
            mocked.when(DirectoryPlace::lookup).thenReturn(dirPlace);

            assertTrue(Startup.verifyCostQualityUniqueness());
        }
    }
}
