package emissary.grpc.invoker;

import emissary.grpc.exceptions.PoolException;
import emissary.grpc.exceptions.ServiceException;
import emissary.grpc.exceptions.ServiceNotAvailableException;
import emissary.grpc.pool.ConnectionFactory;
import emissary.grpc.retry.RetryHandler;

import com.google.protobuf.GeneratedMessageV3;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractBlockingStub;
import org.apache.commons.pool2.ObjectPool;

import java.util.function.BiFunction;
import java.util.function.Function;

public class BlockingInvoker extends GrpcInvoker {
    public BlockingInvoker(Function<String, ObjectPool<ManagedChannel>> channelPoolLookup, RetryHandler retryHandler) {
        super(channelPoolLookup, retryHandler);
    }

    /**
     * Executes a unary gRPC call to a given endpoint using an {@link AbstractBlockingStub}. If the gRPC connection fails
     * due to a {@link PoolException} or a {@link ServiceNotAvailableException}, the call will be tried again per the
     * configurations set using {@link RetryHandler}. All other Exceptions are thrown on the spot. Will also throw an
     * Exception once max attempts have been reached.
     *
     * @param targetId the identifier used in the configs for the given gRPC endpoint
     * @param stubFactory function that creates the appropriate gRPC stub from a {@link ManagedChannel}
     * @param callLogic function that performs the actual gRPC call using the stub and request
     * @param request the protobuf request message to send
     * @return the response returned by the gRPC call
     * @param <Q> the protobuf request type
     * @param <R> the protobuf response type
     * @param <S> the gRPC stub type
     */
    public <Q extends GeneratedMessageV3, R extends GeneratedMessageV3, S extends AbstractBlockingStub<S>> R invoke(
            String targetId, Function<ManagedChannel, S> stubFactory, BiFunction<S, Q, R> callLogic, Q request) {
        return retryHandler.execute(() -> {
            ObjectPool<ManagedChannel> channelPool = lookupChannelPool(targetId);
            ManagedChannel channel = ConnectionFactory.acquireChannel(channelPool);
            R response = null;
            try {
                S stub = stubFactory.apply(channel);
                response = callLogic.apply(stub, request);
                ConnectionFactory.returnChannel(channel, channelPool);
            } catch (StatusRuntimeException e) {
                ConnectionFactory.invalidateChannel(channel, channelPool);
                ServiceException.handleGrpcStatusRuntimeException(e);
            } catch (RuntimeException e) {
                ConnectionFactory.invalidateChannel(channel, channelPool);
                throw e;
            }
            return response;
        });
    }
}
