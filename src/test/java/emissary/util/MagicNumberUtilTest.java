package emissary.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class MagicNumberUtilTest extends UnitTest {
    @Test
    public void testMultipleFileLoads() throws IOException {
        MagicNumberUtil m = new MagicNumberUtil();
        File f1 = File.createTempFile("magic", ".dat");
        File f2 = File.createTempFile("magic", ".dat");
        try (OutputStream o1 = Files.newOutputStream(f1.toPath())) {
            o1.write("0  string  pattern1  P1".getBytes());
        }
        try (OutputStream o2 = Files.newOutputStream(f2.toPath())) {
            o2.write("0  string  pattern2  P2".getBytes());
        }
        m.load(f1);
        m.load(f2);
        assertEquals("Rules from both files must load", 2, m.size());
    }

    @Test
    public void testMultipleByteLoads() throws IOException {
        MagicNumberUtil m = new MagicNumberUtil();
        m.load("0 string pattern1 S1".getBytes());
        m.load("0 string pattern2 S2".getBytes());
        assertEquals("Rules from both files must load", 2, m.size());
    }
}
