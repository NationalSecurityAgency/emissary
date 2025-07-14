package emissary.grpc;

import emissary.config.Configurator;
import emissary.grpc.exceptions.PoolException;
import emissary.grpc.exceptions.ServiceNotAvailableException;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    protected Map<String, String> configureHostnames() {
        return Map.of(CONNECTION_ID, Objects.requireNonNull(configG).findRequiredStringEntry(GRPC_HOST));
    }

    @Override
    protected Map<String, Integer> configurePortNumbers() {
        return Map.of(CONNECTION_ID, Integer.parseInt(Objects.requireNonNull(configG).findRequiredStringEntry(GRPC_PORT)));
    }

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
     * @param <Q> the protobuf request type
     * @param <R> the protobuf response type
     * @param <S> the gRPC stub type
     */
    protected <Q extends GeneratedMessageV3, R extends GeneratedMessageV3, S extends AbstractBlockingStub<S>> R invokeGrpc(
            Function<ManagedChannel, S> stubFactory, BiFunction<S, Q, R> callLogic, Q request) {

        return invokeGrpc(CONNECTION_ID, stubFactory, callLogic, request);
    }

    /**
     * Executes multiple unary gRPC calls in parallel using a shared {@link AbstractFutureStub}.
     *
     * @param stubFactory function that creates the appropriate {@code FutureStub} from a {@link ManagedChannel}
     * @param callLogic function that maps a stub and request to a {@link ListenableFuture}
     * @param requestList list of protobuf request messages to be sent
     * @return list of gRPC responses in the same order as {@code requestList}
     * @param <Q> the protobuf request type
     * @param <R> the protobuf response type
     * @param <S> the gRPC stub type
     */
    protected <Q extends GeneratedMessageV3, R extends GeneratedMessageV3, S extends AbstractFutureStub<S>> List<R> invokeBatchedGrpc(
            Function<ManagedChannel, S> stubFactory, BiFunction<S, Q, ListenableFuture<R>> callLogic, List<Q> requestList) {

        return invokeBatchedGrpc(CONNECTION_ID, stubFactory, callLogic, requestList);
    }

    public String getHost() {
        return getHostname(CONNECTION_ID);
    }

    public int getPort() {
        return getPortNumber(CONNECTION_ID);
    }
}
