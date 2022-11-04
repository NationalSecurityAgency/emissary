package emissary.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emissary.test.core.junit5.UnitTest;
import org.junit.jupiter.api.Test;

class EntropyTest extends UnitTest {

    @Test
    void testEntropy() {
        assertTrue(
                Entropy.checkText(
                        "Now is the time for all good men to come to the aid of their countries. This is the time of greatest need and we have nothing to fear but a day which will live in Infamy."
                                .getBytes(UTF_8)),
                "Text check");
    }

    @Test
    void testNonText() {
        byte[] b = new byte[256];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) i;
        }
        assertFalse(Entropy.checkText(b), "Sequence of bytes is not text");
    }

}
