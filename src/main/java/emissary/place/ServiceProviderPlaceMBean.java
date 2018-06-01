package emissary.place;

import java.util.List;

/**
 * MBean interface that exposes attributes/methods to JConsole
 */
public interface ServiceProviderPlaceMBean {
    public List<String> getRunningConfig();

    public String getPlaceStats();

    public long getResourceLimitMillis();

    public void dumpRunningConfig();

    public void dumpPlaceStats();
}
