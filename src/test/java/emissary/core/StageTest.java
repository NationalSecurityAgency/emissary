package emissary.core;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageTest extends UnitTest {

    @Test
    void testIsParallelStage() {
        assertTrue(Stage.ANALYZE.isParallel(), "Analyze must be a parallel stage");
    }

    @Test
    @SuppressWarnings("EnumOrdinal")
    void testIsParallelStageByInt() {
        int i = Stage.ANALYZE.ordinal();
        assertTrue(Stage.isParallelStage(i), "Analyze must be parallel by int");
    }

    @Test
    void testIsParallelIllegalByInt() {
        assertFalse(Stage.isParallelStage(55), "Illegal stage by int is not parallel");
    }

    @Test
    void testNameMapping() {
        assertEquals("ANALYZE", Stage.ANALYZE.name(), "Name to int to name mapping");
    }

    @Test
    void testIllegalNameMapping() {
        assertEquals("UNDEFINED", Stage.getStageName(-1), "Illegal name mapping");
    }

    @Test
    void testGetByNameNull() {
        assertNull(Stage.getByName(null));
    }

    @Test
    void testStageProgression() {
        List<Stage> list = Arrays.asList(Stage.values());
        Stage stage;
        for (int i = 0; i < list.size() - 2; i++) {
            stage = list.get(i);
            assertEquals(list.get(i + 1), Stage.nextStageAfter(stage), "Next stage progression from " + stage);
        }
        stage = list.get(list.size() - 1);
        assertNull(Stage.nextStageAfter(stage), "Next stage progression from " + stage);
    }

    @Test
    void testIllegalProgression() {
        assertNull(Stage.nextStageAfter("FOO"), "No stage after illegal stage");
    }
}
