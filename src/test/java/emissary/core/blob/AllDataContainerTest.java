package emissary.core.blob;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.commons.lang.SerializationUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import emissary.core.blob.IDataContainer.LegacyContainerWrapper;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized.class)
public class AllDataContainerTest {

    @Parameters(name = "{index}: {0} cache allowed: {1}")
    public static Iterable<Object[]> getClassesToTest() {
        return Arrays.asList(new Object[][] {
                {MemoryDataContainer.class, true},
                {MemoryDataContainer.class, false},
                {SimpleOffHeapMemoryDataContainer.class, true},
                {SimpleOffHeapMemoryDataContainer.class, false},
                {DiskDataContainer.class, true},
                {DiskDataContainer.class, false},
                {SelectingDataContainer.class, true},
                {SelectingDataContainer.class, false},
                {LegacyContainerWrapper.class, true},
                {LegacyContainerWrapper.class, false}

        });
    }

    private Class<? extends IDataContainer> classToTest;
    private boolean allowCache;
    private IDataContainer cont;

    public AllDataContainerTest(Class<? extends IDataContainer> classToTest, boolean allowCache) throws Exception {
        this.classToTest = classToTest;
        this.allowCache = allowCache;
    }

    @Before
    public void setup() throws Exception {
        if (classToTest == LegacyContainerWrapper.class) {
            cont = IDataContainer.wrap(new MemoryDataContainer());
        } else {
            cont = classToTest.newInstance();
        }
    }

    @Test
    public void happyWorldUsingArrays() throws Exception {
        cont.setData("Base Test".getBytes(StandardCharsets.UTF_8));
        if (!allowCache) {
            cont.invalidateCache();
        }
        Assert.assertEquals("Base Test", new String(cont.data(), StandardCharsets.UTF_8));
    }

    @Test
    // @Ignore("Contract of ByteBuffer not explicitly defined")
    public void testMutationViaBufferPossible() throws Exception {
        cont.setData("Base Test".getBytes(StandardCharsets.UTF_8));

        if (!allowCache) {
            cont.invalidateCache();
        }

        ByteBuffer dataBuffer = cont.dataBuffer();
        dataBuffer.position(0);
        dataBuffer.put("Overwrite".getBytes(StandardCharsets.UTF_8));
        if (dataBuffer instanceof MappedByteBuffer) {
            try {
                ((MappedByteBuffer) dataBuffer).force();
            } catch (UnsupportedOperationException e) {
                // Weird class hierarchy, no issue
            }
            dataBuffer = null;
            System.gc();
        }

        Assert.assertNotEquals("Base Test", new String(cont.data(), StandardCharsets.UTF_8));

    }

    @Test
    public void testChannelWriteUsingNewChannel() throws Exception {
        try (SeekableByteChannel channel = cont.newChannel(100)) {
            channel.position(channel.size());
            channel.write(ByteBuffer.wrap("testWriteUsingNewChannel".getBytes(StandardCharsets.UTF_8)));
        }
        if (!allowCache) {
            cont.invalidateCache();
        }

        Assert.assertEquals("testWriteUsingNewChannel", new String(cont.data(), StandardCharsets.UTF_8));
    }

    @Test
    public void testChannelOverwriteUsingNewChannel() throws Exception {
        cont.setData("Old data".getBytes(StandardCharsets.UTF_8));
        if (!allowCache) {
            cont.invalidateCache();
        }

        try (SeekableByteChannel channel = cont.newChannel(100)) {
            channel.position(channel.size());
            channel.write(ByteBuffer.wrap("testOverwriteUsingNewChannel".getBytes(StandardCharsets.UTF_8)));
        }

        Assert.assertEquals("testOverwriteUsingNewChannel", new String(cont.data(), StandardCharsets.UTF_8));
    }

    @Test
    public void testChannelAppendToEndOfChannel() throws Exception {
        cont.setData("testAppendToEndOfChannel".getBytes(StandardCharsets.UTF_8));
        if (!allowCache) {
            cont.invalidateCache();
        }

        try (SeekableByteChannel channel = cont.channel()) {
            channel.position(channel.size());
            channel.write(ByteBuffer.wrap(" with append".getBytes(StandardCharsets.UTF_8)));
        }

        Assert.assertEquals("testAppendToEndOfChannel with append", new String(cont.data(), StandardCharsets.UTF_8));
    }

