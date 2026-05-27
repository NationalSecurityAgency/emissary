package emissary.config;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that configuration entry order is preserved during merge operations. Entries should maintain their relative
 * order from the source configuration regardless of whether they are loaded directly or via merge.
 */
class ServiceConfigGuideOrderTest extends UnitTest {

    private static final String KEY = "TEST_KEY";

    /**
     * Helper to join multiline configuration lines and convert to InputStream.
     */
    private static InputStream toStream(String... lines) {
        String content = String.join("\n", lines) + "\n";
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testNonMergePreservesOrder() throws IOException {
        var guide = new ServiceConfigGuide(toStream(
                KEY + " = valueA",
                KEY + " = valueB",
                KEY + " = valueC"));

        assertIterableEquals(List.of("valueA", "valueB", "valueC"), guide.findEntries(KEY));
    }

    @Test
    void testMergePreservesOrder() throws IOException {
        var baseGuide = new ServiceConfigGuide(toStream("EXISTING_KEY = existingValue"));
        var mergeGuide = new ServiceConfigGuide(toStream(
                KEY + " = valueA",
                KEY + " = valueB",
                KEY + " = valueC"));

        baseGuide.merge(mergeGuide);
        assertIterableEquals(List.of("valueC", "valueB", "valueA"), baseGuide.findEntries(KEY));
    }

    @Test
    void testMergePlacementAndOrder() throws IOException {
        var baseGuide = new ServiceConfigGuide(toStream(KEY + " = baseValue1", KEY + " = baseValue2"));
        var mergeGuide = new ServiceConfigGuide(toStream(KEY + " = mergedA", KEY + " = mergedB"));

        baseGuide.merge(mergeGuide);
        assertIterableEquals(List.of("mergedB", "mergedA", "baseValue1", "baseValue2"), baseGuide.findEntries(KEY));
    }

    @Test
    void testConsistentOrderBetweenLoadAndMerge() throws IOException {
        String[] configLines = {KEY + " = alpha", KEY + " = beta", KEY + " = gamma", KEY + " = delta", KEY + " = epsilon"};

        var directEntries = new ServiceConfigGuide(toStream(configLines)).findEntries(KEY);

        var emptyBase = new ServiceConfigGuide(toStream("# empty configuration"));
        emptyBase.merge(new ServiceConfigGuide(toStream(configLines)));
        var mergedEntries = emptyBase.findEntries(KEY);

        Collections.reverse(mergedEntries);
        assertIterableEquals(directEntries, mergedEntries);
    }

    @Test
    void testSequentialMergesPreserveOrder() throws IOException {
        var baseGuide = new ServiceConfigGuide(toStream("BASE_KEY = baseValue"));
        var earlierGuide = new ServiceConfigGuide(toStream(KEY + " = earlier1", KEY + " = earlier2"));
        var laterGuide = new ServiceConfigGuide(toStream(KEY + " = later1", KEY + " = later2"));

        baseGuide.merge(earlierGuide);
        baseGuide.merge(laterGuide);

        assertIterableEquals(List.of("later2", "later1", "earlier2", "earlier1"), baseGuide.findEntries(KEY));
    }

    @Test
    void testFlavoredMergePreservesHandlerOrder() throws IOException {
        var baseGuide = new ServiceConfigGuide(toStream("SERVICE_NAME = DataProcessor", "STAGE = TRANSFORM"));
        var flavorGuide = new ServiceConfigGuide(toStream(KEY + " = ValidationHandler", KEY + " = TransformHandler", KEY + " = OutputHandler"));

        baseGuide.merge(flavorGuide);
        assertIterableEquals(List.of("OutputHandler", "TransformHandler", "ValidationHandler"), baseGuide.findEntries(KEY));
    }

    @Test
    void testCoordinationPlacePriorityOrder() throws IOException {
        var baseGuide = new ServiceConfigGuide(toStream("PLACE_NAME = CoordinationPlace"));
        var flavorGuide = new ServiceConfigGuide(toStream(
                KEY + " = \"http://host1:8001/CoordPlace\"",
                KEY + " = \"http://host2:8002/CoordPlace\"",
                KEY + " = \"http://host3:8003/CoordPlace\""));

        baseGuide.merge(flavorGuide);
        List<String> coords = baseGuide.findEntries(KEY);

        assertEquals(3, coords.size());
        assertTrue(coords.get(0).contains("host3"));
        assertTrue(coords.get(1).contains("host2"));
        assertTrue(coords.get(2).contains("host1"));
    }

    @Test
    void testSingleEntryMerge() throws IOException {
        var baseGuide = new ServiceConfigGuide(toStream("OTHER_KEY = otherValue"));
        var mergeGuide = new ServiceConfigGuide(toStream(KEY + " = singleValue"));

        baseGuide.merge(mergeGuide);
        assertIterableEquals(List.of("singleValue"), baseGuide.findEntries(KEY));
    }

    @Test
    void testEmptyMerge() throws IOException {
        var baseGuide = new ServiceConfigGuide(toStream(KEY + " = valueA", KEY + " = valueB"));
        var emptyGuide = new ServiceConfigGuide(toStream("# This is an empty configuration"));

        baseGuide.merge(emptyGuide);
        assertIterableEquals(List.of("valueA", "valueB"), baseGuide.findEntries(KEY));
    }

    @Test
    void testLargeEntryCount() throws IOException {
        int numEntries = 50;
        String[] configLines = IntStream.range(0, numEntries)
                .mapToObj(i -> String.format("%s = value%03d", KEY, i))
                .toArray(String[]::new);

        var directEntries = new ServiceConfigGuide(toStream(configLines)).findEntries(KEY);

        var emptyBase = new ServiceConfigGuide(toStream("# empty"));
        emptyBase.merge(new ServiceConfigGuide(toStream(configLines)));
        var mergedEntries = emptyBase.findEntries(KEY);

        Collections.reverse(mergedEntries);
        assertIterableEquals(directEntries, mergedEntries);
    }

    @Test
    void testMixedKeysPreserveOrder() throws IOException {
        var emptyBase = new ServiceConfigGuide(toStream("# empty"));
        var toMerge = new ServiceConfigGuide(toStream("KEY_A = a1", "KEY_B = b1", "KEY_A = a2", "KEY_B = b2", "KEY_A = a3"));
        emptyBase.merge(toMerge);

        assertIterableEquals(List.of("a3", "a2", "a1"), emptyBase.findEntries("KEY_A"));
        assertIterableEquals(List.of("b2", "b1"), emptyBase.findEntries("KEY_B"));
    }

    @Test
    void testMultiMerge() throws IOException {
        var layer1 = new ServiceConfigGuide(toStream("KEY_A = baseA", "KEY_B = baseB"));
        var layer2 = new ServiceConfigGuide(toStream("KEY_A = overrideA1", "KEY_C = c1"));
        var layer3 = new ServiceConfigGuide(toStream("KEY_A = overrideA2", "KEY_B = overrideB1"));

        layer1.merge(layer2);
        layer1.merge(layer3);

        assertIterableEquals(List.of("overrideA2", "overrideA1", "baseA"), layer1.findEntries("KEY_A"));
        assertIterableEquals(List.of("overrideB1", "baseB"), layer1.findEntries("KEY_B"));
        assertIterableEquals(List.of("c1"), layer1.findEntries("KEY_C"));
    }

    @Test
    void testMultiMerge2() throws IOException {
        String root = "COMMON_KEY = rootValue";
        var leftGuide = new ServiceConfigGuide(toStream(root));
        leftGuide.merge(new ServiceConfigGuide(toStream("LEFT_KEY = leftValue", "COMMON_KEY = leftOverride")));

        var rightGuide = new ServiceConfigGuide(toStream(root));
        rightGuide.merge(new ServiceConfigGuide(toStream("RIGHT_KEY = rightValue", "COMMON_KEY = rightOverride")));

        var masterGuide = new ServiceConfigGuide(toStream("# Empty Master"));
        masterGuide.merge(leftGuide);
        masterGuide.merge(rightGuide);

        List<String> commonEntries = masterGuide.findEntries("COMMON_KEY");

        assertTrue(commonEntries.contains("rightOverride"));
        assertTrue(commonEntries.contains("leftOverride"));
        assertEquals("rootValue", commonEntries.get(0));
    }

    @Test
    void testMultiFlavorMix() throws IOException {
        var configG = new ServiceConfigGuide(toStream("APP_NAME = Emissary", "DB_MAX_CONNECTIONS = 20", "LOG_LEVEL = INFO", "FEATURE_FLAGS = auth-v2",
                "FEATURE_FLAGS = coalesce", "ROUTING_ENDPOINTS = http://emissary/route"));
        configG.merge(new ServiceConfigGuide(toStream("DB_MAX_CONNECTIONS = 100", "CLOUD_PROVIDER = AWS", "STORAGE_BUCKET = s3://emissary",
                "ROUTING_ENDPOINTS = http://aws.emissary/route")));
        configG.merge(
                new ServiceConfigGuide(toStream("OBJECT_TRACE = off", "LOG_LEVEL = WARN", "DATA_AGE = \"7d\"", "STORAGE_BUCKET = s3://emissary2")));
        configG.merge(
                new ServiceConfigGuide(toStream("DB_MAX_CONNECTIONS = 500", "DEPLOY_ENV = PROD", "FEATURE_FLAGS = push", "FEATURE_FLAGS = tls")));
        configG.merge(new ServiceConfigGuide(toStream("LOG_LEVEL = DEBUG", "FEATURE_FLAGS = test")));

        assertIterableEquals(List.of("DEBUG", "WARN", "INFO"), configG.findEntries("LOG_LEVEL"));
        assertIterableEquals(List.of("500", "100", "20"), configG.findEntries("DB_MAX_CONNECTIONS"));
        assertIterableEquals(List.of("test", "tls", "push", "auth-v2", "coalesce"), configG.findEntries("FEATURE_FLAGS"));
        assertIterableEquals(List.of("s3://emissary2", "s3://emissary"), configG.findEntries("STORAGE_BUCKET"));

        assertEquals("Emissary", configG.findEntries("APP_NAME").get(0));
        assertEquals("7d", configG.findEntries("DATA_AGE").get(0));
        assertEquals("off", configG.findEntries("OBJECT_TRACE").get(0));
    }

    @Test
    void testMultiFlavorWithCancellations() throws IOException {
        var configG = new ServiceConfigGuide(toStream("LOG_LEVEL = INFO", "FEATURE_FLAGS = legacy-auth", "FEATURE_FLAGS = coalesce",
                "ROUTING_ENDPOINTS = http://emissary/route1", "ROUTING_ENDPOINTS = http://emissary/route2", "TIMEOUT = 5000"));
        configG.merge(new ServiceConfigGuide(toStream("CLOUD_PROVIDER = AWS", "ROUTING_ENDPOINTS = http://aws.emissary/route1", "TIMEOUT = 3000")));

        assertIterableEquals(List.of("http://aws.emissary/route1", "http://emissary/route1", "http://emissary/route2"),
                configG.findEntries("ROUTING_ENDPOINTS"));
        assertIterableEquals(List.of("3000", "5000"), configG.findEntries("TIMEOUT"));

        configG.merge(new ServiceConfigGuide(toStream("OBJECT_TRACE = off", "ROUTING_ENDPOINTS != \"*\"", "ROUTING_ENDPOINTS = http://emissary/v1",
                "FEATURE_FLAGS != \"*\"", "FEATURE_FLAGS = tls")));
        assertIterableEquals(List.of("http://emissary/v1"), configG.findEntries("ROUTING_ENDPOINTS"));
        assertIterableEquals(List.of("tls"), configG.findEntries("FEATURE_FLAGS"));

        configG.merge(new ServiceConfigGuide(toStream("DEPLOY_ENV = PROD", "FEATURE_FLAGS = push", "TIMEOUT = 2500")));
        assertIterableEquals(List.of("push", "tls"), configG.findEntries("FEATURE_FLAGS"));
        assertIterableEquals(List.of("2500", "3000", "5000"), configG.findEntries("TIMEOUT"));

        configG.merge(new ServiceConfigGuide(toStream("LOG_LEVEL = DEBUG", "TIMEOUT != \"*\"", "FEATURE_FLAGS = debug")));
        assertEquals(1, configG.findEntries("ROUTING_ENDPOINTS").size());

        assertIterableEquals(List.of("debug", "push", "tls"), configG.findEntries("FEATURE_FLAGS"));
        assertTrue(configG.findEntries("TIMEOUT").isEmpty());
        assertEquals("DEBUG", configG.findEntries("LOG_LEVEL").get(0));
        assertEquals("off", configG.findEntries("OBJECT_TRACE").get(0));
    }

    @Test
    void testFlavorBlankOverride() {
        assertDoesNotThrow(() -> new ServiceConfigGuide(toStream("FEATURE_FLAGS = flagA", "FEATURE_FLAGS = flagB")));
        assertThrows(IOException.class, () -> new ServiceConfigGuide(toStream("FEATURE_FLAGS = ")));
    }

    @Test
    void testSelfMerge() throws IOException {
        var guide = new ServiceConfigGuide(toStream(KEY + " = selfValue", "COUNTER = 1"));
        assertDoesNotThrow(() -> guide.merge(guide));
        assertNotNull(guide.findEntries(KEY));
    }

    @Test
    void testSpecialCharacterKeysAndFormatting() throws IOException {
        var base = new ServiceConfigGuide(toStream("# Initialization"));
        var target = new ServiceConfigGuide(toStream(
                "KEY\\=WITH\\=EQUALS = value_containing_\\=equals",
                "  SPACED_KEY   =   spaced_value  ",
                "COMMENT_LIKE_KEY = value # not a comment",
                "COLON_KEY : colon_value"));
        base.merge(target);

        assertFalse(base.findEntries("SPACED_KEY").isEmpty());
        assertIterableEquals(List.of("value"), base.findEntries("COMMENT_LIKE_KEY"));
    }

    @Test
    void testMalformedOrEmptyKeyValuePairs() {
        String malformedConfig = " = anonymousValue\nEMPTY_VAL_KEY =\nBAD_LINE_NO_DELIMITER\n" + KEY + " = validPostMalformed";
        assertThrows(IOException.class, () -> new ServiceConfigGuide(toStream(malformedConfig)));
    }

    @Test
    void testCancellationDropsPreviousEntries() throws IOException {
        var baseGuide = new ServiceConfigGuide(toStream(KEY + " = valueA", KEY + " = valueB", "KEEP_KEY = keepMe"));
        var cancelGuide = new ServiceConfigGuide(toStream(KEY + " != \"*\"", KEY + " = newValue"));

        baseGuide.merge(cancelGuide);

        assertIterableEquals(List.of("newValue"), baseGuide.findEntries(KEY));
        assertIterableEquals(List.of("keepMe"), baseGuide.findEntries("KEEP_KEY"));
    }

    @Test
    void testCancellation() throws IOException {
        var baseGuide = new ServiceConfigGuide(toStream("KEEP_KEY = clearSailing"));
        var cancelGuide = new ServiceConfigGuide(toStream("UNKNOWN_KEY != \"*\""));

        assertDoesNotThrow(() -> baseGuide.merge(cancelGuide));
        assertTrue(baseGuide.findEntries("UNKNOWN_KEY").isEmpty());
    }
}
