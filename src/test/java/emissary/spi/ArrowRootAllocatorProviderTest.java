package emissary.spi;

import emissary.test.core.junit5.UnitTest;

import org.apache.arrow.memory.BufferAllocator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ArrowRootAllocatorProviderTest extends UnitTest {
    @Test
    public void testArrowRootAllocatorProvider() {
        ArrowRootAllocatorProvider provider = new ArrowRootAllocatorProvider();
        provider.initialize();
        BufferAllocator allocator = ArrowRootAllocatorProvider.getArrowRootAllocator();
        assertNotNull(allocator);
    }

    @Test()
    public void testArrowRootAllocatorProviderAfterShutdown() {
        ArrowRootAllocatorProvider provider = new ArrowRootAllocatorProvider();
        provider.initialize();
        BufferAllocator allocatorOne = ArrowRootAllocatorProvider.getArrowRootAllocator();
        assertNotNull(allocatorOne);
        provider.shutdown();
        assertThrows(IllegalStateException.class, ArrowRootAllocatorProvider::getArrowRootAllocator, "expected IllegalStateException");
    }
}

