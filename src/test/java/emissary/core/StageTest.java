package emissary.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StageTest extends UnitTest {
    Stage stages = new Stage();

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
        assertTrue(stages.getStage("ANALYZE").isParallelStage(), "Analyze must be a parallel stage");
    }

    @Test
    void testIsParallelIllegalStage() {
        assertFalse(stages.getStage("FOO").isParallelStage(), "Illegal stage is not parallel");
    }

    @Test
    void testIsParallelStageByInt() {
        int i = stages.getStage("ANALYZE").getIndex();
        assertTrue(stages.getStage(i).isParallelStage(), "Analyze must be parallel by int");
    }

    @Test
    void testIsParallelIllegalByInt() {
        assertFalse(stages.getStage(55).isParallelStage(), "Illegal stage by int is not parallel");
    }

    @Test
    void testNameMapping() {
        assertEquals("ANALYZE", stages.getStage(stages.getStage("ANALYZE").getIndex()).getStageName(), "Name to int to name mapping");
    }

    @Test
    void testIllegalNameMapping() {
        assertEquals("UNDEFINED", stages.getStage(-1).getStageName(), "Illegal name mapping");
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
