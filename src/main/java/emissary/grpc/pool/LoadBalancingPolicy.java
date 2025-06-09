package emissary.grpc.pool;

import java.util.Locale;

public enum LoadBalancingPolicy {
    ROUND_ROBIN, PICK_FIRST;

    public static LoadBalancingPolicy getPolicy(String input, LoadBalancingPolicy defaultPolicy) {
        if (input == null) {
            return defaultPolicy;
        }
        return LoadBalancingPolicy.valueOf(input.toUpperCase(Locale.ROOT));
    }

    public static String getPolicyName(String input, LoadBalancingPolicy defaultPolicy) {
        return LoadBalancingPolicy
                .getPolicy(input, defaultPolicy)
                .name()
                .toLowerCase(Locale.ROOT);
    }
}
