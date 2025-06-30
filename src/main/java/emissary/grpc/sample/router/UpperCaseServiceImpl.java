package emissary.grpc.sample.router;

import emissary.grpc.sample.v1.proto.LetterCaseHealthStatus;
import emissary.grpc.sample.v1.proto.LetterCaseRequest;
import emissary.grpc.sample.v1.proto.LetterCaseResponse;
import emissary.grpc.sample.v1.proto.LetterCaseServiceGrpc;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;

import java.util.Locale;

/**
 * Mocks an external service that capitalizes a String.
 */
public class UpperCaseServiceImpl extends LetterCaseServiceGrpc.LetterCaseServiceImplBase {
    @Override
    public void checkHealth(Empty request, StreamObserver<LetterCaseHealthStatus> responseObserver) {
        LetterCaseHealthStatus status = LetterCaseHealthStatus.newBuilder().setOk(true).build();
        responseObserver.onNext(status);
        responseObserver.onCompleted();
    }

    @Override
    public void toLetterCase(LetterCaseRequest request, StreamObserver<LetterCaseResponse> responseObserver) {
        LetterCaseResponse resp = LetterCaseResponse.newBuilder()
                .setResult(request.getQuery().toUpperCase(Locale.ROOT))
                .build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

}
