package emissary.core;

import com.codahale.metrics.health.HealthCheck;
import emissary.pool.AgentPool;
import emissary.pool.MobileAgentFactory;

public class AgentPoolHealthCheck extends HealthCheck {

    @Override
    protected Result check() throws Exception {
        int active = 0;
        int idle = 0;
        try {
            for (int i = 0; i < AgentPool.lookup().getMaxActive(); i++) {
                String agentKey = MobileAgentFactory.AGENT_NAME + "-" + String.format("%02d", i);
                if (Namespace.exists(agentKey)) {
                    if (Namespace.lookup(agentKey).toString().startsWith("Idle")) {
                        idle++;
                    } else {
                        active++;
                    }
                } else {
                    return Result.unhealthy("Missing an agent in the Namespace: " + agentKey);
                }
            }
            return Result.healthy("Pool size active/idle: " + active + "/" + idle);
        } catch (EmissaryException e) {
            return Result.unhealthy("Problem when looking up the pool: " + e.getMessage());
        }
    }

}
