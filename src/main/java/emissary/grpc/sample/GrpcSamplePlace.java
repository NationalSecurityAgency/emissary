package emissary.grpc.sample;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.grpc.GrpcRoutingPlace;
import emissary.grpc.future.CompletableFutureFinalizers;
import emissary.grpc.sample.v1.SampleRequest;
import emissary.grpc.sample.v1.SampleResponse;
import emissary.grpc.sample.v1.SampleServiceGrpc;
import emissary.grpc.sample.v1.SampleServiceGrpc.SampleServiceBlockingStub;
import emissary.grpc.sample.v1.SampleServiceGrpc.SampleServiceFutureStub;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Connects to an arbitrary external service that implements {@link emissary.grpc.sample.v1.SampleServiceGrpc}.
 */
public class GrpcSamplePlace extends GrpcRoutingPlace {
    public GrpcSamplePlace(Configurator configs) throws IOException {
        super(configs);
    }

    protected SampleRequest generateRequest(IBaseDataObject o) {
        return SampleRequest.newBuilder()
                .setQuery(ByteString.copyFrom(o.data()))
                .build();
    }

    @Override
    public void process(IBaseDataObject o) {
        throw new UnsupportedOperationException("Do not use: This method is only overridden to reduce error noise");
    }

    public void processEndpoint(IBaseDataObject o, String endpoint) {
        SampleResponse response = invokeGrpc(
                endpoint, SampleServiceGrpc::newBlockingStub, SampleServiceBlockingStub::callSampleService, generateRequest(o));

        o.addAlternateView(endpoint, response.getResult().toByteArray());
    }

    public void processEndpointsSequentially(IBaseDataObject o) {
        hostnameTable.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .forEach(endpoint -> processEndpoint(o, endpoint));
    }

    public void processEndpointsInParallel(IBaseDataObject o, @Nullable Function<Throwable, SampleResponse> exceptionally) {
        Map<String, CompletableFuture<SampleResponse>> futureMap = hostnameTable.keySet().stream()
                .collect(Collectors.toMap(k -> k, k -> invokeGrpcAsync(
                        k, SampleServiceGrpc::newFutureStub, SampleServiceFutureStub::callSampleService, generateRequest(o))));
        Map<String, SampleResponse> responseMap = CompletableFutureFinalizers.awaitAllAndGet(futureMap, HashMap::new, exceptionally);
        responseMap.forEach((k, v) -> o.addAlternateView(k, v.getResult().toByteArray()));
    }

    /**
     * Calls a health check to the external service before borrowing the channel from the pool.
     *
     * @param managedChannel the gRPC channel to validate
     * @return {@code true} if channel is healthy, otherwise {@code false}
     */
    @Override
    protected boolean validateConnection(ManagedChannel managedChannel) {
        SampleServiceBlockingStub stub = SampleServiceGrpc.newBlockingStub(managedChannel);
        return stub.callSampleHealthCheck(Empty.getDefaultInstance()).getOk();
    }
}
