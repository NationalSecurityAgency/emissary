package emissary.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import emissary.test.core.UnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class StageTest extends UnitTest {
    Stage stages;

    @Override
    @Before
    public void setUp() throws Exception {
        stages = new Stage();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        stages = null;
    }

    @Test
    public void testIsParallelStage() {
        assertTrue("Analyze must be a parallel stage", stages.isParallelStage("ANALYZE"));
    }

    @Test
    public void testIsParallelIllegalStage() {
        assertFalse("Illegal stage is not parallel", stages.isParallelStage("FOO"));
    }

    @Test
    public void testIsParallelStageByInt() {
        int i = stages.getStageIndex("ANALYZE");
        assertTrue("Analyze must be parallel by int", stages.isParallelStage(i));
    }

    @Test
    public void testIsParallelIllegalByInt() {
        assertFalse("Illegal stage by int is not parallel", stages.isParallelStage(55));
    }

    @Test
    public void testNameMapping() {
        assertEquals("Name to int to name mapping", "ANALYZE", stages.getStageName(stages.getStageIndex("ANALYZE")));
    }

    @Test
    public void testIllegalNameMapping() {
        assertEquals("Illegal name mapping", "UNDEFINED", stages.getStageName(-1));
    }

    @Test
    public void testStageProgression() {
        List<String> list = stages.getStages();
        list.add(null);
        for (int i = 0; i < list.size() - 1; i++) {
            String s = list.get(i);
            assertEquals("Next stage progression from " + s, list.get(i + 1), stages.nextStageAfter(s));
        }
    }

    @Test
    public void testIllegalProgression() {
        assertNull("No stage after illegal stage", stages.nextStageAfter("FOO"));
    }
}
