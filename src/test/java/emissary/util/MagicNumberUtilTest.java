package emissary.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import emissary.test.core.UnitTest;
import org.junit.jupiter.api.Test;

class MagicNumberUtilTest extends UnitTest {
    @Test
    void testMultipleFileLoads() throws IOException {
        MagicNumberUtil m = new MagicNumberUtil();
        Path f1 = Files.createTempFile("magic", ".dat");
        Path f2 = Files.createTempFile("magic", ".dat");
        try {
            try (OutputStream o1 = Files.newOutputStream(f1)) {
                o1.write("0  string  pattern1  P1".getBytes());
            }
            try (OutputStream o2 = Files.newOutputStream(f2)) {
                o2.write("0  string  pattern2  P2".getBytes());
            }
            m.load(f1.toFile());
            m.load(f2.toFile());
            assertEquals(2, m.size(), "Rules from both files must load");
        } finally {
            Files.deleteIfExists(f1);
            Files.deleteIfExists(f2);
        }
    }

    @Test
    void testMultipleByteLoads() {
        MagicNumberUtil m = new MagicNumberUtil();
        m.load("0 string pattern1 S1".getBytes());
        m.load("0 string pattern2 S2".getBytes());
        assertEquals(2, m.size(), "Rules from both files must load");
    }
}
