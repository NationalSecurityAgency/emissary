package emissary.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import emissary.test.core.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StageTest extends UnitTest {
    Stage stages;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        stages = new Stage();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        stages = null;
    }

    @Test
    void testIsParallelStage() {
        assertTrue(stages.isParallelStage("ANALYZE"), "Analyze must be a parallel stage");
    }

    @Test
    void testIsParallelIllegalStage() {
        assertFalse(stages.isParallelStage("FOO"), "Illegal stage is not parallel");
    }

    @Test
    void testIsParallelStageByInt() {
        int i = stages.getStageIndex("ANALYZE");
        assertTrue(stages.isParallelStage(i), "Analyze must be parallel by int");
    }

    @Test
    void testIsParallelIllegalByInt() {
        assertFalse(stages.isParallelStage(55), "Illegal stage by int is not parallel");
    }

    @Test
    void testNameMapping() {
        assertEquals("ANALYZE", stages.getStageName(stages.getStageIndex("ANALYZE")), "Name to int to name mapping");
    }

    @Test
    void testIllegalNameMapping() {
        assertEquals("UNDEFINED", stages.getStageName(-1), "Illegal name mapping");
    }

    @Test
    void testStageProgression() {
        List<String> list = stages.getStages();
        list.add(null);
        for (int i = 0; i < list.size() - 1; i++) {
            String s = list.get(i);
            assertEquals(list.get(i + 1), stages.nextStageAfter(s), "Next stage progression from " + s);
        }
    }

    @Test
    void testIllegalProgression() {
        assertNull(stages.nextStageAfter("FOO"), "No stage after illegal stage");
    }
}
