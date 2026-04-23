package emissary.config;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * Tests that configuration entry order is preserved during merge operations. Entries should maintain their relative
 * order from the source configuration regardless of whether they are loaded directly or via merge.
 */
class ServiceConfigGuideOrderTest extends UnitTest {

    private static final String KEY = "TEST_KEY";

    /**
     * Helper to convert string to InputStream for ServiceConfigGuide constructor.
     */
    private static InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("Configuration Entry Ordering")
    class EntryOrderingTests {

        @Test
        @DisplayName("Non-merge operation preserves entry order")
        void testNonMergePreservesOrder() throws IOException {
            String config = "TEST_KEY = valueA\n" +
                    "TEST_KEY = valueB\n" +
                    "TEST_KEY = valueC\n";

            ServiceConfigGuide guide = new ServiceConfigGuide(toStream(config));
            List<String> entries = guide.findEntries(KEY);

            assertEquals(3, entries.size(), "Should have 3 entries");
            assertEquals("valueA", entries.get(0), "First entry should be valueA");
            assertEquals("valueB", entries.get(1), "Second entry should be valueB");
            assertEquals("valueC", entries.get(2), "Third entry should be valueC");
        }

        @Test
        @DisplayName("Merge operation preserves entry order")
        void testMergePreservesOrder() throws IOException {
            // Base configuration
            String baseConfig = "EXISTING_KEY = existingValue\n";

            // Configuration to merge - order matters!
            String mergeConfig = "TEST_KEY = valueA\n" +
                    "TEST_KEY = valueB\n" +
                    "TEST_KEY = valueC\n";

            ServiceConfigGuide baseGuide = new ServiceConfigGuide(toStream(baseConfig));
            ServiceConfigGuide mergeGuide = new ServiceConfigGuide(toStream(mergeConfig));

            // Perform merge
            baseGuide.merge(mergeGuide);

            List<String> entries = baseGuide.findEntries(KEY);

            assertEquals(3, entries.size(), "Should have 3 entries after merge");

            // Verify merged entries maintain their original order
            assertEquals("valueA", entries.get(0),
                    "First merged entry should be valueA, not reversed");
            assertEquals("valueB", entries.get(1),
                    "Second merged entry should be valueB");
            assertEquals("valueC", entries.get(2),
                    "Third merged entry should be valueC, not reversed");
        }

        @Test
        @DisplayName("Merge places new entries before existing while preserving internal order")
        void testMergePlacementAndOrder() throws IOException {
            String baseConfig = "TEST_KEY = baseValue1\n" +
                    "TEST_KEY = baseValue2\n";

            String mergeConfig = "TEST_KEY = mergedA\n" +
                    "TEST_KEY = mergedB\n";

            ServiceConfigGuide baseGuide = new ServiceConfigGuide(toStream(baseConfig));
            ServiceConfigGuide mergeGuide = new ServiceConfigGuide(toStream(mergeConfig));

            baseGuide.merge(mergeGuide);

            List<String> entries = baseGuide.findEntries(KEY);

            assertEquals(4, entries.size(), "Should have 4 total entries");

            // Merged entries come first and maintain their relative order
            assertEquals("mergedA", entries.get(0), "Merged entries first: mergedA");
            assertEquals("mergedB", entries.get(1), "Merged entries maintain order: mergedB");
            assertEquals("baseValue1", entries.get(2), "Base entries follow: baseValue1");
            assertEquals("baseValue2", entries.get(3), "Base entries maintain order: baseValue2");
        }

        @Test
        @DisplayName("Order consistency between direct load and merge")
        void testConsistentOrderBetweenLoadAndMerge() throws IOException {
            String config = "TEST_KEY = alpha\n" +
                    "TEST_KEY = beta\n" +
                    "TEST_KEY = gamma\n" +
                    "TEST_KEY = delta\n" +
                    "TEST_KEY = epsilon\n";

            // Method 1: Direct load
            ServiceConfigGuide directGuide = new ServiceConfigGuide(toStream(config));
            List<String> directEntries = directGuide.findEntries(KEY);

            // Method 2: Empty base + merge
            ServiceConfigGuide emptyBase = new ServiceConfigGuide(
                    toStream("# empty configuration\n"));
            ServiceConfigGuide toMerge = new ServiceConfigGuide(toStream(config));
            emptyBase.merge(toMerge);
            List<String> mergedEntries = emptyBase.findEntries(KEY);

            // Both methods should produce identical order
            assertEquals(directEntries.size(), mergedEntries.size(),
                    "Same number of entries");

            for (int i = 0; i < directEntries.size(); i++) {
                assertEquals(directEntries.get(i), mergedEntries.get(i),
                        "Entry at position " + i + " should match between direct load and merge");
            }
        }
    }

