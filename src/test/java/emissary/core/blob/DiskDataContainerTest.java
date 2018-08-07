package emissary.core.blob;

import java.io.File;
import java.io.FilenameFilter;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import emissary.test.core.UnitTest;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DiskDataContainerTest extends UnitTest {

    @Test
    public void testFileCleanup() throws Exception {
        for (int i = 0; i < 10000; i++) {
            DiskDataContainer ddc = new DiskDataContainer();
            ddc.setData(new byte[10000]);
            ddc.invalidateCache();
            Assert.assertEquals(10000, ddc.data().length);
        }
        System.gc();
        Thread.sleep(15000);
        System.gc();
        final String[] remainingFiles = new File(DiskDataContainer.tempFilePath).list(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                return name.matches(DiskDataContainer.TEMP_FILE_PREFIX + ".*//.bdo");
            }
        });
        Assert.assertEquals(0, remainingFiles.length);
    }
}
