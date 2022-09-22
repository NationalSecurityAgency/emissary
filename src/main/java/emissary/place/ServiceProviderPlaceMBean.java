package emissary.place;

import java.util.List;

/**
 * MBean interface that exposes attributes/methods to JConsole
 */
public interface ServiceProviderPlaceMBean {
    List<String> getRunningConfig();

    String getPlaceStats();

    long getResourceLimitMillis();

    void dumpRunningConfig();

    void dumpPlaceStats();
}