    @Nested
    @DisplayName("Multiple Merge Operations")
    class MultipleMergeTests {

        @Test
        @DisplayName("Sequential merges preserve order with override semantics")
        void testSequentialMergesPreserveOrder() throws IOException {
            String baseConfig = "BASE_KEY = baseValue\n";
            String earlierMerge = "TEST_KEY = earlier1\n" +
                    "TEST_KEY = earlier2\n";
            String laterMerge = "TEST_KEY = later1\n" +
                    "TEST_KEY = later2\n";

            ServiceConfigGuide baseGuide = new ServiceConfigGuide(toStream(baseConfig));
            ServiceConfigGuide earlierGuide = new ServiceConfigGuide(toStream(earlierMerge));
            ServiceConfigGuide laterGuide = new ServiceConfigGuide(toStream(laterMerge));

            baseGuide.merge(earlierGuide);
            baseGuide.merge(laterGuide);

            List<String> entries = baseGuide.findEntries(KEY);

            assertEquals(4, entries.size(), "Should have 4 entries total");

            // Later merges prepend for override semantics (findStringEntry returns first match)
            // This is INTENTIONAL: later config should override earlier config
            assertEquals("later1", entries.get(0), "Most recent merge has priority: later1");
            assertEquals("later2", entries.get(1), "Recent merge maintains order: later2");
            assertEquals("earlier1", entries.get(2), "Earlier merge follows: earlier1");
            assertEquals("earlier2", entries.get(3), "Earlier merge maintains order: earlier2");
        }
    }

    @Nested
    @DisplayName("Flavored Configuration Scenarios")
    class FlavoredConfigTests {

        @Test
        @DisplayName("Flavor override preserves handler chain order")
        void testFlavoredMergePreservesHandlerOrder() throws IOException {
            // Simulating base service config
            String baseConfig = "SERVICE_NAME = DataProcessor\n" +
                    "STAGE = TRANSFORM\n";

            // Simulating flavored override with ordered handlers
            String flavorConfig = "HANDLER = ValidationHandler\n" +
                    "HANDLER = TransformHandler\n" +
                    "HANDLER = OutputHandler\n";

            ServiceConfigGuide baseGuide = new ServiceConfigGuide(toStream(baseConfig));
            ServiceConfigGuide flavorGuide = new ServiceConfigGuide(toStream(flavorConfig));

            baseGuide.merge(flavorGuide);

            List<String> handlers = baseGuide.findEntries("HANDLER");

            assertEquals(3, handlers.size(), "Should have 3 handlers");
            // Handler execution order is critical!
            assertEquals("ValidationHandler", handlers.get(0),
                    "First handler: ValidationHandler");
            assertEquals("TransformHandler", handlers.get(1),
                    "Second handler: TransformHandler");
            assertEquals("OutputHandler", handlers.get(2),
                    "Third handler: OutputHandler");
        }

