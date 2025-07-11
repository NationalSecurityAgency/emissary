package emissary.grpc.place.sample.connection;

import emissary.grpc.sample.v1.proto.DataReverseHealthStatus;
import emissary.grpc.sample.v1.proto.DataReverseRequest;
import emissary.grpc.sample.v1.proto.DataReverseResponse;
import emissary.grpc.sample.v1.proto.DataReverseServiceGrpc;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;

/**
 * Mocks an external service that reverses a ByteString.
 */
public class DataReverseServiceImpl extends DataReverseServiceGrpc.DataReverseServiceImplBase {
    @Override
    public void checkHealth(Empty request, StreamObserver<DataReverseHealthStatus> responseObserver) {
        DataReverseHealthStatus status = DataReverseHealthStatus.newBuilder().setOk(true).build();
        responseObserver.onNext(status);
        responseObserver.onCompleted();
    }

    @Override
    public void dataReverse(DataReverseRequest request, StreamObserver<DataReverseResponse> responseObserver) {
        DataReverseResponse resp = DataReverseResponse.newBuilder()
                .setResult(reverseByteString(request.getQuery()))
                .build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    private static ByteString reverseByteString(ByteString input) {
        byte[] bytes = input.toByteArray();
        for (int i = 0, j = bytes.length - 1; i < j; i++, j--) {
            byte tmp = bytes[i];
            bytes[i] = bytes[j];
            bytes[j] = tmp;
        }
        return ByteString.copyFrom(bytes);
    }

}
