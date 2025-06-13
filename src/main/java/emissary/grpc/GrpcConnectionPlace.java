package emissary.grpc;

import emissary.config.Configurator;
import emissary.grpc.exceptions.PoolException;
import emissary.grpc.exceptions.ServiceException;
import emissary.grpc.exceptions.ServiceNotAvailableException;
import emissary.grpc.pool.ConnectionFactory;
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

/**
 * Place for processing data using gRPC connections to external services.
 * <p>
 * Configuration Keys:
 * <ul>
 * <li>{@code GRPC_HOST} - gRPC service hostname or DNS target, <i>required</i></li>
 * <li>{@code GRPC_PORT} - gRPC service port, <i>required</i></li>
 * <li>See {@link ConnectionFactory} for supported pooling and gRPC channel configuration keys and defaults.</li>
 * <li>See {@link RetryHandler} for supported retry configuration keys and defaults.</li>
 * </ul>
 */
public abstract class GrpcConnectionPlace extends ServiceProviderPlace implements IGrpcConnectionPlace {
    public static final String GRPC_HOST = "GRPC_HOST";
    public static final String GRPC_PORT = "GRPC_PORT";

    protected ConnectionFactory connectionFactory;
    protected ObjectPool<ManagedChannel> channelPool;
    protected RetryHandler retryHandler;

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
        int port = Integer.parseInt(configG.findRequiredStringEntry(GRPC_PORT));

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
        retryHandler = new RetryHandler(configG, this.getPlaceName());
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
     * Executes a unary gRPC call using a {@code BlockingStub}. If the gRPC connection fails due to a {@link PoolException}
     * or a {@link ServiceNotAvailableException}, the call will be tried again per the configurations set using
     * {@link RetryHandler}. All other Exceptions are thrown on the spot. Will also throw an Exception once max attempts
     * have been reached.
     *
     * @param stubFactory function that creates the appropriate gRPC stub from a {@link ManagedChannel}
     * @param callLogic function that performs the actual gRPC call using the stub and request
     * @param request the protobuf request message to send
     * @return the response returned by the gRPC call
     * @param <StubT> the gRPC stub type
     * @param <ReqT> the protobuf request type
     * @param <RespT> the protobuf response type
     */
    protected <StubT extends AbstractBlockingStub<StubT>, ReqT extends GeneratedMessageV3, RespT extends GeneratedMessageV3> RespT invokeGrpc(
            Function<ManagedChannel, StubT> stubFactory,
            BiFunction<StubT, ReqT, RespT> callLogic, ReqT request) {

        return retryHandler.execute(() -> {
            ManagedChannel channel = ConnectionFactory.acquireChannel(channelPool);
            RespT response = null;
            try {
                StubT stub = stubFactory.apply(channel);
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

    /**
     * Executes multiple unary gRPC calls in parallel using a shared {@link AbstractFutureStub}.
     * <p>
     * TODO: Determine channel handling strategy when some calls succeed and others fail <br>
     * TODO: Clarify expected blocking behavior for response collection
     *
     * @param stubFactory function that creates the appropriate {@code FutureStub} from a {@link ManagedChannel}
     * @param callLogic function that maps a stub and request to a {@link ListenableFuture}
     * @param requestList list of protobuf request messages to be sent
     * @return list of gRPC responses in the same order as {@code requestList}
     * @param <StubT> the gRPC stub type
     * @param <ReqT> the protobuf request type
     * @param <RespT> the protobuf response type
     */
    protected <StubT extends AbstractFutureStub<StubT>, ReqT extends GeneratedMessageV3, RespT extends GeneratedMessageV3> List<RespT> invokeBatchedGrpc(
            Function<ManagedChannel, StubT> stubFactory,
            BiFunction<StubT, ReqT, ListenableFuture<RespT>> callLogic, List<ReqT> requestList) {

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
