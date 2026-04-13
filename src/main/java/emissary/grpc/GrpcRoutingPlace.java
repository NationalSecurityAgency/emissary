package emissary.grpc;

import emissary.config.Configurator;
import emissary.grpc.future.CompletableFutureFinalizers;
import emissary.grpc.invoker.GrpcInvoker;
import emissary.grpc.pool.ConnectionFactory;
import emissary.grpc.pool.PoolException;
import emissary.grpc.retry.RetryHandler;
import emissary.place.ServiceProviderPlace;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.GeneratedMessageV3;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractBlockingStub;
import io.grpc.stub.AbstractFutureStub;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.pool2.ObjectPool;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
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
    public static final Set<Status.Code> RETRY_GRPC_CODES = Set.of(
            Status.Code.UNAVAILABLE,
            Status.Code.DEADLINE_EXCEEDED,
            Status.Code.RESOURCE_EXHAUSTED);

    public static final String GRPC_HOST = "GRPC_HOST_";
    public static final String GRPC_PORT = "GRPC_PORT_";

    protected GrpcInvoker grpcInvoker;

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

    private void configureGrpc() {
        if (configG == null) {
            throw new IllegalStateException("gRPC configurations not found for " + this.getPlaceName());
        }

        hostnameTable.putAll(getHostnameConfigs());
        portNumberTable.putAll(getPortNumberConfigs());

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

        grpcInvoker = new GrpcInvoker(new RetryHandler(configG, this.getPlaceName(), this::retryOnException));
    }

    protected Map<String, String> getHostnameConfigs() {
        return Objects.requireNonNull(configG).findStringMatchMap(GRPC_HOST, true);
    }

    protected Map<String, Integer> getPortNumberConfigs() {
        return Objects.requireNonNull(configG).findStringMatchMap(GRPC_PORT).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Integer.parseInt(entry.getValue())));
    }

    /**
     * Determines if the {@link RetryHandler} should try again when an exception is thrown during gRPC invocation. Default
     * behavior attempts retries for any {@link PoolException} or for any Exception with a {@link Status.Code} in
     * {@link #RETRY_GRPC_CODES}. Subclasses may override this behavior.
     *
     * @param t the Exception thrown during gRPC invocation
     * @return {@code true} if the {@link RetryHandler} should try again, otherwise {@code false}
     */
    protected boolean retryOnException(Throwable t) {
        if (t instanceof StatusRuntimeException) {
            StatusRuntimeException e = (StatusRuntimeException) t;
            return RETRY_GRPC_CODES.contains(e.getStatus().getCode());
        }
        return t instanceof PoolException;
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
     * Wrapper method for {@link GrpcInvoker#invoke(ObjectPool, Function, BiFunction, GeneratedMessageV3)} that executes a
     * unary gRPC call to a given endpoint.
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
        return grpcInvoker.invoke(channelPoolLookup(targetId), stubFactory, callLogic, request);
    }

    /**
     * Wrapper method for {@link GrpcInvoker#invokeAsync(ObjectPool, Function, BiFunction, GeneratedMessageV3)} that
     * executes a unary gRPC call to a given endpoint and returns a {@link CompletableFuture future}.
     *
     * @param targetId the identifier used in the configs for the given gRPC endpoint
     * @param stubFactory function that creates the appropriate gRPC stub from a {@link ManagedChannel}
     * @param callLogic function that performs the actual gRPC call using the stub and request
     * @param request the protobuf request message to send
     * @return the future that waits for the response returned by the gRPC call
     * @param <Q> the protobuf request type
     * @param <R> the protobuf response type
     * @param <S> the gRPC stub type
     */
    protected <Q extends GeneratedMessageV3, R extends GeneratedMessageV3, S extends AbstractFutureStub<S>> CompletableFuture<R> invokeGrpcAsync(
            String targetId, Function<ManagedChannel, S> stubFactory, BiFunction<S, Q, ListenableFuture<R>> callLogic, Q request) {
        return grpcInvoker.invokeAsync(channelPoolLookup(targetId), stubFactory, callLogic, request);
    }

    /**
     * Wrapper for {@link CompletableFutureFinalizers#awaitAllAndGet(Collection, Supplier, Function)}. If any future
     * completes exceptionally, the corresponding {@link CompletableFuture#join()} call will throw a
     * {@link RuntimeException}, and iteration will stop at that point.
     *
     * @param futures collection of futures representing asynchronous computations
     * @param factory creates the output collection instance
     * @return a new collection of the results returned by the asynchronous computations
     * @param <R> result type
     * @param <C> collection type
     */
    protected <R, C extends Collection<R>> C awaitAllAndGet(
            Collection<CompletableFuture<R>> futures, Supplier<? extends C> factory) {
        return CompletableFutureFinalizers.awaitAllAndGet(futures, factory, null);
    }

    /**
     * Wrapper for {@link CompletableFutureFinalizers#awaitAllAndGet(Collection, Supplier, Function)} with custom future
     * exception handling.
     *
     * @param futures collection of futures representing asynchronous computations
     * @param factory creates the output collection instance
     * @param exceptionally function for handling individual future exceptions, throws normally if {@code null}
     * @return a new collection of the results returned by the asynchronous computations
     * @param <R> result type
     * @param <C> collection type
     */
    protected <R, C extends Collection<R>> C awaitAllAndGet(
            Collection<CompletableFuture<R>> futures, Supplier<? extends C> factory, Function<Throwable, R> exceptionally) {
        return CompletableFutureFinalizers.awaitAllAndGet(futures, factory, exceptionally);
    }

    /**
     * Wrapper for {@link CompletableFutureFinalizers#awaitAllAndGet(Map, Supplier, Function)}. If any future completes
     * exceptionally, the corresponding {@link CompletableFuture#join()} call will throw a {@link RuntimeException}, and
     * iteration will stop at that point.
     *
     * @param futures map of keys to futures representing asynchronous computations
     * @param factory creates the output map instance
     * @return a new map of keys to the results returned by the asynchronous computations
     * @param <K> key type
     * @param <R> result type
     * @param <M> map type
     */
    protected <K, R, M extends Map<K, R>> M awaitAllAndGet(
            Map<K, CompletableFuture<R>> futures, Supplier<? extends M> factory) {
        return CompletableFutureFinalizers.awaitAllAndGet(futures, factory, null);
    }

    /**
     * Wrapper for {@link CompletableFutureFinalizers#awaitAllAndGet(Map, Supplier, Function)} with custom future exception
     * handling.
     *
     * @param futures map of keys to futures representing asynchronous computations
     * @param factory creates the output map instance
     * @param exceptionally function for handling individual future exceptions, throws normally if {@code null}
     * @return a new map of keys to the results returned by the asynchronous computations
     * @param <K> key type
     * @param <R> result type
     * @param <M> map type
     */
    protected <K, R, M extends Map<K, R>> M awaitAllAndGet(
            Map<K, CompletableFuture<R>> futures, Supplier<? extends M> factory, Function<Throwable, R> exceptionally) {
        return CompletableFutureFinalizers.awaitAllAndGet(futures, factory, exceptionally);
    }

    private ObjectPool<ManagedChannel> channelPoolLookup(String targetId) {
        return tableLookup(channelPoolTable, targetId);
    }

    public String getHostname(String targetId) {
        return tableLookup(hostnameTable, targetId);
    }

    public int getPortNumber(String targetId) {
        return tableLookup(portNumberTable, targetId);
    }

    protected <T> T tableLookup(Map<String, T> table, String targetId) {
        if (table.containsKey(targetId)) {
            return table.get(targetId);
        }
        throw new IllegalArgumentException(String.format("Target-ID %s was never configured", targetId));
    }
}
