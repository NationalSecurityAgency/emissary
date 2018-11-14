package emissary.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import emissary.test.core.UnitTest;
import org.junit.Test;

public class MagicNumberUtilTest extends UnitTest {
    @Test
    public void testMultipleFileLoads() throws IOException {
        MagicNumberUtil m = new MagicNumberUtil();
        File f1 = File.createTempFile("magic", ".dat");
        File f2 = File.createTempFile("magic", ".dat");
        FileOutputStream o1 = new FileOutputStream(f1);
        o1.write("0  string  pattern1  P1".getBytes());
        o1.close();
        FileOutputStream o2 = new FileOutputStream(f2);
        o2.write("0  string  pattern2  P2".getBytes());
        o2.close();
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
