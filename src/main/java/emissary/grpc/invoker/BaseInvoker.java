package emissary.grpc.invoker;

import emissary.grpc.retry.RetryHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseInvoker {
    protected final RetryHandler retryHandler;
    protected final Logger logger;

    protected BaseInvoker(RetryHandler retryHandler) {
        this.retryHandler = retryHandler;
        this.logger = LoggerFactory.getLogger(this.getClass());
    }
}
