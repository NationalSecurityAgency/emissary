package emissary.grpc.place.sample.connection;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.grpc.place.GrpcConnectionPlace;
import emissary.grpc.pool.ConnectionFactory;
import emissary.grpc.retry.RetryHandler;
import emissary.grpc.sample.v1.proto.DataReverseRequest;
import emissary.grpc.sample.v1.proto.DataReverseResponse;
import emissary.grpc.sample.v1.proto.DataReverseServiceGrpc;
import emissary.grpc.sample.v1.proto.DataReverseServiceGrpc.DataReverseServiceBlockingStub;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/**
 * Connects to a mock service {@link DataReverseServiceImpl} that reverses a byte array through a gRPC connection and
 * writes it to an alternate view.
 */
public class DataReversePlace extends GrpcConnectionPlace {
    public static final String REVERSED_DATA = "REVERSED_DATA";
    public static final String GRPC_POOL_KILL_AFTER_RETURN = "GRPC_POOL_KILL_AFTER_RETURN";

    private boolean wasValidateConnectionCalled;
    private final boolean passivateConnection;

    public DataReversePlace(Configurator cfg) throws IOException {
        super(cfg);
        wasValidateConnectionCalled = false;
        passivateConnection = cfg.findBooleanEntry(GRPC_POOL_KILL_AFTER_RETURN, false);
    }

    @Override
    public void process(IBaseDataObject o) {
        processWithMockBehavior(o, DataReverseServiceBlockingStub::dataReverse);
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
            return stub.dataReverse(request);
        });
    }

    /**
     * Performs Emissary processing with custom call logic to the mock gRPC server. This is unnecessary in a real
     * {@link GrpcConnectionPlace} subclass, and is only implemented here to simulate errors occurring in the server.
     *
     * @param o the data object to process
     * @param callLogic method for the mock server to perform
     */
    private void processWithMockBehavior(IBaseDataObject o,
            BiFunction<DataReverseServiceBlockingStub, DataReverseRequest, DataReverseResponse> callLogic) {

        DataReverseRequest request = DataReverseRequest.newBuilder()
                .setQuery(ByteString.copyFrom(o.data()))
                .build();

        DataReverseResponse response = invokeGrpc(DataReverseServiceGrpc::newBlockingStub, callLogic, request);
        o.addAlternateView(REVERSED_DATA, response.getResult().toByteArray());
    }

    /**
     * Calls a health check to the mock external service before borrowing the channel from the pool.
     *
     * @param managedChannel the gRPC channel to validate
     * @return {@code true} if channel is healthy, otherwise {@code false}
     */
    @Override
    protected boolean validateConnection(ManagedChannel managedChannel) {
        wasValidateConnectionCalled = true;
        DataReverseServiceBlockingStub stub = DataReverseServiceGrpc.newBlockingStub(managedChannel);
        return stub.checkHealth(Empty.getDefaultInstance()).getOk();
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
     * {@link DataReversePlace#validateConnection(ManagedChannel) validateConnection} and
     * {@link DataReversePlace#passivateConnection(ManagedChannel) passivateConnection}.
     *
     * @return a channel from the pool
     */
    public ManagedChannel acquireChannel() {
        return ConnectionFactory.acquireChannel(channelPoolTable.get(CONNECTION_ID));
    }

    /**
     * Returns a channel to the connection pool. Used for testing {@link DataReversePlace#validateConnection(ManagedChannel)
     * validateConnection} and {@link DataReversePlace#passivateConnection(ManagedChannel) passivateConnection}.
     *
     * @param channel the channel to return
     */
    public void returnChannel(ManagedChannel channel) {
        ConnectionFactory.returnChannel(channel, channelPoolTable.get(CONNECTION_ID));
    }

    public boolean getWasValidateConnectionCalled() {
        return wasValidateConnectionCalled;
    }
}