    @Test
    public void testChannelAppendToUnInitialisedChannel() throws Exception {
        try (SeekableByteChannel channel = cont.channel()) {
            channel.position(channel.size());
            channel.write(ByteBuffer.wrap("testAppendToUnInitialisedChannel".getBytes(StandardCharsets.UTF_8)));
        }

        Assert.assertEquals("testAppendToUnInitialisedChannel", new String(cont.data(), StandardCharsets.UTF_8));
    }

    @Test
    public void testChannelWriteBeyondNewChannel() throws Exception {
        try (SeekableByteChannel channel = cont.newChannel(100)) {
            channel.position(10);
            channel.write(ByteBuffer.wrap("testWriteBeyondNewChannel".getBytes(UTF_8)));
        }
        if (!allowCache) {
            cont.invalidateCache();
        }

        byte[] data = cont.data();
        Assert.assertEquals("testWriteBeyondNewChannel", new String(data, 10, data.length - 10, UTF_8));
    }

    @Test
    public void testChannelOverwriteBeyondNewChannel() throws Exception {
        cont.setData("Old data".getBytes(StandardCharsets.UTF_8));
        if (!allowCache) {
            cont.invalidateCache();
        }

        try (SeekableByteChannel channel = cont.newChannel(100)) {
            channel.position(12);
            channel.write(ByteBuffer.wrap("testOverwriteBeyondNewChannel".getBytes(UTF_8)));
        }

        byte[] data = cont.data();
        Assert.assertEquals("testOverwriteBeyondNewChannel", new String(data, 12, data.length - 12, UTF_8));
    }

    @Test
    public void testChannelAppendBeyondEndOfChannel() throws Exception {
        cont.setData("testAppendBeyondEndOfChannel".getBytes(UTF_8));
        if (!allowCache) {
            cont.invalidateCache();
        }

        try (SeekableByteChannel channel = cont.channel()) {
            channel.position(channel.size() + 30);
            channel.write(ByteBuffer.wrap(" with ツ append".getBytes(StandardCharsets.UTF_8)));
        }

        byte[] data = cont.data();
        Assert.assertEquals("testAppendBeyondEndOfChannel", new String(data, 0, 28, UTF_8));
        Assert.assertEquals(" with ツ append", new String(data, 58, data.length - 58, UTF_8));
    }

    @Test
    public void testChannelAppendBeyondUnInitialisedChannel() throws Exception {
        try (SeekableByteChannel channel = cont.channel()) {
            channel.position(channel.size() + 13);
            channel.write(ByteBuffer.wrap("testAppendBeyondUnInitialisedChannel".getBytes(UTF_8)));
        }

        byte[] data = cont.data();
        Assert.assertEquals("testAppendBeyondUnInitialisedChannel", new String(data, 13, data.length - 13, UTF_8));
    }

    @Test
    public void testChannelTruncateSimple() throws Exception {
        cont.setData("testChannelTruncateSimple".getBytes(UTF_8));
        try (SeekableByteChannel channel = cont.channel()) {
            channel.truncate(10);
        }

        Assert.assertEquals(10, cont.length());
        Assert.assertEquals(10, cont.data().length);
        Assert.assertEquals("testChanne", new String(cont.data(), StandardCharsets.UTF_8));
    }

    @Test
    public void testChannelTruncateThenAppend() throws Exception {
        cont.setData("testChannelTruncateSimple".getBytes(UTF_8));
        try (SeekableByteChannel channel = cont.channel()) {
            channel.position(channel.size());
            channel.truncate(11);
            channel.write(ByteBuffer.wrap("Replacement".getBytes(UTF_8)));
        }

        Assert.assertEquals("testChannelReplacement", new String(cont.data(), StandardCharsets.UTF_8));
    }

    @Test
    public void testChannelTruncateBeyondUnInitialisedChannel() throws Exception {
        try (SeekableByteChannel channel = cont.channel()) {
            channel.truncate(10);
        }

        Assert.assertEquals(0, cont.length());
        Assert.assertEquals(0, cont.data().length);
    }

