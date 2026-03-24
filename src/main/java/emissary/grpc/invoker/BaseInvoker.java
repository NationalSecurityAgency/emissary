package emissary.grpc.invoker;

import emissary.grpc.retry.RetryHandler;

import org.slf4j.Logger;

public abstract class BaseInvoker {
    protected final RetryHandler retryHandler;
    protected final Logger logger;

    protected BaseInvoker(RetryHandler retryHandler, Logger logger) {
        this.retryHandler = retryHandler;
        this.logger = logger;
    }
}
