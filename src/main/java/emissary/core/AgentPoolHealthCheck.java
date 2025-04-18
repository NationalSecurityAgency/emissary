package emissary.core;

import emissary.pool.AgentPool;
import emissary.pool.MobileAgentFactory;

import com.codahale.metrics.health.HealthCheck;

import java.util.Locale;

public class AgentPoolHealthCheck extends HealthCheck {

    @Override
    protected Result check() {
        int active = 0;
        int idle = 0;
        try {
            for (int i = 0; i < AgentPool.lookup().getMaxTotal(); i++) {
                String agentKey = MobileAgentFactory.AGENT_NAME + "-" + String.format(Locale.getDefault(), "%02d", i);
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
