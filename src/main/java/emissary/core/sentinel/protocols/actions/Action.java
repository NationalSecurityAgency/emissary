package emissary.core.sentinel.protocols.actions;

import emissary.core.sentinel.Sentinel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Map;

public class Action {

    protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * Take action when rule conditions are met
     *
     * @param trackers the listing of agents, places, and filenames that's currently processing
     */
    public void trigger(Map<String, Sentinel.Tracker> trackers) {

    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

}
