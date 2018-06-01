package emissary.id;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import emissary.core.IBaseDataObject;

/**
 * Id place that sets the current form based on size of the data
 */
public class SizeIdPlace extends IdPlace {
    protected int[] SIZES = {0, // ZERO
            200, // TINY
            3000, // SMALL
            40000, // MEDIUM
            500000, // LARGE
            6000000, // HUGE
            70000000, // ENORMOUS
            900000000 // ASTRONOMICAL
    };

    protected String[] LABELS = {"SIZE_ZERO", "SIZE_TINY", "SIZE_SMALL", "SIZE_MEDIUM", "SIZE_LARGE", "SIZE_HUGE", "SIZE_ENORMOUS",
            "SIZE_ASTRONOMICAL"};

    /** True iff the Filetype should be set */
    protected boolean SETFT = true;

    /** True iff the current form should be set */
    protected boolean SETCF = true;

    /**
     * Create the place
     * 
     * @param config the configuration file or resource name
     */
    public SizeIdPlace(String config) throws IOException {
        super(config, "SizeIdPlace.example.com:8001");
        configurePlace();
    }

    /**
     * Create with default config
     */
    public SizeIdPlace() throws IOException {
        configurePlace();
    }

    /**
     * The remote constructor
     */
    public SizeIdPlace(String cfgInfo, String dir, String placeLoc) throws IOException {
        super(cfgInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * Congure stuff for this place
     */
    protected void configurePlace() {}

    /**
     * Process a payload
     * 
     * @param payload the payload to process
     */
    @Override
    public List<IBaseDataObject> processHeavyDuty(IBaseDataObject payload) {
        String szType = fileTypeBySize(payload.dataLength());
        if (SETFT) {
            payload.setFileType(szType);
        }
        if (SETCF) {
            payload.setCurrentForm(szType);
        }

        return Collections.emptyList();
    }

    /**
     * Get a size label based on the size passed in
     * 
     * @param sz the size
     * @return the corresponding label
     */
    public String fileTypeBySize(int sz) {
        for (int i = 0; i < SIZES.length; i++) {
            if (sz <= SIZES[i]) {
                return LABELS[i];
            }
        }
        return LABELS[LABELS.length - 1];
    }

    public static void main(String[] args) {
        mainRunner(SizeIdPlace.class, args);
    }
}