    @Test
    public void testChannelTruncateNewChannel() throws Exception {
        try (SeekableByteChannel channel = cont.newChannel(100)) {
            channel.truncate(10);
        }

        Assert.assertEquals(0, cont.length());
        Assert.assertEquals(0, cont.data().length);
    }

    @Test
    public void testDataContainerLength() {
        cont.setData("This is a test".getBytes(StandardCharsets.UTF_8));
        if (!allowCache) {
            cont.invalidateCache();
        }
        assertEquals("Data length", "This is a test".length(), cont.length());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testNullDataLength() {
        cont.setData(null);
        if (!allowCache) {
            cont.invalidateCache();
        }
        assertEquals("Null data length", 0, cont.dataLength());
    }

    @Test
    public void testNullLength() {
        cont.setData(null);
        if (!allowCache) {
            cont.invalidateCache();
        }
        assertEquals("Null data length", 0, cont.length());
    }

    @Test
    public void testZeroLengthDataSlice() {
        final byte[] ary = new byte[0];
        cont.setData(ary, 0, 0);
        if (!allowCache) {
            cont.invalidateCache();
        }
        assertEquals("Null data length is zero", 0, cont.length());
    }

    @Test
    public void testDataSliceLength() {
        final byte[] ary = "abcdefghijk".getBytes();
        cont.setData(ary, 3, 4);
        if (!allowCache) {
            cont.invalidateCache();
        }
        assertEquals("Array slice must use length", 4, cont.length());
    }

    @Test
    public void testDataSliceData() {
        final byte[] ary = "abcdefghijk".getBytes();
        cont.setData(ary, 3, 4);
        if (!allowCache) {
            cont.invalidateCache();
        }
        assertEquals("Array slice must use proper data", "defg", new String(cont.data()));
    }

    @Test
    public void testNullData() {
        cont.setData(null);
        if (!allowCache) {
            cont.invalidateCache();
        }
        assertNotNull("Data array can never be null", cont.data());
    }

    @Test
    public void testNullDataOverwrite() throws Exception {
        happyWorldUsingArrays();
        cont.setData(null);
        if (!allowCache) {
            cont.invalidateCache();
        }
        assertNotNull("Data array can never be null", cont.data());
    }

    @Test
    public void testNullDataSlice() {
        cont.setData(null, 3, 4);
        if (!allowCache) {
            cont.invalidateCache();
        }
        assertNotNull("Data slice can never be null", cont.data());
    }

    @Test
    public void testBufferSimple() {
        cont.setData("testBufferSimple".getBytes(UTF_8));
        if (!allowCache) {
            cont.invalidateCache();
        }
        ByteBuffer buf = cont.dataBuffer();
        byte[] output = new byte[buf.limit()];
        buf.get(output);
        Assert.assertEquals("testBufferSimple", new String(output, UTF_8));
    }

    @Test
    public void testBufferUninitialised() {
        if (!allowCache) {
            cont.invalidateCache();
        }
        ByteBuffer buf = cont.dataBuffer();
        byte[] output = new byte[buf.limit()];
        buf.get(output);
        Assert.assertEquals("", new String(output, UTF_8));
    }

    @Test
    public void testClone() throws Exception {
        try {
            cont.setData("Base Data".getBytes());
            if (!allowCache) {
                cont.invalidateCache();
            }
            final IDataContainer clone = cont.clone();

            try (SeekableByteChannel channel = cont.channel()) {
                channel.position(channel.size());
                channel.write(ByteBuffer.wrap(" with append".getBytes(UTF_8)));
            }

            Assert.assertFalse(Arrays.equals(cont.data(), clone.data()));
            Assert.assertEquals("Base Data with append", new String(cont.data(), UTF_8));
            Assert.assertEquals("Base Data", new String(clone.data(), UTF_8));
        } catch (CloneNotSupportedException ex) {
            fail("Clone must be supported on IDataContainer");
        }
    }

    @Test
    public void testCloneUninitialisedData() throws Exception {
        try {
            if (!allowCache) {
                cont.invalidateCache();
            }
            final IDataContainer clone = cont.clone();

            try (SeekableByteChannel channel = cont.channel()) {
                channel.position(channel.size());
                channel.write(ByteBuffer.wrap(" with append".getBytes(UTF_8)));
            }

            Assert.assertFalse(Arrays.equals(cont.data(), clone.data()));
            Assert.assertEquals(" with append", new String(cont.data(), UTF_8));
            Assert.assertEquals("", new String(clone.data(), UTF_8));
        } catch (CloneNotSupportedException ex) {
            fail("Clone must be supported on IDataContainer");
        }
    }

    @Test
    public void testSerialization() throws Exception {
        String data = "Some data with interesting characters (ツ) to test serialization doesn't break encoding.";
        cont.setData(data.getBytes(StandardCharsets.UTF_8));
        if (!allowCache) {
            cont.invalidateCache();
        }

        byte[] serialized = SerializationUtils.serialize(cont);

        cont = null;
        IDataContainer deserialized = (IDataContainer) SerializationUtils.deserialize(serialized);
        Assert.assertEquals(data, new String(deserialized.data(), StandardCharsets.UTF_8));
    }

    @Test
    public void testSerializationMultipleObjects() throws Exception {
        String data = "Some data with interesting characters (ツ) to test serialization doesn't break encoding.";
        cont.setData(data.getBytes(StandardCharsets.UTF_8));
        if (!allowCache) {
            cont.invalidateCache();
        }

        byte[] serialized = new byte[0];
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            SerializationUtils.serialize(cont, baos);
            SerializationUtils.serialize(cont, baos);
            serialized = baos.toByteArray();
        }

        cont = null;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(serialized)) {
            IDataContainer deserialized = (IDataContainer) SerializationUtils.deserialize(bais);
            Assert.assertEquals(data, new String(deserialized.data(), StandardCharsets.UTF_8));
            deserialized = (IDataContainer) SerializationUtils.deserialize(bais);
            Assert.assertEquals(data, new String(deserialized.data(), UTF_8));
        }
    }

    @Test
    public void testDirectFileInteractionAppend() throws Exception {
        cont.setData("Original Data that is reasonably long".getBytes(UTF_8));
        try (IFileProvider prov = cont.getFileProvider()) {
            File file = prov.getFile();
            try (FileOutputStream fos = new FileOutputStream(file, true)) {
                fos.write(" Appended Text".getBytes(UTF_8));
            }
        }
        Assert.assertEquals("Original Data that is reasonably long Appended Text", new String(cont.data(), UTF_8));
    }

    @Test
    public void testDirectFileInteractionReplace() throws Exception {
        cont.setData("Original Data that is reasonably long".getBytes(UTF_8));
        try (IFileProvider prov = cont.getFileProvider()) {
            File file = prov.getFile();
            try (FileOutputStream fos = new FileOutputStream(file, false)) {
                fos.write("Replacement Text".getBytes(UTF_8));
            }
        }
        Assert.assertEquals("Replacement Text", new String(cont.data(), UTF_8));
    }

    @Test
    public void testDirectFileInteractionAppendShort() throws Exception {
        cont.setData("A".getBytes(UTF_8));
        try (IFileProvider prov = cont.getFileProvider()) {
            File file = prov.getFile();
            try (FileOutputStream fos = new FileOutputStream(file, true)) {
                fos.write(" Appended Text".getBytes(UTF_8));
            }
        }
        Assert.assertEquals("A Appended Text", new String(cont.data(), UTF_8));
    }

    @Test
    public void testDirectFileInteractionReplaceShort() throws Exception {
        cont.setData("A".getBytes(UTF_8));
        try (IFileProvider prov = cont.getFileProvider()) {
            File file = prov.getFile();
            try (FileOutputStream fos = new FileOutputStream(file, false)) {
                fos.write("Replacement Text".getBytes(UTF_8));
            }
        }
        Assert.assertEquals("Replacement Text", new String(cont.data(), UTF_8));
    }

    @Test
    public void testGetFileNeverNull() throws Exception {
        try (IFileProvider prov = cont.getFileProvider()) {
            Assert.assertNotNull(prov.getFile());
        }
        cont.setData(null);
        try (IFileProvider prov = cont.getFileProvider()) {
            Assert.assertNotNull(prov.getFile());
        }
        try (SeekableByteChannel sbc = cont.newChannel(1)) {
        }
        try (IFileProvider prov = cont.getFileProvider()) {
            Assert.assertNotNull(prov.getFile());
        }
    }

}