        @Test
        @DisplayName("Coordination place priority order preserved")
        void testCoordinationPlacePriorityOrder() throws IOException {
            String baseConfig = "PLACE_NAME = CoordinationPlace\n";

            String flavorConfig = "COORDINATE_WITH = \"http://host1:8001/CoordPlace\"\n" +
                    "COORDINATE_WITH = \"http://host2:8002/CoordPlace\"\n" +
                    "COORDINATE_WITH = \"http://host3:8003/CoordPlace\"\n";

            ServiceConfigGuide baseGuide = new ServiceConfigGuide(toStream(baseConfig));
            ServiceConfigGuide flavorGuide = new ServiceConfigGuide(toStream(flavorConfig));

            baseGuide.merge(flavorGuide);

            List<String> coords = baseGuide.findEntries("COORDINATE_WITH");

            assertEquals(3, coords.size());
            // Coordination order often determines primary/backup sequence
            assertTrue(coords.get(0).contains("host1"), "Primary should be host1");
            assertTrue(coords.get(1).contains("host2"), "Secondary should be host2");
            assertTrue(coords.get(2).contains("host3"), "Tertiary should be host3");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Single entry merge works correctly")
        void testSingleEntryMerge() throws IOException {
            String baseConfig = "OTHER_KEY = otherValue\n";
            String mergeConfig = "TEST_KEY = singleValue\n";

            ServiceConfigGuide baseGuide = new ServiceConfigGuide(toStream(baseConfig));
            ServiceConfigGuide mergeGuide = new ServiceConfigGuide(toStream(mergeConfig));

            baseGuide.merge(mergeGuide);

            List<String> entries = baseGuide.findEntries(KEY);

            assertEquals(1, entries.size());
            assertEquals("singleValue", entries.get(0));
        }

        @Test
        @DisplayName("Empty configuration merge has no effect")
        void testEmptyMerge() throws IOException {
            String baseConfig = "TEST_KEY = valueA\n" +
                    "TEST_KEY = valueB\n";
            String emptyConfig = "# This is an empty configuration\n";

            ServiceConfigGuide baseGuide = new ServiceConfigGuide(toStream(baseConfig));
            ServiceConfigGuide emptyGuide = new ServiceConfigGuide(toStream(emptyConfig));

            baseGuide.merge(emptyGuide);

            List<String> entries = baseGuide.findEntries(KEY);

            assertEquals(2, entries.size());
            assertEquals("valueA", entries.get(0));
            assertEquals("valueB", entries.get(1));
        }

        @Test
        @DisplayName("Large number of entries maintains order")
        void testLargeEntryCount() throws IOException {
            StringBuilder config = new StringBuilder();
            int numEntries = 50;

            for (int i = 0; i < numEntries; i++) {
                config.append(String.format("TEST_KEY = value%03d%n", i));
            }

            // Direct load
            ServiceConfigGuide directGuide = new ServiceConfigGuide(
                    toStream(config.toString()));

            // Via merge
            ServiceConfigGuide emptyBase = new ServiceConfigGuide(
                    toStream("# empty\n"));
            ServiceConfigGuide toMerge = new ServiceConfigGuide(
                    toStream(config.toString()));
            emptyBase.merge(toMerge);

            List<String> directEntries = directGuide.findEntries(KEY);
            List<String> mergedEntries = emptyBase.findEntries(KEY);

            assertEquals(numEntries, directEntries.size());
            assertEquals(numEntries, mergedEntries.size());

            for (int i = 0; i < numEntries; i++) {
                String expected = String.format("value%03d", i);
                assertEquals(expected, directEntries.get(i),
                        "Direct: wrong at position " + i);
                assertEquals(expected, mergedEntries.get(i),
                        "Merge: wrong value at position " + i);
            }
        }

        @Test
        @DisplayName("Mixed keys preserve per-key ordering")
        void testMixedKeysPreserveOrder() throws IOException {
            String config = "KEY_A = a1\n" +
                    "KEY_B = b1\n" +
                    "KEY_A = a2\n" +
                    "KEY_B = b2\n" +
                    "KEY_A = a3\n";

            ServiceConfigGuide emptyBase = new ServiceConfigGuide(
                    toStream("# empty\n"));
            ServiceConfigGuide toMerge = new ServiceConfigGuide(toStream(config));
            emptyBase.merge(toMerge);

            List<String> aEntries = emptyBase.findEntries("KEY_A");
            List<String> bEntries = emptyBase.findEntries("KEY_B");

            assertEquals(3, aEntries.size());
            assertEquals("a1", aEntries.get(0));
            assertEquals("a2", aEntries.get(1));
            assertEquals("a3", aEntries.get(2));

            assertEquals(2, bEntries.size());
            assertEquals("b1", bEntries.get(0));
            assertEquals("b2", bEntries.get(1));
        }
    }
}
