package emissary.grpc.pool;

import java.util.Locale;

public enum PoolRetrievalOrdering {
    LIFO, FIFO;

    public static PoolRetrievalOrdering getOrder(String input, PoolRetrievalOrdering defaultOrdering) {
        if (input == null) {
            return defaultOrdering;
        }
        return PoolRetrievalOrdering.valueOf(input.toUpperCase(Locale.ROOT));
    }

    public static boolean isLifo(String order, PoolRetrievalOrdering defaultOrdering) {
        return PoolRetrievalOrdering.getOrder(order, defaultOrdering) == LIFO;
    }

    public static boolean isFifo(String order, PoolRetrievalOrdering defaultOrdering) {
        return PoolRetrievalOrdering.getOrder(order, defaultOrdering) == FIFO;
    }
}
