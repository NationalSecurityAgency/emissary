package emissary.util.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class FastBoyerMooreTest extends UnitTest {
    private final String[][] keywords = {{"one", "two", "three", "four", "five"}, {"uno", "dos", "tres", "quatro", "cinco"},
            {"alpha", "beta", "gamma", "delta", "epsilon"}};

    @Test
    public void testSimpleScan() {
        try {
            final FastBoyerMoore scanner = new FastBoyerMoore(this.keywords);
            final byte[] data =
                    ("Hi, this is a one-two-cinco test of the emergency alpha five gamma\nbroadcasting system. \n\n"
                            + "If this were a real emergency epsilon (delta) alpha you would four dos tres get the heck out of here.").getBytes();

            int pos = 0;
            final List<int[]> result = new ArrayList<int[]>();
            while (pos < data.length) {
                pos = scanner.staticSingleScan(data, pos, data.length, result);
            }
            assertEquals("All results must be found and reported out", 12, result.size());
            assertEquals("First result check pos", 14, result.get(0)[0]);
            assertEquals("First result check id", 0, result.get(0)[1]);
            assertEquals("First result check length", 3, result.get(0)[2]);
        } catch (Exception ex) {
            fail("Creation of scanner failure: " + ex);
        }
    }

}
