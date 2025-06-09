package emissary.grpc;

import emissary.config.Configurator;
import emissary.grpc.exceptions.PoolException;
import emissary.grpc.exceptions.ServiceException;
import emissary.grpc.exceptions.ServiceNotAvailableException;
import emissary.grpc.pool.ConnectionFactory;
import emissary.grpc.retry.Executor;
import emissary.grpc.retry.Policy;
import emissary.place.ServiceProviderPlace;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.GeneratedMessageV3;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractBlockingStub;
import io.grpc.stub.AbstractFutureStub;
import jakarta.annotation.Nullable;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class GrpcConnectionPlace extends ServiceProviderPlace implements IGrpcConnectionPlace {
    protected static final String GRPC_HOST = "GRPC_HOST";
    protected static final String GRPC_PORT = "GRPC_PORT";

    private ConnectionFactory connectionFactory;
    protected ObjectPool<ManagedChannel> channelPool;
    protected Policy retryPolicy;

    protected GrpcConnectionPlace() throws IOException {
        super();
        configureGrpc();
    }

    protected GrpcConnectionPlace(String thePlaceLocation) throws IOException {
        super(thePlaceLocation);
        configureGrpc();
    }

    protected GrpcConnectionPlace(InputStream configStream) throws IOException {
        super(configStream);
        configureGrpc();
    }

    protected GrpcConnectionPlace(String configFile, String placeLocation) throws IOException {
        super(configFile, placeLocation);
        configureGrpc();
    }

    protected GrpcConnectionPlace(InputStream configStream, String placeLocation) throws IOException {
        super(configStream, placeLocation);
        configureGrpc();
    }

    protected GrpcConnectionPlace(String configFile, @Nullable String theDir, String thePlaceLocation) throws IOException {
        super(configFile, theDir, thePlaceLocation);
        configureGrpc();
    }

    protected GrpcConnectionPlace(InputStream configStream, @Nullable String theDir, String thePlaceLocation) throws IOException {
        super(configStream, theDir, thePlaceLocation);
        configureGrpc();
    }

    protected GrpcConnectionPlace(@Nullable Configurator configs) throws IOException {
        super(configs);
        configureGrpc();
    }

    protected void configureGrpc() {
        if (configG == null) {
            throw new IllegalStateException("gRPC configurations not found for " + this.getPlaceName());
        }

        String host = configG.findRequiredStringEntry(GRPC_HOST);
        int port = configG.findIntEntry(GRPC_PORT, -1);
        if (port == -1) {
            throw new IllegalArgumentException(String.format("Missing required parameter [%s]", GRPC_PORT));
        }

        connectionFactory = new ConnectionFactory(host, port, configG) {
            @Override
            public boolean validateObject(PooledObject<ManagedChannel> pooledObject) {
                return validateConnection(pooledObject.getObject());
            }

            @Override
            public void passivateObject(PooledObject<ManagedChannel> pooledObject) {
                passivateConnection(pooledObject.getObject());
            }
        };
        channelPool = connectionFactory.newConnectionPool();
        retryPolicy = new Policy(configG, this.getPlaceName());
    }

    /**
     * Validates whether a given {@link ManagedChannel} is capable of successfully communicating with its associated gRPC
     * server.
     *
     * @param managedChannel the gRPC channel to validate
     * @return {@code true} if the channel is healthy and the server responds successfully, else {@code false}
     */
    protected abstract boolean validateConnection(ManagedChannel managedChannel);

    /**
     * Called after a gRPC call to clean up the channel. No-op by default, since gRPC channels are designed to remain ready
     * for reuse. Override this if using a stub that requires channels be reset or cleared between uses.
     *
     * @param managedChannel the gRPC channel to clean up
     */
    protected void passivateConnection(ManagedChannel managedChannel) { /* No-op */ }

    /**
     * Executes a unary gRPC call using a {@code BlockingStub}.
     *
     * @param stubFactory function that creates the appropriate gRPC stub from a {@link ManagedChannel}
     * @param callLogic function that performs the actual gRPC call using the stub and request
     * @param payload the protobuf request message to send
     * @return the response returned by the gRPC call, or {@code null} if a non-retryable error
     * @param <StubT> the gRPC stub type
     * @param <ReqT> the protobuf request type
     * @param <RespT> the protobuf response type
     */
    protected <StubT extends AbstractBlockingStub<StubT>, ReqT extends GeneratedMessageV3, RespT extends GeneratedMessageV3> RespT invokeGrpc(
            Function<ManagedChannel, StubT> stubFactory,
            BiFunction<StubT, ReqT, RespT> callLogic, ReqT payload) {

        ManagedChannel channel = ConnectionFactory.acquireChannel(channelPool);
        RespT response = null;
        try {
            StubT stub = stubFactory.apply(channel);
            response = callLogic.apply(stub, payload);
            ConnectionFactory.returnChannel(channel, channelPool);
        } catch (StatusRuntimeException e) {
            ConnectionFactory.returnChannel(channel, channelPool);
            ServiceException.handleGrpcStatusRuntimeException(e);
        } catch (RuntimeException e) {
            logger.error("Encountered error while processing data in {}", this.getPlaceName(), e);
            ConnectionFactory.invalidateChannel(channel, channelPool);
        }
        return response;
    }

    /**
     * Wraps {@link GrpcConnectionPlace#invokeGrpc(Function, BiFunction, GeneratedMessageV3)} with retry logic. If the gRPC
     * connection fails, the call will be tried again per the configurations set using {@link Policy}.
     *
     * @param stubFactory a function that creates the appropriate gRPC stub from a {@link ManagedChannel}
     * @param callLogic a function that invokes the desired RPC method on the stub
     * @param payload the protobuf message to send
     * @return the gRPC response message, or {@code null} if all retries fail
     * @param <StubT> the type of gRPC stub
     * @param <ReqT> the type of protobuf request
     * @param <RespT> the type of protobuf response
     */
    protected <StubT extends AbstractBlockingStub<StubT>, ReqT extends GeneratedMessageV3, RespT extends GeneratedMessageV3> RespT invokeGrpcWithRetry(
            Function<ManagedChannel, StubT> stubFactory,
            BiFunction<StubT, ReqT, RespT> callLogic, ReqT payload) {

        Executor<RespT> retryExecutor = retryPolicy.generateExecutor(() -> invokeGrpc(stubFactory, callLogic, payload));
        while (retryExecutor.canContinue()) {
            try {
                return retryExecutor.execute();
            } catch (PoolException | ServiceNotAvailableException e) {
                logger.atLevel(retryExecutor.getLogLevel()).log("Failed gRPC connection attempt #{} from {}",
                        retryExecutor.getAttemptNumber(), this.getPlaceName());
            }
        }
        return null;
    }

    /**
     * Executes multiple unary gRPC calls in parallel using a shared {@link AbstractFutureStub}.
     * <p>
     * TODO: Determine channel handling strategy when some calls succeed and others fail <br>
     * TODO: Clarify expected blocking behavior for response collection
     *
     * @param stubFactory function that creates the appropriate {@code FutureStub} from a {@link ManagedChannel}
     * @param callLogic function that maps a stub and payload to a {@link ListenableFuture}
     * @param payloadList list of protobuf request messages to be sent
     * @return list of gRPC responses in the same order as {@code payloadList}
     * @param <StubT> the gRPC stub type
     * @param <ReqT> the protobuf request type
     * @param <RespT> the protobuf response type
     */
    protected <StubT extends AbstractFutureStub<StubT>, ReqT extends GeneratedMessageV3, RespT extends GeneratedMessageV3> List<RespT> invokeBatchedGrpc(
            Function<ManagedChannel, StubT> stubFactory,
            BiFunction<StubT, ReqT, ListenableFuture<RespT>> callLogic, List<ReqT> payloadList) {

        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Executes multiple unary gRPC calls in parallel using a shared {@link AbstractFutureStub}. Failed requests will be
     * retried according to the configured {@link Policy}.
     * <p>
     * TODO: Decide when to call retries - Requires blocking
     *
     * @param stubFactory function that creates the appropriate {@code FutureStub} from a {@link ManagedChannel}
     * @param callLogic function that maps a stub and payload to a {@link ListenableFuture}
     * @param payloadList list of protobuf request messages to be sent
     * @return list of gRPC responses in the same order as {@code payloadList}
     * @param <StubT> the gRPC stub type
     * @param <ReqT> the protobuf request type
     * @param <RespT> the protobuf response type
     */
    protected <StubT extends AbstractFutureStub<StubT>, ReqT extends GeneratedMessageV3, RespT extends GeneratedMessageV3> List<RespT> invokeBatchedGrpcWithRetry(
            Function<ManagedChannel, StubT> stubFactory,
            BiFunction<StubT, ReqT, ListenableFuture<RespT>> callLogic, List<ReqT> payloadList) {

        throw new UnsupportedOperationException("Not yet implemented");
    }

    public String getHost() {
        return connectionFactory.getHost();
    }

    public int getPort() {
        return connectionFactory.getPort();
    }

    public String getTarget() {
        return connectionFactory.getTarget();
    }

    public long getKeepAliveMillis() {
        return connectionFactory.getKeepAliveMillis();
    }

    public long getKeepAliveTimeoutMillis() {
        return connectionFactory.getKeepAliveTimeoutMillis();
    }

    public boolean getKeepAliveWithoutCalls() {
        return connectionFactory.getKeepAliveWithoutCalls();
    }

    public String getLoadBalancingPolicy() {
        return connectionFactory.getLoadBalancingPolicy();
    }

    public int getMaxInboundMessageByteSize() {
        return connectionFactory.getMaxInboundMessageByteSize();
    }

    public int getMaxInboundMetadataByteSize() {
        return connectionFactory.getMaxInboundMetadataByteSize();
    }

    public float getErodingPoolFactor() {
        return connectionFactory.getErodingPoolFactor();
    }

    public boolean getPoolBlockedWhenExhausted() {
        return connectionFactory.getPoolBlockedWhenExhausted();
    }

    public long getPoolMaxWaitMillis() {
        return connectionFactory.getPoolMaxWaitMillis();
    }

    public int getPoolMinIdleConnections() {
        return connectionFactory.getPoolMinIdleConnections();
    }

    public int getPoolMaxIdleConnections() {
        return connectionFactory.getPoolMaxIdleConnections();
    }

    public int getPoolMaxTotalConnections() {
        return connectionFactory.getPoolMaxTotalConnections();
    }

    public boolean getPoolIsLifo() {
        return connectionFactory.getPoolIsLifo();
    }

    public boolean getPoolIsFifo() {
        return connectionFactory.getPoolIsFifo();
    }
}
