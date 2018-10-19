package emissary.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class EntropyTest extends UnitTest {

    @Test
    public void testEntropy() {
        assertTrue(
                "Text check",
                Entropy.checkText(
                        "Now is the time for all good men to come to the aid of their countries. This is the time of greatest need and we have nothing to fear but a day which will live in Infamy."
                                .getBytes()));
    }

    @Test
    public void testNonText() {
        byte[] b = new byte[256];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) i;
        }
        assertFalse("Sequence of bytes is not text", Entropy.checkText(b));
    }

}
