package emissary.grpc;

import emissary.config.Configurator;
import emissary.grpc.pool.ConnectionFactory;
import emissary.grpc.retry.RetryHandler;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.GeneratedMessageV3;
import io.grpc.ManagedChannel;
import io.grpc.stub.AbstractBlockingStub;
import io.grpc.stub.AbstractFutureStub;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Convenience place for processing data using gRPC connections to external services that only require one single
 * endpoint.
 * <p>
 * Configuration Keys:
 * <ul>
 * <li>{@code GRPC_HOST} - gRPC service hostname or DNS target, <i>required</i></li>
 * <li>{@code GRPC_PORT} - gRPC service port, <i>required</i></li>
 * <li>See {@link ConnectionFactory} for supported pooling and gRPC channel configuration keys and defaults.</li>
 * <li>See {@link RetryHandler} for supported retry configuration keys and defaults.</li>
 * </ul>
 */
public abstract class GrpcConnectionPlace extends GrpcRoutingPlace {
    public static final String GRPC_HOST = "GRPC_HOST";
    public static final String GRPC_PORT = "GRPC_PORT";
    protected static final String CONNECTION_ID = "#gRPC-service";

    protected GrpcConnectionPlace() throws IOException {
        super();
    }

    protected GrpcConnectionPlace(String thePlaceLocation) throws IOException {
        super(thePlaceLocation);
    }

    protected GrpcConnectionPlace(InputStream configStream) throws IOException {
        super(configStream);
    }

    protected GrpcConnectionPlace(String configFile, String placeLocation) throws IOException {
        super(configFile, placeLocation);
    }

    protected GrpcConnectionPlace(InputStream configStream, String placeLocation) throws IOException {
        super(configStream, placeLocation);
    }

    protected GrpcConnectionPlace(String configFile, @Nullable String theDir, String thePlaceLocation) throws IOException {
        super(configFile, theDir, thePlaceLocation);
    }

    protected GrpcConnectionPlace(InputStream configStream, @Nullable String theDir, String thePlaceLocation) throws IOException {
        super(configStream, theDir, thePlaceLocation);
    }

    protected GrpcConnectionPlace(@Nullable Configurator configs) throws IOException {
        super(configs);
    }

    @Override
    protected Map<String, String> getHostnameConfigs() {
        return Map.of(CONNECTION_ID, Objects.requireNonNull(configG).findRequiredStringEntry(GRPC_HOST));
    }

    @Override
    protected Map<String, Integer> getPortNumberConfigs() {
        return Map.of(CONNECTION_ID, Integer.parseInt(Objects.requireNonNull(configG).findRequiredStringEntry(GRPC_PORT)));
    }

    /**
     * Wrapper method for {@link GrpcRoutingPlace#invokeGrpc(String, Function, BiFunction, GeneratedMessageV3)} that
     * executes a unary gRPC call to a given endpoint.
     *
     * @param stubFactory function that creates the appropriate gRPC stub from a {@link ManagedChannel}
     * @param callLogic function that performs the actual gRPC call using the stub and request
     * @param request the protobuf request message to send
     * @return the response returned by the gRPC call
     * @param <Q> the protobuf request type
     * @param <R> the protobuf response type
     * @param <S> the gRPC stub type
     */
    protected <Q extends GeneratedMessageV3, R extends GeneratedMessageV3, S extends AbstractBlockingStub<S>> R invokeGrpc(
            Function<ManagedChannel, S> stubFactory, BiFunction<S, Q, R> callLogic, Q request) {
        return invokeGrpc(CONNECTION_ID, stubFactory, callLogic, request);
    }

    /**
     * Wrapper method for {@link GrpcRoutingPlace#invokeAsyncGrpc(String, Function, BiFunction, GeneratedMessageV3)} that
     * executes a unary gRPC call to a given endpoint returns a {@link CompletableFuture future}.
     *
     * @param stubFactory function that creates the appropriate gRPC stub from a {@link ManagedChannel}
     * @param callLogic function that performs the actual gRPC call using the stub and request
     * @param request the protobuf request message to send
     * @return the future that waits for the response returned by the gRPC call
     * @param <Q> the protobuf request type
     * @param <R> the protobuf response type
     * @param <S> the gRPC stub type
     */
    protected <Q extends GeneratedMessageV3, R extends GeneratedMessageV3, S extends AbstractFutureStub<S>> CompletableFuture<R> invokeAsyncGrpc(
            Function<ManagedChannel, S> stubFactory, BiFunction<S, Q, ListenableFuture<R>> callLogic, Q request) {
        return invokeAsyncGrpc(CONNECTION_ID, stubFactory, callLogic, request);
    }

    public String getHost() {
        return getHostname(CONNECTION_ID);
    }

    public int getPort() {
        return getPortNumber(CONNECTION_ID);
    }
}
