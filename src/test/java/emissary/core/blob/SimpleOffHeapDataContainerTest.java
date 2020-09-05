package emissary.core.blob;

import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import org.junit.Assert;
import org.junit.Test;

public class SimpleOffHeapDataContainerTest {

    @Test
    public void testAllocateSizeGreaterThanJvmHeap() throws Exception {
        SimpleOffHeapMemoryDataContainer sohmdc = new SimpleOffHeapMemoryDataContainer();
        // Tests are rarely run in a 2GB container
        try (SeekableByteChannel channel = sohmdc.newChannel(Integer.MAX_VALUE + 1L)) {
            Assert.assertEquals(0, channel.size());
            long written = 0;
            ByteBuffer zeros = ByteBuffer.wrap(new byte[1024]);
            while (written < Integer.MAX_VALUE + 1L) {
                written += channel.write(zeros);
                zeros.rewind();
            }
            Assert.assertTrue("Greater than 2GB should have been written", channel.size() > Integer.MAX_VALUE);
        }
    }

    @Test
    public void testFastChannelGrowth() throws Exception {
        SimpleOffHeapMemoryDataContainer sohmdc = new SimpleOffHeapMemoryDataContainer();
        try (SeekableByteChannel channel = sohmdc.newChannel(10)) {
            ByteBuffer bytes = ByteBuffer.wrap(new byte[1000]);
            channel.write(bytes);
            Assert.assertEquals(1000, channel.size());
        }
    }
}
