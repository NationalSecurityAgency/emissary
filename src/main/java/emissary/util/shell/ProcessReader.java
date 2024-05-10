/*
 * ProcessReader.java
 *
 * Created on November 19, 2001, 5:28 PM
 */

package emissary.util.shell;

import org.slf4j.MDC;

import java.util.Map;

public abstract class ProcessReader extends Thread {

    private Map<String, String> contextMap;

    public abstract void finish();

    /**
     * Allows parent threads to pass state values retrieved through a call to {@link MDC#getCopyOfContextMap()}
     * 
     * @param contextMap Map of captured point-in-time state values
     */
    public void setContextMap(Map<String, String> contextMap) {
        this.contextMap = contextMap;
    }

    /**
     * Applies logger context to the current thread. This method should only be called from within the overridden
     * {@link Thread#run()} method, since the context is backed by a {@link ThreadLocal ThreadLocal&lt;T&gt;} map.
     */
    protected final void applyLogContextMap() {
        if (contextMap != null) {
            MDC.setContextMap(contextMap);
        }
    }

    /**
     * Wrapper method to ensure that the {@link #applyLogContextMap()} method is always invoked before executing the core
     * functionality of ProcessReader subclasses
     */
    @Override
    public void run() {
        applyLogContextMap();
        runImpl();
    }

    /**
     * Abstract method that subclasses should override to implement their core thread-specific functionality
     */
    abstract void runImpl();
}
