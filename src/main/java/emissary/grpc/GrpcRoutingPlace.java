package emissary.grpc;

import emissary.config.Configurator;
import emissary.grpc.exceptions.PoolException;
import emissary.grpc.exceptions.ServiceException;
import emissary.grpc.exceptions.ServiceNotAvailableException;
import emissary.grpc.pool.ConnectionFactory;
import emissary.grpc.retry.RetryHandler;
import emissary.place.ServiceProviderPlace;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.GeneratedMessageV3;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractBlockingStub;
import io.grpc.stub.AbstractFutureStub;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.pool2.ObjectPool;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Place for processing data using gRPC connections to external services. Supports multiple end-points with
 * <i>shared</i> configurations, where each endpoint is identified by a given target ID.
 * <p>
 * Configuration Keys:
 * <ul>
 * <li>{@code GRPC_HOST_{Target-ID}} - gRPC service hostname or DNS target, where {@code Target-ID} is the unique
 * identifier for the given host:port</li>
 * <li>{@code GRPC_PORT_{Target-ID}} - gRPC service port, where {@code Target-ID} is the unique identifier for the given
 * host:port</li>
 * <li>See {@link ConnectionFactory} for supported pooling and gRPC channel configuration keys and defaults.</li>
 * <li>See {@link RetryHandler} for supported retry configuration keys and defaults.</li>
 * </ul>
 */
public abstract class GrpcRoutingPlace extends ServiceProviderPlace implements IGrpcRoutingPlace {
    public static final String GRPC_HOST = "GRPC_HOST_";
    public static final String GRPC_PORT = "GRPC_PORT_";

    protected RetryHandler retryHandler;
    protected final Map<String, String> hostnameTable = new HashMap<>();
    protected final Map<String, Integer> portNumberTable = new HashMap<>();
    protected final Map<String, ObjectPool<ManagedChannel>> channelPoolTable = new HashMap<>();

    protected GrpcRoutingPlace() throws IOException {
        super();
        configureGrpc();
    }

    protected GrpcRoutingPlace(String thePlaceLocation) throws IOException {
        super(thePlaceLocation);
        configureGrpc();
    }

    protected GrpcRoutingPlace(InputStream configStream) throws IOException {
        super(configStream);
        configureGrpc();
    }

    protected GrpcRoutingPlace(String configFile, String placeLocation) throws IOException {
        super(configFile, placeLocation);
        configureGrpc();
    }

    protected GrpcRoutingPlace(InputStream configStream, String placeLocation) throws IOException {
        super(configStream, placeLocation);
        configureGrpc();
    }

    protected GrpcRoutingPlace(String configFile, @Nullable String theDir, String thePlaceLocation) throws IOException {
        super(configFile, theDir, thePlaceLocation);
        configureGrpc();
    }

    protected GrpcRoutingPlace(InputStream configStream, @Nullable String theDir, String thePlaceLocation) throws IOException {
        super(configStream, theDir, thePlaceLocation);
        configureGrpc();
    }

    protected GrpcRoutingPlace(@Nullable Configurator configs) throws IOException {
        super(configs);
        configureGrpc();
    }

    protected Map<String, String> configureHostnames() {
        return Objects.requireNonNull(configG).findStringMatchMap(GRPC_HOST, true);
    }

    protected Map<String, Integer> configurePortNumbers() {
        return Objects.requireNonNull(configG).findStringMatchMap(GRPC_PORT).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Integer.parseInt(entry.getValue())));
    }

    private void configureGrpc() {
        if (configG == null) {
            throw new IllegalStateException("gRPC configurations not found for " + this.getPlaceName());
        }

        hostnameTable.putAll(configureHostnames());
        portNumberTable.putAll(configurePortNumbers());

        if (!hostnameTable.keySet().equals(portNumberTable.keySet())) {
            throw new IllegalArgumentException("gRPC hostname target-IDs do not match gRPC port number target-IDs");
        }

        if (hostnameTable.isEmpty()) {
            throw new NullPointerException(String.format(
                    "Missing required arguments: %s${Target-ID} and %s${Target-ID}", GRPC_HOST, GRPC_PORT));
        }

        Set<String> targetIds = hostnameTable.keySet();
        for (String id : targetIds) {
            channelPoolTable.put(id, newConnectionPool(id));
        }

        retryHandler = new RetryHandler(configG, this.getPlaceName());
    }

    private ObjectPool<ManagedChannel> newConnectionPool(String id) {
        return newConnectionFactory(id).newConnectionPool();
    }

    private ConnectionFactory newConnectionFactory(String id) {
        return newConnectionFactory(hostnameTable.get(id), portNumberTable.get(id), Objects.requireNonNull(configG));
    }

    private ConnectionFactory newConnectionFactory(String host, int port, @Nonnull Configurator cfg) {
        return new ConnectionFactory(host, port, cfg, this::validateConnection, this::passivateConnection);
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
     * Executes a unary gRPC call to a given endpoint using a {@code BlockingStub}. If the gRPC connection fails due to a
     * {@link PoolException} or a {@link ServiceNotAvailableException}, the call will be tried again per the configurations
     * set using {@link RetryHandler}. All other Exceptions are thrown on the spot. Will also throw an Exception once max
     * attempts have been reached.
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
    protected <Q extends GeneratedMessageV3, R extends GeneratedMessageV3, S extends AbstractBlockingStub<S>> R invokeGrpc(
            String targetId, Function<ManagedChannel, S> stubFactory, BiFunction<S, Q, R> callLogic, Q request) {

        return retryHandler.execute(() -> {
            ObjectPool<ManagedChannel> channelPool = channelPoolLookup(targetId);
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

    /**
     * Executes multiple unary gRPC calls to a given endpoint in parallel using a shared {@link AbstractFutureStub}.
     * <p>
     * TODO: Determine channel handling strategy when some calls succeed and others fail <br>
     * TODO: Clarify expected blocking behavior for response collection
     *
     * @param targetId the identifier used in the configs for the given gRPC endpoint
     * @param stubFactory function that creates the appropriate {@code FutureStub} from a {@link ManagedChannel}
     * @param callLogic function that maps a stub and request to a {@link ListenableFuture}
     * @param requestList list of protobuf request messages to be sent
     * @return list of gRPC responses in the same order as {@code requestList}
     * @param <Q> the protobuf request type
     * @param <R> the protobuf response type
     * @param <S> the gRPC stub type
     */
    protected <Q extends GeneratedMessageV3, R extends GeneratedMessageV3, S extends AbstractFutureStub<S>> List<R> invokeBatchedGrpc(
            String targetId, Function<ManagedChannel, S> stubFactory,
            BiFunction<S, Q, ListenableFuture<R>> callLogic, List<Q> requestList) {

        throw new UnsupportedOperationException("Not yet implemented");
    }

    private ObjectPool<ManagedChannel> channelPoolLookup(String targetId) {
        if (channelPoolTable.containsKey(targetId)) {
            return channelPoolTable.get(targetId);
        }
        throw new IllegalArgumentException(String.format("Target-ID %s was never configured", targetId));
    }

    public String getHostname(String targetId) {
        return hostnameTable.get(targetId);
    }

    public int getPortNumber(String targetId) {
        return portNumberTable.get(targetId);
    }
}
