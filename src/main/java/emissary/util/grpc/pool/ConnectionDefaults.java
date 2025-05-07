package emissary.util.grpc.pool;

/**
 * <a href="https://docs.microsoft.com/en-us/aspnet/core/grpc/performance?view=aspnetcore-5.0">Source</a> for default
 * gRPC settings.
 */
final class ConnectionDefaults {
    static final long GRPC_KEEP_ALIVE_MS = 60000L;
    static final long GRPC_KEEP_ALIVE_TIMEOUT_MS = 30000L;
    static final boolean GRPC_KEEP_ALIVE_WITHOUT_CALLS = false;
    static final int GRPC_MAX_INBOUND_METADATA_SIZE = 1 << 13; // grpc-java default: 8 KiB (2^13)
    static final int GRPC_MAX_INBOUND_MESSAGE_SIZE = 1 << 22; // grpc-java default: 4 MiB (2^22)
    static final String LOAD_BALANCING_POLICY = "round_robin";
    static final float ERODING_POOL_FACTOR = -1.0f; // disables automatic pool shrinking
    static final int MIN_IDLE_CONNS = 0;
    static final int MAX_IDLE_CONNS = 8;
    static final int MAX_POOL_SIZE = 8;
    static final long MAX_WAIT_POOL_BORROW = 10000L;
    static final boolean TEST_ON_BORROW = true;

    private ConnectionDefaults() {}
}
