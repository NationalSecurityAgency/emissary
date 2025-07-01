package emissary.grpc.place.sample.router;

import emissary.config.Configurator;
import emissary.core.IBaseDataObject;
import emissary.grpc.place.GrpcRouterPlace;
import emissary.grpc.place.sample.router.LetterCaseServiceImpl.LowerCaseServiceImpl;
import emissary.grpc.place.sample.router.LetterCaseServiceImpl.UpperCaseServiceImpl;
import emissary.grpc.sample.v1.proto.LetterCaseRequest;
import emissary.grpc.sample.v1.proto.LetterCaseResponse;
import emissary.grpc.sample.v1.proto.LetterCaseServiceGrpc;
import emissary.grpc.sample.v1.proto.LetterCaseServiceGrpc.LetterCaseServiceImplBase;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Connects to mock services {@link UpperCaseServiceImpl} and {@link LowerCaseServiceImpl}, which both implement the
 * same {@link LetterCaseServiceImplBase}. Both services use a gRPC connection to change the case of a String, which is
 * then written to an alternate view.
 */
public class LetterCasePlace extends GrpcRouterPlace {
    public static final String FORM_PREFIX = "SERVICE_PROXY_FORM-";
    public static final String ALTERNATE_VIEW_NAME = "ALT_LETTER_CASE_VIEW";

    private static final Pattern FORM_TO_PARSE = Pattern.compile(FORM_PREFIX + "(\\w+)");

    public LetterCasePlace(Configurator cfg) throws IOException {
        super(cfg);
    }

    @Override
    public void process(IBaseDataObject o) {
        Matcher matcher = FORM_TO_PARSE.matcher(o.currentForm());
        if (matcher.matches()) {
            LetterCaseRequest request = LetterCaseRequest.newBuilder()
                    .setQuery(new String(o.data()))
                    .build();

            LetterCaseResponse response = invokeGrpc(
                    matcher.group(1),
                    LetterCaseServiceGrpc::newBlockingStub,
                    LetterCaseServiceGrpc.LetterCaseServiceBlockingStub::toLetterCase,
                    request);

            o.addAlternateView(ALTERNATE_VIEW_NAME, response.getResult().getBytes());

        } else {
            throw new IllegalStateException("Unsupported form type " + o.currentForm());
        }
    }

    /**
     * Calls a health check to the mock external service before borrowing the channel from the pool.
     *
     * @param managedChannel the gRPC channel to validate
     * @return {@code true} if channel is healthy, otherwise {@code false}
     */
    @Override
    protected boolean validateConnection(ManagedChannel managedChannel) {
        LetterCaseServiceGrpc.LetterCaseServiceBlockingStub stub = LetterCaseServiceGrpc.newBlockingStub(managedChannel);
        return stub.checkHealth(Empty.getDefaultInstance()).getOk();
    }
}
