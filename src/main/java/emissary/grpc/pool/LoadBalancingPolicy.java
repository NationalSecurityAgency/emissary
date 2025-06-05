package emissary.grpc.pool;

import java.util.Locale;

public enum LoadBalancingPolicy {
    ROUND_ROBIN("round_robin"), PICK_FIRST("pick_first");

    private final String policy;

    LoadBalancingPolicy(String policy) {
        this.policy = policy;
    }

    public static String resolvePolicy(String policy, LoadBalancingPolicy defaultPolicy) {
        if (policy == null) {
            return defaultPolicy.policy;
        }
        return LoadBalancingPolicy.valueOf(policy.toUpperCase(Locale.ROOT)).policy;
    }
}
