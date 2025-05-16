package emissary.util.grpc.pool;

/**
 * <a href="https://docs.microsoft.com/en-us/aspnet/core/grpc/performance?view=aspnetcore-5.0">Source</a> for default
 * gRPC settings.
 */
final class ConnectionDefaults {
    static final long KEEP_ALIVE = 60000L;
    static final long KEEP_ALIVE_TIMEOUT = 30000L;
    static final boolean KEEP_ALIVE_WITHOUT_CALLS = false;
    static final int MAX_MESSAGE_SIZE = 1 << 22; // grpc-java default: 4 MiB (2^22)
    static final int MAX_METADATA_SIZE = 1 << 13; // grpc-java default: 8 KiB (2^13)
    static final String LOAD_BALANCING_POLICY = "round_robin";
    static final float ERODING_FACTOR = -1.0f; // disables automatic pool shrinking
    static final int MIN_IDLE_CONNECTIONS = 0;
    static final int MAX_IDLE_CONNECTIONS = 8;
    static final int MAX_SIZE = 8;
    static final long MAX_BORROW_WAIT = 10000L;
    static final boolean TEST_BEFORE_BORROW = true;

    private ConnectionDefaults() {}
}
