package emissary.grpc.invoker;

import emissary.grpc.retry.RetryHandler;

import io.grpc.ManagedChannel;
import org.apache.commons.pool2.ObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public abstract class GrpcInvoker {
    protected final Function<String, ObjectPool<ManagedChannel>> channelPoolLookup;
    protected final RetryHandler retryHandler;
    protected final Logger logger;

    protected GrpcInvoker(Function<String, ObjectPool<ManagedChannel>> channelPoolLookup, RetryHandler retryHandler) {
        this.channelPoolLookup = channelPoolLookup;
        this.retryHandler = retryHandler;
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    protected ObjectPool<ManagedChannel> lookupChannelPool(String targetId) {
        return this.channelPoolLookup.apply(targetId);
    }
}
