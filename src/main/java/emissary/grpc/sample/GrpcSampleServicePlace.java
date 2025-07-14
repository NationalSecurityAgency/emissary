package emissary.grpc.sample;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.grpc.GrpcRoutingPlace;
import emissary.grpc.pool.ConnectionFactory;
import emissary.grpc.retry.RetryHandler;
import emissary.grpc.sample.v1.SampleRequest;
import emissary.grpc.sample.v1.SampleResponse;
import emissary.grpc.sample.v1.SampleServiceGrpc;
import emissary.grpc.sample.v1.SampleServiceGrpc.SampleServiceBlockingStub;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Connects to an arbitrary external service that implements {@link emissary.grpc.sample.v1.SampleServiceGrpc}. Results
 * are written to an alternate view.
 */
public class GrpcSampleServicePlace extends GrpcRoutingPlace {
    public static final String SERVICE_PROXY = "SAMPLE_FORM";
    public static final String ALTERNATE_VIEW_NAME = "SAMPLE_ALTERNATE_VIEW";
    private static final Pattern FORM_PATTERN = Pattern.compile(SERVICE_PROXY + "-(\\w+)");
    public static final String GRPC_POOL_KILL_AFTER_RETURN = "GRPC_POOL_KILL_AFTER_RETURN";

    @Nullable
    private AtomicBoolean connectionValidated;
    private final boolean passivateConnection;

    public GrpcSampleServicePlace(Configurator configs) throws IOException {
        super(configs);
        connectionValidated = null;
        passivateConnection = configs.findBooleanEntry(GRPC_POOL_KILL_AFTER_RETURN, false);
    }

    @Override
    public void process(IBaseDataObject o) {
        processWithMockBehavior(o, SampleServiceBlockingStub::callSampleService);
    }

    /**
     * Performs Emissary processing while simulating server errors.
     *
     * @param o the data object to process
     * @param e the Exception to throw
     */
    public void throwExceptionsDuringProcess(IBaseDataObject o, RuntimeException e) {
        processWithMockBehavior(o, (stub, request) -> {
            throw e;
        });
    }

    /**
     * Performs Emissary processing while simulating server errors. Assumes {@link RetryHandler} is configured to retry the
     * remote procedural call.
     *
     * @param o the data object to process
     * @param e the Exception to throw
     * @param maxRetries the maximum number of times the handler can attempt to retry a connection, caps at configured
     *        amount
     * @param attempt the current number of processing attempts
     */
    public void throwExceptionsDuringProcessWithSuccessfulRetry(IBaseDataObject o, RuntimeException e,
            int maxRetries, AtomicInteger attempt) {

        processWithMockBehavior(o, (stub, request) -> {
            if (attempt.incrementAndGet() < maxRetries) {
                throw e;
            }
            return stub.callSampleService(request);
        });
    }

    /**
     * Performs Emissary processing with custom call logic to the mock gRPC server. This is unnecessary in a real
     * {@link GrpcRoutingPlace} subclass, and is only implemented here to simulate errors occurring in the server.
     *
     * @param o the data object to process
     * @param callLogic method for the mock server to perform
     */
    private void processWithMockBehavior(
            IBaseDataObject o, BiFunction<SampleServiceBlockingStub, SampleRequest, SampleResponse> callLogic) {

        Matcher matcher = FORM_PATTERN.matcher(o.currentForm());
        if (matcher.matches()) {
            String targetId = matcher.group(1);
            SampleRequest request = SampleRequest.newBuilder()
                    .setQuery(ByteString.copyFrom(o.data()))
                    .build();
            SampleResponse response = invokeGrpc(targetId, SampleServiceGrpc::newBlockingStub, callLogic, request);
            o.addAlternateView(ALTERNATE_VIEW_NAME, response.getResult().toByteArray());
        }
    }

    /**
     * Calls a health check to the external service before borrowing the channel from the pool.
     *
     * @param managedChannel the gRPC channel to validate
     * @return {@code true} if channel is healthy, otherwise {@code false}
     */
    @Override
    protected boolean validateConnection(ManagedChannel managedChannel) {
        if (connectionValidated != null) {
            connectionValidated.set(true);
        }
        SampleServiceBlockingStub stub = SampleServiceGrpc.newBlockingStub(managedChannel);
        return stub.callSampleHealthCheck(Empty.getDefaultInstance()).getOk();
    }

    /**
     * Shuts down a channel after it's been returned to the pool, if the place has been configured to do so.
     *
     * @param managedChannel the gRPC channel to clean up
     */
    @Override
    protected void passivateConnection(ManagedChannel managedChannel) {
        if (passivateConnection) {
            managedChannel.shutdownNow();
        }
    }

    /**
     * Borrows a channel from the connection pool. Used for testing
     * {@link GrpcRoutingPlace#validateConnection(ManagedChannel) validateConnection} and
     * {@link GrpcRoutingPlace#passivateConnection(ManagedChannel) passivateConnection}.
     *
     * @return a channel from the pool
     */
    public ManagedChannel acquireChannel(String targetId) {
        return ConnectionFactory.acquireChannel(channelPoolTable.get(targetId));
    }

    /**
     * Returns a channel to the connection pool. Used for testing {@link GrpcRoutingPlace#validateConnection(ManagedChannel)
     * validateConnection} and {@link GrpcRoutingPlace#passivateConnection(ManagedChannel) passivateConnection}.
     *
     * @param channel the channel to return
     */
    public void returnChannel(ManagedChannel channel, String targetId) {
        ConnectionFactory.returnChannel(channel, channelPoolTable.get(targetId));
    }

    public void setValidationCheck(AtomicBoolean connectionValidated) {
        this.connectionValidated = connectionValidated;
    }
}
