package emissary.output.roller.coalesce;

import static emissary.output.roller.journal.JournaledChannelPool.EXTENSION;
import static emissary.util.io.ListOpenFilesTest.testForLsof;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import emissary.output.roller.journal.Journal;
import emissary.output.roller.journal.JournaledChannelPool;
import emissary.output.roller.journal.KeyedOutput;
import org.junit.Test;

public class LsofCoalescerTest extends MaxAttemptCoalescerTest {

    protected Coalescer getCoalescer() throws IOException {
        return new LsofCoalescer(targetPath);
    }

    protected void coalesce() throws IOException {
        coalescer.coalesce();
    }

    @Test
    public void testMaxAttemptCoalesce() throws Exception {
        testForLsof();
        super.testMaxAttemptCoalesce();
    }

    @Test
    public void testCoalesceCannotReadAttemptFile1() throws Exception {
        testForLsof();
        super.testCoalesceCannotReadAttemptFile1();
    }

    @Test
    public void testCoalesceCannotReadAttemptFile2() throws Exception {
        testForLsof();
        super.testCoalesceCannotReadAttemptFile2();
    }

    @Test
    public void testCoalesceTooManyAttempts() throws Exception {
        testForLsof();
        super.testCoalesceTooManyAttempts();
    }

    @Test
    public void testCleanupEmptyPartFiles() throws IOException {
        testForLsof();
        String key = "emptyFile";
        Path empty = Paths.get(targetPath.toString(), key + EXTENSION);
        Files.createFile(empty);
        coalesce();
        assertFalse(Files.exists(empty));
        assertFalse(Files.exists(Paths.get(targetPath.toString(), key)));
    }

    @Test
    public void testNoFilesReady() throws Exception {
        try (JournaledChannelPool pool = new JournaledChannelPool(targetPath, temp1Filename, 2);
             KeyedOutput one = pool.getFree();
             KeyedOutput two = pool.getFree()) {
            Files.copy(temp1, one);
            Files.copy(temp2, two);
            one.commit();
            two.commit();

            coalesce();

            Path destination = targetPath.resolve(temp1Filename);
            assertFalse(Files.exists(destination));
        }

        roll();
        coalesce();
        assertSuccessOutput();
    }
}
