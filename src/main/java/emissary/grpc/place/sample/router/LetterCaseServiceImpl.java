package emissary.grpc.place.sample.router;

import emissary.grpc.sample.v1.proto.LetterCaseHealthStatus;
import emissary.grpc.sample.v1.proto.LetterCaseRequest;
import emissary.grpc.sample.v1.proto.LetterCaseResponse;
import emissary.grpc.sample.v1.proto.LetterCaseServiceGrpc;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;

import java.util.Locale;

/**
 * Mocks an external service that changes the case of a String.
 */
public abstract class LetterCaseServiceImpl extends LetterCaseServiceGrpc.LetterCaseServiceImplBase {
    @Override
    public void checkHealth(Empty request, StreamObserver<LetterCaseHealthStatus> responseObserver) {
        LetterCaseHealthStatus status = LetterCaseHealthStatus.newBuilder().setOk(true).build();
        responseObserver.onNext(status);
        responseObserver.onCompleted();
    }

    @Override
    public void toLetterCase(LetterCaseRequest request, StreamObserver<LetterCaseResponse> responseObserver) {
        LetterCaseResponse resp = LetterCaseResponse.newBuilder()
                .setResult(changeCase(request.getQuery()))
                .build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    protected abstract String changeCase(String input);

    /**
     * Mocks an external service that capitalizes a String.
     */
    public static class UpperCaseServiceImpl extends LetterCaseServiceImpl {
        @Override
        protected String changeCase(String input) {
            return input.toUpperCase(Locale.ROOT);
        }
    }

    /**
     * Mocks an external service that lowercases a String.
     */
    public static class LowerCaseServiceImpl extends LetterCaseServiceImpl {
        @Override
        protected String changeCase(String input) {
            return input.toLowerCase(Locale.ROOT);
        }
    }
}
