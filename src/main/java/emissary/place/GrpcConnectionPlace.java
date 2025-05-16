package emissary.place;

import emissary.config.Configurator;
import emissary.util.grpc.exceptions.ServiceException;
import emissary.util.grpc.exceptions.ServiceNotAvailableException;
import emissary.util.grpc.pool.ConnectionFactory;
import emissary.util.grpc.retry.RetryExecutor;
import emissary.util.grpc.retry.RetryPolicy;

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
    private static final String GRPC_HOST = "GRPC_HOST";
    private static final String GRPC_PORT = "GRPC_PORT";

    private ConnectionFactory connectionFactory;
    protected ObjectPool<ManagedChannel> channelPool;
    protected RetryPolicy retryPolicy;

    public GrpcConnectionPlace() throws IOException {
        super();
        configureGrpc();
    }

    public GrpcConnectionPlace(String thePlaceLocation) throws IOException {
        super(thePlaceLocation);
        configureGrpc();
    }

    public GrpcConnectionPlace(InputStream configStream) throws IOException {
        super(configStream);
        configureGrpc();
    }

    public GrpcConnectionPlace(String configFile, String placeLocation) throws IOException {
        super(configFile, placeLocation);
        configureGrpc();
    }

    public GrpcConnectionPlace(InputStream configStream, String placeLocation) throws IOException {
        super(configStream, placeLocation);
        configureGrpc();
    }

    public GrpcConnectionPlace(String configFile, @Nullable String theDir, String thePlaceLocation) throws IOException {
        super(configFile, theDir, thePlaceLocation);
        configureGrpc();
    }

    public GrpcConnectionPlace(InputStream configStream, @Nullable String theDir, String thePlaceLocation) throws IOException {
        super(configStream, theDir, thePlaceLocation);
        configureGrpc();
    }

    public GrpcConnectionPlace(@Nullable Configurator config) throws IOException {
        super();
        configG = config != null ? config : configG;
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
        };
        channelPool = connectionFactory.newConnectionPool();
        retryPolicy = new RetryPolicy(configG, this.getPlaceName());
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
        try {
            StubT stub = stubFactory.apply(channel);
            return callLogic.apply(stub, payload);
        } catch (StatusRuntimeException e) {
            ConnectionFactory.returnChannel(channel, channelPool);
            ServiceException.handleGrpcStatusRuntimeException(e);
        } catch (RuntimeException e) {
            logger.error("Encountered error while processing data in {}", this.getPlaceName(), e);
            ConnectionFactory.invalidateChannel(channel, channelPool);
            channel = null;
        } finally {
            ConnectionFactory.returnChannel(channel, channelPool);
        }
        return null;
    }

    /**
     * Wraps {@link GrpcConnectionPlace#invokeGrpc(Function, BiFunction, GeneratedMessageV3)} with retry logic. If the gRPC
     * connection fails, the call will be tried again per the configurations set using {@link RetryPolicy}.
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

        RetryExecutor<RespT> retryExecutor = new RetryExecutor<>(() -> invokeGrpc(stubFactory, callLogic, payload), retryPolicy);
        while (retryExecutor.canContinue()) {
            try {
                return retryExecutor.execute();
            } catch (ServiceNotAvailableException e) {
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
     * retried according to the configured {@link RetryPolicy}.
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

    public long getKeepAlive() {
        return connectionFactory.getKeepAlive();
    }

    public long getKeepAliveTimeout() {
        return connectionFactory.getKeepAliveTimeout();
    }

    public boolean isKeepAliveWithoutCalls() {
        return connectionFactory.isKeepAliveWithoutCalls();
    }

    public int getMaxInboundMessageSize() {
        return connectionFactory.getMaxInboundMessageSize();
    }

    public int getMaxInboundMetadataSize() {
        return connectionFactory.getMaxInboundMetadataSize();
    }

    public String getLoadBalancingPolicy() {
        return connectionFactory.getLoadBalancingPolicy();
    }

    public float getErodingPoolFactor() {
        return connectionFactory.getErodingPoolFactor();
    }
}
