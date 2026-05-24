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
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            assertEquals("valueC", entries.get(0), "Third merged entry should be valueC, not reversed");
            assertEquals("valueB", entries.get(1), "Second merged entry should be valueB");
            assertEquals("valueA", entries.get(2), "First merged entry should be valueA, not reversed");
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
            assertEquals("mergedB", entries.get(0), "Merged entries maintain order: mergedB");
            assertEquals("mergedA", entries.get(1), "Merged entries first: mergedA");
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

            int size = directEntries.size();
            IntStream.range(0, size)
                    .forEach(i -> assertEquals(directEntries.get(i), mergedEntries.get(size - 1 - i),
                            "Entry at position " + i + " should match merge" + (size - 1 - i)));

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
            assertEquals("later2", entries.get(0), "Recent merge maintains order: later2");
            assertEquals("later1", entries.get(1), "Most recent merge has priority: later1");
            assertEquals("earlier2", entries.get(2), "Earlier merge maintains order: earlier2");
            assertEquals("earlier1", entries.get(3), "Earlier merge follows: earlier1");
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
            assertEquals("OutputHandler", handlers.get(0),
                    "Third handler: OutputHandler");
            assertEquals("TransformHandler", handlers.get(1),
                    "Second handler: TransformHandler");
            assertEquals("ValidationHandler", handlers.get(2),
                    "First handler: ValidationHandler");
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
            assertTrue(coords.get(0).contains("host3"), "Primary should be host3");
            assertTrue(coords.get(1).contains("host2"), "Secondary should be host2");
            assertTrue(coords.get(2).contains("host1"), "Tertiary should be host3");
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

            int size = directEntries.size();
            IntStream.range(0, size)
                    .forEach(i -> assertEquals(directEntries.get(i), mergedEntries.get(size - 1 - i),
                            "Entry at position " + i + " should match merge" + (size - 1 - i)));
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
            assertEquals("a3", aEntries.get(0));
            assertEquals("a2", aEntries.get(1));
            assertEquals("a1", aEntries.get(2));

            assertEquals(2, bEntries.size());
            assertEquals("b2", bEntries.get(0));
            assertEquals("b1", bEntries.get(1));
        }
    }


    @Test
    void testMultiMerge() throws IOException {
        ServiceConfigGuide layer1 = new ServiceConfigGuide(toStream("KEY_A = baseA\nKEY_B = baseB\n"));
        ServiceConfigGuide layer2 = new ServiceConfigGuide(toStream("KEY_A = overrideA1\nKEY_C = c1\n"));
        ServiceConfigGuide layer3 = new ServiceConfigGuide(toStream("KEY_A = overrideA2\nKEY_B = overrideB1\n"));

        layer1.merge(layer2);
        layer1.merge(layer3);

        List<String> aEntries = layer1.findEntries("KEY_A");
        assertEquals(3, aEntries.size());
        assertEquals("overrideA2", aEntries.get(0));
        assertEquals("overrideA1", aEntries.get(1));
        assertEquals("baseA", aEntries.get(2));

        List<String> bEntries = layer1.findEntries("KEY_B");
        assertEquals(2, bEntries.size());
        assertEquals("overrideB1", bEntries.get(0));
        assertEquals("baseB", bEntries.get(1));

        List<String> cEntries = layer1.findEntries("KEY_C");
        assertEquals(1, cEntries.size());
        assertEquals("c1", cEntries.get(0));
    }

    @Test
    void testMultiMerge2() throws IOException {
        String root = "COMMON_KEY = rootValue\n";
        String flavorLeft = "LEFT_KEY = leftValue\nCOMMON_KEY = leftOverride\n";
        String flavorRight = "RIGHT_KEY = rightValue\nCOMMON_KEY = rightOverride\n";

        ServiceConfigGuide leftGuide = new ServiceConfigGuide(toStream(root));
        leftGuide.merge(new ServiceConfigGuide(toStream(flavorLeft)));

        ServiceConfigGuide rightGuide = new ServiceConfigGuide(toStream(root));
        rightGuide.merge(new ServiceConfigGuide(toStream(flavorRight)));

        ServiceConfigGuide masterGuide = new ServiceConfigGuide(toStream("# Empty Master\n"));
        masterGuide.merge(leftGuide);
        masterGuide.merge(rightGuide);

        List<String> commonEntries = masterGuide.findEntries("COMMON_KEY");

        assertTrue(commonEntries.contains("rightOverride"));
        assertTrue(commonEntries.contains("leftOverride"));
        assertEquals("rootValue", commonEntries.get(0), "Most recent merged branch (right) should take priority");
    }

    @Test
    void testMultiMergeFlavored() throws IOException {
        String defaultFlavor = "TIMEOUT = 5000\nRETRY_COUNT = 3\nENV = prod\n";
        String regionFlavor = "TIMEOUT = 4000\nREGION = us-east\n";
        String zoneFlavor = "ZONE = us-east-1a\n";
        String instanceFlavor = "TIMEOUT = 2500\nRETRY_COUNT = 5\n";

        ServiceConfigGuide base = new ServiceConfigGuide(toStream(defaultFlavor));
        base.merge(new ServiceConfigGuide(toStream(regionFlavor)));
        base.merge(new ServiceConfigGuide(toStream(zoneFlavor)));
        base.merge(new ServiceConfigGuide(toStream(instanceFlavor)));

        List<String> timeouts = base.findEntries("TIMEOUT");
        assertEquals(3, timeouts.size());
        assertEquals("2500", timeouts.get(0), "Instance layer priority");
        assertEquals("4000", timeouts.get(1), "Region layer fallback");
        assertEquals("5000", timeouts.get(2), "Base default fallback");

        List<String> retries = base.findEntries("RETRY_COUNT");
        assertEquals("5", retries.get(0));
        assertEquals("3", retries.get(1));
    }

    @Test
    void testFlavorBlankOverride() {
        String baseConfig = "FEATURE_FLAGS = flagA\nFEATURE_FLAGS = flagB\n";
        String flavorConfig = "FEATURE_FLAGS = \n";

        assertDoesNotThrow(() -> new ServiceConfigGuide(toStream(baseConfig)));
        assertThrows(IOException.class, () -> new ServiceConfigGuide(toStream(flavorConfig)));
    }

    @Test
    void testSelfMerge() throws IOException {
        String config = "TEST_KEY = selfValue\nCOUNTER = 1\n";
        ServiceConfigGuide guide = new ServiceConfigGuide(toStream(config));

        assertDoesNotThrow(() -> guide.merge(guide),
                "Merging a configuration instance into itself must not throw exceptions or create infinite loops");

        List<String> entries = guide.findEntries(KEY);
        assertNotNull(entries);
    }

    @Test
    void testSpecialCharacterKeysAndFormatting() throws IOException {
        String config = "KEY\\=WITH\\=EQUALS = value_containing_\\=equals\n" +
                "  SPACED_KEY   =   spaced_value  \n" +
                "COMMENT_LIKE_KEY = value # not a comment\n" +
                "COLON_KEY : colon_value\n";

        ServiceConfigGuide base = new ServiceConfigGuide(toStream("# Initialization\n"));
        ServiceConfigGuide target = new ServiceConfigGuide(toStream(config));
        base.merge(target);

        List<String> spacedEntries = base.findEntries("SPACED_KEY");
        assertFalse(spacedEntries.isEmpty(), "Keys with surrounding whitespace should be resolvable");

        List<String> commentLike = base.findEntries("COMMENT_LIKE_KEY");
        assertFalse(commentLike.isEmpty());
        assertFalse(commentLike.get(0).contains("#"), "Inline hash signs should not be retained if within data bounds");
        assertEquals("value", commentLike.get(0));
    }

    @Test
    void testMalformedOrEmptyKeyValuePairs() {
        String malformedConfig = " = anonymousValue\n" +
                "EMPTY_VAL_KEY =\n" +
                "BAD_LINE_NO_DELIMITER\n" +
                "TEST_KEY = validPostMalformed\n";

        assertThrows(IOException.class, () -> new ServiceConfigGuide(toStream(malformedConfig)));
    }

    @Test
    void testCancellationDropsPreviousEntries() throws IOException {
        String baseConfig = "TEST_KEY = valueA\n" +
                "TEST_KEY = valueB\n" +
                "KEEP_KEY = keepMe\n";

        String cancelConfig = "TEST_KEY != \"*\"\n" +
                "TEST_KEY = newValue\n";

        ServiceConfigGuide baseGuide = new ServiceConfigGuide(toStream(baseConfig));
        ServiceConfigGuide cancelGuide = new ServiceConfigGuide(toStream(cancelConfig));

        baseGuide.merge(cancelGuide);

        List<String> entries = baseGuide.findEntries(KEY);
        assertEquals(1, entries.size(), "Previous values should be dropped, leaving only the post-cancellation value");
        assertEquals("newValue", entries.get(0));

        List<String> keepEntries = baseGuide.findEntries("KEEP_KEY");
        assertEquals(1, keepEntries.size());
        assertEquals("keepMe", keepEntries.get(0));
    }

    @Test
    void testCancellation() throws IOException {
        String baseConfig = "KEEP_KEY = clearSailing\n";
        String cancelConfig = "UNKNOWN_KEY != \"*\"\n"; // Cancelling something that doesn't exist

        ServiceConfigGuide baseGuide = new ServiceConfigGuide(toStream(baseConfig));
        ServiceConfigGuide cancelGuide = new ServiceConfigGuide(toStream(cancelConfig));

        assertDoesNotThrow(() -> baseGuide.merge(cancelGuide),
                "Cancelling a non-existent key should be a silent no-op");

        assertTrue(baseGuide.findEntries("UNKNOWN_KEY").isEmpty());
    }

    @Test
    void testMultiFlavorMix() throws IOException {
        String config1 =
                "APP_NAME = Emissary\n" +
                        "DB_MAX_CONNECTIONS = 20\n" +
                        "LOG_LEVEL = INFO\n" +
                        "FEATURE_FLAGS = auth-v2\n" +
                        "FEATURE_FLAGS = coalesce\n" +
                        "ROUTING_ENDPOINTS = http://emissary/route";

        String config2 =
                "DB_MAX_CONNECTIONS = 100\n" +
                        "CLOUD_PROVIDER = AWS\n" +
                        "STORAGE_BUCKET = s3://emissary\n" +
                        "ROUTING_ENDPOINTS = http://aws.emissary/route";

        String config3 =
                "OBJECT_TRACE = off\n" +
                        "LOG_LEVEL = WARN\n" +
                        "DATA_AGE = \"7d\"\n" +
                        "STORAGE_BUCKET = s3://emissary2\n";

        String config4 =
                "DB_MAX_CONNECTIONS = 500\n" +
                        "DEPLOY_ENV = PROD\n" +
                        "FEATURE_FLAGS = push\n" +
                        "FEATURE_FLAGS = tls\n";

        String config5 =
                "LOG_LEVEL = DEBUG\n" +
                        "FEATURE_FLAGS = test\n";

        ServiceConfigGuide configG = new ServiceConfigGuide(toStream(config1));
        ServiceConfigGuide layer2 = new ServiceConfigGuide(toStream(config2));
        ServiceConfigGuide layer3 = new ServiceConfigGuide(toStream(config3));
        ServiceConfigGuide layer4 = new ServiceConfigGuide(toStream(config4));
        ServiceConfigGuide layer5 = new ServiceConfigGuide(toStream(config5));

        configG.merge(layer2);
        configG.merge(layer3);
        configG.merge(layer4);
        configG.merge(layer5);

        List<String> logLevels = configG.findEntries("LOG_LEVEL");
        assertEquals(3, logLevels.size());
        assertEquals("DEBUG", logLevels.get(0));
        assertEquals("WARN", logLevels.get(1));
        assertEquals("INFO", logLevels.get(2));

        List<String> dbPools = configG.findEntries("DB_MAX_CONNECTIONS");
        assertEquals(3, dbPools.size());
        assertEquals("500", dbPools.get(0));
        assertEquals("100", dbPools.get(1));
        assertEquals("20", dbPools.get(2));

        List<String> featureFlags = configG.findEntries("FEATURE_FLAGS");
        assertEquals(5, featureFlags.size());
        assertEquals("test", featureFlags.get(0));
        assertEquals("tls", featureFlags.get(1));
        assertEquals("push", featureFlags.get(2));
        assertEquals("auth-v2", featureFlags.get(3));
        assertEquals("coalesce", featureFlags.get(4));

        List<String> storageBuckets = configG.findEntries("STORAGE_BUCKET");
        assertEquals(2, storageBuckets.size());
        assertEquals("s3://emissary2", storageBuckets.get(0));
        assertEquals("s3://emissary", storageBuckets.get(1));

        List<String> appNames = configG.findEntries("APP_NAME");
        assertEquals(1, appNames.size());
        assertEquals("Emissary", appNames.get(0));

        List<String> residency = configG.findEntries("DATA_AGE");
        assertEquals(1, residency.size());
        assertEquals("7d", residency.get(0));

        List<String> objTrace = configG.findEntries("OBJECT_TRACE");
        assertEquals(1, objTrace.size());
        assertEquals("off", objTrace.get(0));
    }

    @Test
    void testMultiFlavorWithCancellations() throws IOException {
        String config1 =
                "LOG_LEVEL = INFO\n" +
                        "FEATURE_FLAGS = legacy-auth\n" +
                        "FEATURE_FLAGS = coalesce\n" +
                        "ROUTING_ENDPOINTS = http://emissary/route1\n" +
                        "ROUTING_ENDPOINTS = http://emissary/route2\n" +
                        "TIMEOUT = 5000\n";

        String config2 =
                "CLOUD_PROVIDER = AWS\n" +
                        "ROUTING_ENDPOINTS = http://aws.emissary/route1\n" +
                        "TIMEOUT = 3000\n";

        String config3 =
                "OBJECT_TRACE = off\n" +
                        "ROUTING_ENDPOINTS != \"*\"\n" +
                        "ROUTING_ENDPOINTS = http://emissary/v1\n" +
                        "FEATURE_FLAGS != \"*\"\n" +
                        "FEATURE_FLAGS = tls\n";

        String config4 =
                "DEPLOY_ENV = PROD\n" +
                        "FEATURE_FLAGS = push\n" +
                        "TIMEOUT = 2500\n";

        String config5 =
                "LOG_LEVEL = DEBUG\n" +
                        "TIMEOUT != \"*\"\n" +
                        "FEATURE_FLAGS = debug\n";

        ServiceConfigGuide configG = new ServiceConfigGuide(toStream(config1));

        ServiceConfigGuide flavor2 = new ServiceConfigGuide(toStream(config2));
        ServiceConfigGuide flavor3 = new ServiceConfigGuide(toStream(config3));
        ServiceConfigGuide flavor4 = new ServiceConfigGuide(toStream(config4));
        ServiceConfigGuide flavor5 = new ServiceConfigGuide(toStream(config5));

        configG.merge(flavor2);

        List<String> routes = configG.findEntries("ROUTING_ENDPOINTS");
        assertEquals(3, routes.size());
        assertEquals("http://aws.emissary/route1", routes.get(0));
        assertEquals("http://emissary/route1", routes.get(1));

        List<String> timeouts = configG.findEntries("TIMEOUT");
        assertEquals(2, timeouts.size());
        assertEquals("3000", timeouts.get(0));
        assertEquals("5000", timeouts.get(1));

        configG.merge(flavor3);

        routes = configG.findEntries("ROUTING_ENDPOINTS");
        assertEquals(1, routes.size());
        assertEquals("http://emissary/v1", routes.get(0));

        List<String> flags = configG.findEntries("FEATURE_FLAGS");
        assertEquals(1, flags.size());
        assertEquals("tls", flags.get(0));

        List<String> compliance = configG.findEntries("OBJECT_TRACE");
        assertEquals(1, compliance.size());
        assertEquals("off", compliance.get(0));

        configG.merge(flavor4);

        flags = configG.findEntries("FEATURE_FLAGS");
        assertEquals(2, flags.size());
        assertEquals("push", flags.get(0));
        assertEquals("tls", flags.get(1));

        timeouts = configG.findEntries("TIMEOUT");
        assertEquals(3, timeouts.size()); // 2500, 3000, 5000
        assertEquals("2500", timeouts.get(0));

        configG.merge(flavor5);

        routes = configG.findEntries("ROUTING_ENDPOINTS");
        assertEquals(1, routes.size());
        assertEquals("http://emissary/v1", routes.get(0));

        flags = configG.findEntries("FEATURE_FLAGS");
        assertEquals(3, flags.size());
        assertEquals("debug", flags.get(0));
        assertEquals("push", flags.get(1));
        assertEquals("tls", flags.get(2));
        assertFalse(flags.contains("legacy-auth"));
        assertFalse(flags.contains("coalesce"));

        timeouts = configG.findEntries("TIMEOUT");
        assertTrue(timeouts.isEmpty());

        List<String> logLevels = configG.findEntries("LOG_LEVEL");
        assertEquals(2, logLevels.size());
        assertEquals("DEBUG", logLevels.get(0));
        assertEquals("INFO", logLevels.get(1));

        compliance = configG.findEntries("OBJECT_TRACE");
        assertEquals(1, compliance.size());
        assertEquals("off", compliance.get(0));
    }
}
