package emissary.grpc;

import emissary.config.Configurator;
import emissary.grpc.channel.ChannelManager;
import emissary.grpc.channel.ChannelPoolFactory.PoolException;
import emissary.grpc.invoker.GrpcInvoker;
import emissary.grpc.retry.RetryHandler;
import emissary.place.ServiceProviderPlace;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Message;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractBlockingStub;
import io.grpc.stub.AbstractFutureStub;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
 * <li>See {@link ChannelManager} for supported gRPC channel configuration keys and defaults.</li>
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

    protected final Map<String, GrpcInvoker> invokerTable = new HashMap<>();

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
        Objects.requireNonNull(configG);

        Map<String, String> hosts = getHostnameConfigs();
        Map<String, Integer> ports = getPortNumberConfigs();

        if (!hosts.keySet().equals(ports.keySet())) {
            throw new IllegalArgumentException("gRPC hostname target-IDs do not match gRPC port number target-IDs");
        }

        Set<String> targetIds = hosts.keySet();
        if (targetIds.isEmpty()) {
            throw new NullPointerException(String.format(
                    "Missing required arguments: %s${Target-ID} and %s${Target-ID}", GRPC_HOST, GRPC_PORT));
        }

        RetryHandler retryHandler = new RetryHandler(configG, this.getPlaceName(), this::retryOnException);
        for (String id : targetIds) {
            ChannelManager channelManager = new ChannelManager(hosts.get(id), ports.get(id), configG);
            GrpcInvoker grpcInvoker = new GrpcInvoker(channelManager, retryHandler);
            invokerTable.put(id, grpcInvoker);
        }
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

    /**
     * Wrapper method for {@link GrpcInvoker#invoke(Function, BiFunction, Message)} that executes a unary gRPC call to a
     * given endpoint.
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
    protected <Q extends Message, R extends Message, S extends AbstractBlockingStub<S>> R invokeGrpc(
            String targetId, Function<ManagedChannel, S> stubFactory, BiFunction<S, Q, R> callLogic, Q request) {
        return getInvoker(targetId).invoke(stubFactory, callLogic, request);
    }

    /**
     * Wrapper method for {@link GrpcInvoker#invokeAsync(Function, BiFunction, Message)} that executes a unary gRPC call to
     * a given endpoint and returns a {@link CompletableFuture future}.
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
    protected <Q extends Message, R extends Message, S extends AbstractFutureStub<S>> CompletableFuture<R> invokeGrpcAsync(
            String targetId, Function<ManagedChannel, S> stubFactory, BiFunction<S, Q, ListenableFuture<R>> callLogic, Q request) {
        return getInvoker(targetId).invokeAsync(stubFactory, callLogic, request);
    }

    public String getHostname(String targetId) {
        return getInvoker(targetId).getHost();
    }

    public int getPortNumber(String targetId) {
        return getInvoker(targetId).getPort();
    }

    private GrpcInvoker getInvoker(String targetId) {
        if (invokerTable.containsKey(targetId)) {
            return invokerTable.get(targetId);
        }
        throw new IllegalArgumentException(String.format("Target-ID %s was never configured", targetId));
    }

    @Override
    public void shutDown() {
        super.shutDown();
        invokerTable.values().forEach(GrpcInvoker::close);
    }
}
