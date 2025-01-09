package emissary.spi;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Provides a central class for obtaining references to the Arrow root memory allocator. Activate this by including it
 * in the list of classes in
 * 
 * <pre>
 * META - INF / services / emissary.spi.InitializationProvider
 * </pre>
 * 
 * Classes wishing to get a reference to the Arrow root allocator should call the {@link #getArrowRootAllocator()}. They
 * are free to create child allocators as needed, but they are responsible for managing any buffers created using either
 * the root or a chile allocator and calling
 * 
 * <pre>
 * close()
 * </pre>
 * 
 * on them when they are no longer needed. The {@link #shutdown()} method will automatically close any child allocators
 * created, but will throw an {@link java.lang.IllegalStateException} if there are allocated buffers that have not been
 * closed (potentially leaking memory). Provides debug and trace level logging for detailed behavior.
 */
public class ArrowRootAllocatorProvider implements InitializationProvider {
    private static final Logger logger = LoggerFactory.getLogger(ArrowRootAllocatorProvider.class);

    private static final Object allocatorLock = new Object();
    private static BufferAllocator arrowRootAllocator = null;

    @Override
    public void initialize() {
        logger.trace("Waiting for allocator lock in initialize()");
        synchronized (allocatorLock) {
            logger.debug("Creating new Arrow root allocator");

            // creates a RootAllocator with the default memory settings, we may consider implementing a limit here
            // that is set via a system property here instead.
            arrowRootAllocator = new RootAllocator();

            logger.trace("Releasing allocator lock in initialize()");
        }
    }

    /** Shuts down the root allocator and any child allocators */
    @Override
    public void shutdown() {
        logger.trace("Waiting for allocator lock in shutdown()");
        synchronized (allocatorLock) {
            logger.trace("Closing Arrow allocators");
            Collection<BufferAllocator> children = arrowRootAllocator.getChildAllocators();
            if (children.isEmpty()) {
                logger.trace("Root allocator has no children to close");
            } else {
                if (logger.isTraceEnabled()) {
                    logger.trace("Attempting to clode {} child allocators", children.size());
                }
                for (BufferAllocator child : children) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Shutting down child allocator: {}", child.getName());
                    }
                    try {
                        child.close();
                        if (logger.isTraceEnabled()) {
                            logger.trace("Successfully closed child allocator {}", child.getName());
                        }
                    } catch (IllegalStateException e) {
                        // it's ok to catch this, another IllegalStateException will be thrown when closing the root allocator.
                        logger.warn("IllegalStateException when closing child allocator {}, message: {}", child.getName(), e.getMessage());
                    }
                }
            }

            logger.trace("Closing root allocator");
            arrowRootAllocator.close();
            logger.debug("Successfully closed root allocator");
            arrowRootAllocator = null;
            logger.trace("Releasing allocator lock in shutdown()");
        }
        InitializationProvider.super.shutdown();
    }

    /**
     * Obtain a reference to the arrow root allocator. Any buffers or child allocators allocated using this instance must be
     * 
     * <pre>
     * close()
     * </pre>
     * 
     * 'd once they are no longer used.
     * 
     * @return the Arrow root allocator
     */
    public static BufferAllocator getArrowRootAllocator() {
        logger.trace("Waiting for allocator lock in getArrowRootAllocator()");
        synchronized (allocatorLock) {
            try {
                if (arrowRootAllocator == null) {
                    throw new IllegalStateException("Arrow Root Allocator has not been initialized by the " +
                            "ArrowRootAllocatorProvider or is already shutdown, is emissary.spi.ArrowRootAllocatorProver " +
                            "listed in META-INF/services/emissary.spi.InitializationProvider?");
                } else {
                    logger.trace("Returning root allocator");
                    return arrowRootAllocator;
                }
            } finally {
                logger.trace("Releasing allocator lock in getArrowRootAllocator()");
            }
        }
    }
}
