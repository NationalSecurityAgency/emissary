package emissary.spi;

import emissary.test.core.junit5.UnitTest;

import org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.memory.BufferAllocator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests various ArrowRootAllocatorProvider scenarios and demonstrates expected behavior when conditions related to
 * failing to close various Arrow resources occurs.
 */
public class ArrowRootAllocatorProviderTest extends UnitTest {
    /** shutdown is clean if no memory has been allocated and no child allocators have been created */
    @Test
    public void testArrowRootAllocatorProvider() {
        ArrowRootAllocatorProvider provider = new ArrowRootAllocatorProvider();
        provider.initialize();
        BufferAllocator allocator = ArrowRootAllocatorProvider.getArrowRootAllocator();
        assertNotNull(allocator);
        provider.shutdown();
    }

    /** creating a buffer and not closing it will cause a leak */
    @Test
    public void testArrowRootAllocatorShutdownLeak() {
        final ArrowRootAllocatorProvider provider = new ArrowRootAllocatorProvider();
        provider.initialize();
        BufferAllocator allocatorOne = ArrowRootAllocatorProvider.getArrowRootAllocator();
        assertNotNull(allocatorOne);
        ArrowBuf buffer = allocatorOne.buffer(1024);
        assertThrows(IllegalStateException.class, provider::shutdown,
                "expected IllegalStateException attempting to shutdown allocator with allocated buffer open");
    }

    /**
     * creating a child allocator and not closing it before the root allocator provider is shutdown is OK, as long as that
     * child allocator doesn't have any open buffers. The root allocator provider attempts to shut down all children.
     */
    @Test
    public void testArrowRootAllocatorShutdownChildClean() {
        final ArrowRootAllocatorProvider provider = new ArrowRootAllocatorProvider();
        provider.initialize();
        BufferAllocator allocatorOne = ArrowRootAllocatorProvider.getArrowRootAllocator();
        assertNotNull(allocatorOne);
        BufferAllocator allocatorChild = allocatorOne.newChildAllocator("child", 1024, 2048);
        assertNotNull(allocatorChild);
    }

    /**
     * creating a child allocator and not closing its buffers before the root allocator provider is shutdown should fail
     * when the root allocator provider attempts to shut down all children.
     */
    @Test
    public void testArrowRootAllocatorShutdownChildLeak() {
        final ArrowRootAllocatorProvider provider = new ArrowRootAllocatorProvider();
        provider.initialize();
        BufferAllocator allocatorOne = ArrowRootAllocatorProvider.getArrowRootAllocator();
        assertNotNull(allocatorOne);
        BufferAllocator allocatorChild = allocatorOne.newChildAllocator("child", 1024, 2048);
        allocatorChild.buffer(1024);
        assertNotNull(allocatorChild);
        assertThrows(IllegalStateException.class, provider::shutdown,
                "expected IllegalStateException attempting to shutdown allocator with child allocator open");
    }

    /** both allocated buffers and child allocators must be closed before the root allocator can be shutdown cleanly */
    @Test
    public void testArrowRootAllocatorShutdownAfterProperClose() {
        final ArrowRootAllocatorProvider provider = new ArrowRootAllocatorProvider();
        provider.initialize();
        BufferAllocator allocatorOne = ArrowRootAllocatorProvider.getArrowRootAllocator();
        assertNotNull(allocatorOne);
        BufferAllocator allocatorChild = allocatorOne.newChildAllocator("child", 1024, 2048);
        ArrowBuf buffer = allocatorChild.buffer(1024);
        buffer.close();
        allocatorChild.close();
        provider.shutdown();
    }

    /** the root allocator can't be obtained after shutdown */
    @Test()
    public void testArrowRootAllocatorProviderAfterShutdown() {
        ArrowRootAllocatorProvider provider = new ArrowRootAllocatorProvider();
        provider.initialize();
        BufferAllocator allocatorOne = ArrowRootAllocatorProvider.getArrowRootAllocator();
        assertNotNull(allocatorOne);
        provider.shutdown();
        assertThrows(IllegalStateException.class, ArrowRootAllocatorProvider::getArrowRootAllocator,
                "expected IllegalStateException attempting to get an allocator after shutdown");
    }

    /** the root allocator won't allocate after shutdown */
    @Test
    public void testArrowRootAllocatorProviderAllocateAfterShutdown() {
        ArrowRootAllocatorProvider provider = new ArrowRootAllocatorProvider();
        provider.initialize();
        BufferAllocator allocator = ArrowRootAllocatorProvider.getArrowRootAllocator();
        assertNotNull(allocator);
        provider.shutdown();
        assertThrows(IllegalStateException.class, () -> allocator.buffer(1024),
                "expected IllegalStateException attempting to allocate after provider is shutdown");
    }
}

