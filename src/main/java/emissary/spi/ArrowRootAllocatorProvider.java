package emissary.spi;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;

public class ArrowRootAllocatorProvider implements InitializationProvider {

    private static final Object initalizationLock = new Object();
    private static BufferAllocator arrowRootAllocator = null;

    @Override
    public void initialize() {
        synchronized (initalizationLock) {
            arrowRootAllocator = new RootAllocator();
        }
    }

    @Override
    public void shutdown() {
        synchronized (initalizationLock) {
            arrowRootAllocator.close();
            arrowRootAllocator = null;
        }
        InitializationProvider.super.shutdown();
    }

    public static BufferAllocator getArrowRootAllocator() {
        synchronized (initalizationLock) {
            if (arrowRootAllocator == null) {
                throw new IllegalStateException("Arrow Root Allocator has not been initalized by the " +
                        "ArrowRootAllocatorProvider or is already shutdown, is emissary.spi.ArrowRootAllocatorProver " +
                        "listed in META-INF/services/emissary.spi.InitalizationProvider?");
            } else {
                return arrowRootAllocator;
            }
        }
    }
}
