package emissary.core.blob;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;

@FixMethodOrder
public class SelectingDataContainerTest {

    @Test
    public void testNewChannelImplUpgrades() throws Exception {
        SelectingDataContainer sdc = new SelectingDataContainer();
        try (SeekableByteChannel channel = sdc.newChannel(0)) {
            Assert.assertEquals(MemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("M".getBytes(UTF_8)));
            Assert.assertEquals(MemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("M".getBytes(UTF_8)));
            Assert.assertEquals(MemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("M".getBytes(UTF_8)));
            Assert.assertEquals(MemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("M".getBytes(UTF_8)));
            Assert.assertEquals(MemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("M".getBytes(UTF_8)));
            Assert.assertEquals(MemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("M".getBytes(UTF_8)));
            Assert.assertEquals(MemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("O".getBytes(UTF_8)));
            Assert.assertEquals(SimpleOffHeapMemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("O".getBytes(UTF_8)));
            Assert.assertEquals(SimpleOffHeapMemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("O".getBytes(UTF_8)));
            Assert.assertEquals(SimpleOffHeapMemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("O".getBytes(UTF_8)));
            Assert.assertEquals(SimpleOffHeapMemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("O".getBytes(UTF_8)));
            Assert.assertEquals(SimpleOffHeapMemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("O".getBytes(UTF_8)));
            Assert.assertEquals(SimpleOffHeapMemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("O".getBytes(UTF_8)));
            Assert.assertEquals(SimpleOffHeapMemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("O".getBytes(UTF_8)));
            Assert.assertEquals(SimpleOffHeapMemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("D".getBytes(UTF_8)));
            Assert.assertEquals(DiskDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("D".getBytes(UTF_8)));
            Assert.assertEquals(DiskDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("D".getBytes(UTF_8)));
            Assert.assertEquals(DiskDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("D".getBytes(UTF_8)));
            Assert.assertEquals(DiskDataContainer.class, sdc.getActualContainer().getClass());
        }

        Assert.assertEquals("MMMMMMOOOOOOOODDDD", new String(sdc.data(), UTF_8));
    }

    @Test
    public void testAppendChannelImplUpgrades() throws Exception {
        SelectingDataContainer sdc = new SelectingDataContainer();
        sdc.setData("ツ".getBytes(UTF_8));
        try (SeekableByteChannel channel = sdc.channel()) {
            channel.position(channel.size());
            Assert.assertEquals(MemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("ツ".getBytes(UTF_8)));
            Assert.assertEquals(MemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("ば".getBytes(UTF_8)));
            Assert.assertEquals(SimpleOffHeapMemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("ば".getBytes(UTF_8)));
            Assert.assertEquals(SimpleOffHeapMemoryDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("か".getBytes(UTF_8)));
            Assert.assertEquals(DiskDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("か".getBytes(UTF_8)));
            Assert.assertEquals(DiskDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("か".getBytes(UTF_8)));
            Assert.assertEquals(DiskDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("か".getBytes(UTF_8)));
            Assert.assertEquals(DiskDataContainer.class, sdc.getActualContainer().getClass());
        }

        Assert.assertEquals("ツツばばかかかか", new String(sdc.data(), UTF_8));
    }

    @Test
    public void testNewChannelEstimateNotIgnored() throws Exception {
        SelectingDataContainer sdc = new SelectingDataContainer();
        try (SeekableByteChannel channel = sdc.newChannel(100)) {
            channel.position(channel.size());
            Assert.assertEquals(DiskDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("ツ".getBytes(UTF_8)));
            Assert.assertEquals(DiskDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("ば".getBytes(UTF_8)));
            Assert.assertEquals(DiskDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("ば".getBytes(UTF_8)));
            Assert.assertEquals(DiskDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("か".getBytes(UTF_8)));
            Assert.assertEquals(DiskDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("か".getBytes(UTF_8)));
            Assert.assertEquals(DiskDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("か".getBytes(UTF_8)));
            Assert.assertEquals(DiskDataContainer.class, sdc.getActualContainer().getClass());
            channel.write(ByteBuffer.wrap("か".getBytes(UTF_8)));
            Assert.assertEquals(DiskDataContainer.class, sdc.getActualContainer().getClass());
        }

        Assert.assertEquals("ツばばかかかか", new String(sdc.data(), UTF_8));
    }

    @Test(expected = DataException.class)
    public void testMaximumExceededSet() throws Exception {
        SelectingDataContainer sdc = new SelectingDataContainer();
        sdc.setData(new byte[30000001]);
    }

    @Test(expected = DataException.class)
    public void testMaximumExceededNewChannel() throws Exception {
        SelectingDataContainer sdc = new SelectingDataContainer();
        try (SeekableByteChannel channel = sdc.newChannel(30000001)) {
            Assert.fail("Maximum should be exceeded.");
        }
    }

    @Test(expected = DataException.class)
    public void testMaximumExceededChannelGrowth() throws Exception {
        SelectingDataContainer sdc = new SelectingDataContainer();
        try (SeekableByteChannel channel = sdc.newChannel(10)) {
            ByteBuffer bytes = ByteBuffer.wrap(new byte[1000]);
            for (int i = 0; i < 30000000; i += 1) {
                channel.write(bytes);
                bytes.rewind();
            }
        }
    }

    @Test(expected = DataException.class)
    public void testMaximumArraySizeExceeded() throws Exception {
        SelectingDataContainer sdc = new SelectingDataContainer();
        sdc.setData(new byte[750001]);
        sdc.data();
    }
}
