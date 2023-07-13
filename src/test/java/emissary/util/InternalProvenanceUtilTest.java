package emissary.util;

import emissary.test.core.UnitTest;

import org.junit.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InternalProvenanceUtilTest extends UnitTest {

    @Test
    public void testCreatePickupMessageMap() {
        String dummyFilename = "test-file.extension";
        Map<String, String> messageMap = InternalProvenanceUtil.createPickupMessageMap(dummyFilename);
        assertEquals(2, messageMap.size());
        assertTrue(messageMap.containsKey("stage"));
        assertTrue(messageMap.containsKey("inputFilename"));
        assertEquals(dummyFilename, messageMap.get("inputFilename"));
    }
}
