package emissary.util.search;

import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class FastBoyerMooreTest extends UnitTest {
    private final String[][] keywords = {{"one", "two", "three", "four", "five"}, {"uno", "dos", "tres", "quatro", "cinco"},
            {"alpha", "beta", "gamma", "delta", "epsilon"}};

    @Test
    void testSimpleScan() {
        try {
            final FastBoyerMoore scanner = new FastBoyerMoore(this.keywords);
            final byte[] data =
                    ("Hi, this is a one-two-cinco test of the emergency alpha five gamma\nbroadcasting system. \n\n"
                            + "If this were a real emergency epsilon (delta) alpha you would four dos tres get the heck out of here.").getBytes();

            int pos = 0;
            final List<int[]> result = new ArrayList<>();
            while (pos < data.length) {
                pos = scanner.staticSingleScan(data, pos, data.length, result);
            }
            assertEquals(12, result.size(), "All results must be found and reported out");
            assertEquals(14, result.get(0)[0], "First result check pos");
            assertEquals(0, result.get(0)[1], "First result check id");
            assertEquals(3, result.get(0)[2], "First result check length");
        } catch (Exception ex) {
            fail("Creation of scanner failure", ex);
        }
    }

}
