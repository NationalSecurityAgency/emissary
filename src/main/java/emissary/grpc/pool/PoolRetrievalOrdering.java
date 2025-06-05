package emissary.grpc.pool;

public enum PoolRetrievalOrdering {
    LIFO, FIFO;

    public static boolean getLifoFlag(String order, boolean defaultValue) {
        if (order == null) {
            return defaultValue;
        }
        return PoolRetrievalOrdering.valueOf(order) == LIFO;
    }
}
