package emissary.grpc.sample;

import emissary.grpc.sample.v1.SampleHealthStatus;
import emissary.grpc.sample.v1.SampleRequest;
import emissary.grpc.sample.v1.SampleResponse;
import emissary.grpc.sample.v1.SampleServiceGrpc.SampleServiceImplBase;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.BindableService;
import io.grpc.Context;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class GrpcSampleServer implements AutoCloseable {
    private final Server server;

    private GrpcSampleServer(BindableService service) {
        try {
            server = ServerBuilder.forPort(0)
                    .addService(service)
                    .build()
                    .start();
        } catch (IOException e) {
            throw new IllegalStateException("Problem starting server: " + e.getMessage(), e);
        }
    }

    private static SampleServiceImplBase newService(Function<SampleRequest, ByteString> behavior, boolean healthOk) {
        return new SampleServiceImplBase() {
            @Override
            public void callSampleHealthCheck(Empty request, StreamObserver<SampleHealthStatus> responseObserver) {
                SampleHealthStatus status = SampleHealthStatus.newBuilder().setOk(healthOk).build();
                responseObserver.onNext(status);
                responseObserver.onCompleted();
            }

            @Override
            public void callSampleService(SampleRequest request, StreamObserver<SampleResponse> responseObserver) {
                try {
                    SampleResponse response = SampleResponse.newBuilder()
                            .setResult(behavior.apply(request))
                            .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                } catch (Throwable t) {
                    responseObserver.onError(Status.fromThrowable(t).asRuntimeException());
                }
            }
        };
    }

    private static ByteString success(SampleRequest sampleRequest) {
        String requestString = new String(sampleRequest.getQuery().toByteArray());
        String responseString = String.format("RPC {\"%s\"} completed successfully", requestString);
        return ByteString.copyFrom(responseString.getBytes());
    }

    public static GrpcSampleServer defaultBehavior() {
        return GrpcSampleServer.of(true);
    }

    public static GrpcSampleServer of(boolean healthOk) {
        return GrpcSampleServer.of(GrpcSampleServer::success, healthOk);
    }

    public static GrpcSampleServer of(Function<SampleRequest, ByteString> behavior) {
        return GrpcSampleServer.of(behavior, true);
    }

    public static GrpcSampleServer of(Function<SampleRequest, ByteString> behavior, boolean healthOk) {
        return new GrpcSampleServer(newService(behavior, healthOk));
    }

    public static GrpcSampleServer alwaysThrow(RuntimeException ex) {
        return GrpcSampleServer.of(request -> {
            throw ex;
        });
    }

    public static GrpcSampleServer throwAfter(int maxAttempts, AtomicInteger counter, RuntimeException ex) {
        return GrpcSampleServer.of(request -> {
            if (counter.getAndIncrement() < maxAttempts) {
                return success(request);
            }
            throw ex;
        });
    }

    public static GrpcSampleServer throwUntil(int maxAttempts, AtomicInteger counter, RuntimeException ex) {
        return GrpcSampleServer.of(request -> {
            if (counter.incrementAndGet() < maxAttempts) {
                throw ex;
            }
            return success(request);
        });
    }

    public static GrpcSampleServer blockUntilReleased(CountDownLatch startedLatch, CountDownLatch releaseLatch) {
        return GrpcSampleServer.of(request -> {
            Context context = Context.current();
            context.addListener(ignored -> releaseLatch.countDown(), MoreExecutors.directExecutor());
            startedLatch.countDown();
            try {
                releaseLatch.await(); // If stuck, naturally terminates upon server shutdown due to context listener
                if (context.isCancelled()) {
                    return ByteString.EMPTY;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw Status.CANCELLED.asRuntimeException();
            }
            return success(request);
        });
    }

    @Override
    public void close() {
        server.shutdownNow();
    }

    public String getPort() {
        return String.valueOf(server.getPort());
    }
}
