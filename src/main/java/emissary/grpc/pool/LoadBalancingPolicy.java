package emissary.grpc.pool;

import java.util.Locale;

public enum LoadBalancingPolicy {
    ROUND_ROBIN, PICK_FIRST;

    public String formattedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
