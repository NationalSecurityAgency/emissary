package emissary.core.constants;

import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

public class Configurations {

    // common config keys
    public static final String END_FORM = "END_FORM";
    public static final String NEW_FORM = "NEW_FORM";
    public static final String OUTPUT_FORM = "OUTPUT_FORM";
    public static final String PLACE_RESOURCE_LIMIT_MILLIS = "PLACE_RESOURCE_LIMIT_MILLIS";

    // reserved config keys for service/place creation
    public static final String PLACE_NAME = "PLACE_NAME";
    public static final String SERVICE_NAME = "SERVICE_NAME";
    public static final String SERVICE_TYPE = "SERVICE_TYPE";
    public static final String SERVICE_DESCRIPTION = "SERVICE_DESCRIPTION";
    public static final String SERVICE_COST = "SERVICE_COST";
    public static final String SERVICE_QUALITY = "SERVICE_QUALITY";
    public static final String SERVICE_PROXY = "SERVICE_PROXY";
    public static final String SERVICE_KEY = "SERVICE_KEY";
    public static final String SERVICE_PROXY_DENY = "SERVICE_PROXY_DENY";

    /**
     * The list of reserved service config keys for service/place creation
     */
    public static final List<String> RESERVED_SERVICE_CONFIG_KEYS = Collections.unmodifiableList(
            Lists.newArrayList(
                    PLACE_NAME,
                    SERVICE_COST,
                    SERVICE_DESCRIPTION,
                    SERVICE_KEY,
                    SERVICE_NAME,
                    SERVICE_PROXY,
                    SERVICE_PROXY_DENY,
                    SERVICE_QUALITY,
                    SERVICE_TYPE));

    private Configurations() {}
}
