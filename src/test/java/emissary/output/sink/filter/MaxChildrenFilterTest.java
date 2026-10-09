package emissary.output.sink.filter;

import emissary.config.ServiceConfigGuide;
import emissary.core.DataObjectFactory;
import emissary.core.IBaseDataObject;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaxChildrenFilterTest extends UnitTest {

    private static MaxChildrenFilter filterWithMax(final int max) {
        ServiceConfigGuide config = new ServiceConfigGuide();
        config.addEntry(MaxChildrenFilter.MAX_CHILDREN, String.valueOf(max));
        MaxChildrenFilter f = new MaxChildrenFilter();
        f.initialize(config);
        return f;
    }

    private static IBaseDataObject payloadWithChildren(final int children) {
        IBaseDataObject p = DataObjectFactory.getInstance();
        p.setNumChildren(children);
        return p;
    }

    @Test
    void testSinglePayloadThreshold() {
        MaxChildrenFilter f = filterWithMax(3);
        assertTrue(f.accept(payloadWithChildren(3)), "at threshold should be allowed");
        assertTrue(f.accept(payloadWithChildren(1)), "below threshold should be allowed");
        assertFalse(f.accept(payloadWithChildren(4)), "above threshold should be denied");
    }

    @Test
    void testFamilyListCountsDescendants() {
        MaxChildrenFilter f = filterWithMax(2);
        List<IBaseDataObject> within = IntStream.range(0, 3).mapToObj(i -> DataObjectFactory.getInstance())
                .collect(Collectors.toList());
        assertTrue(f.accept(within), "family within threshold should be allowed");

        List<IBaseDataObject> over = IntStream.range(0, 5).mapToObj(i -> DataObjectFactory.getInstance())
                .collect(Collectors.toList());
        assertFalse(f.accept(over), "family over threshold should be denied");

        assertTrue(f.accept(Collections.emptyList()), "empty list should be allowed");
    }

    @Test
    void testUnconfiguredAllowsEverything() {
        MaxChildrenFilter f = new MaxChildrenFilter();
        f.initialize(new ServiceConfigGuide());
        assertTrue(f.accept(payloadWithChildren(1000)), "no threshold configured should allow");
    }
}
