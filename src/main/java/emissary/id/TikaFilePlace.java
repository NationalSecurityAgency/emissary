package emissary.id;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.util.HashMap;
import java.util.Map;

import emissary.core.IBaseDataObject;
import org.apache.commons.lang.StringUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.mime.MimeTypesFactory;

/**
 * Perform file identification tests using the configured TIKA_SIGNATURE_FILE to drive the identification process
 */
public class TikaFilePlace extends emissary.id.IdPlace {
    private static final String DEFAULT_TIKA_SIGNATURE_FILE = "TikaMagic.xml";
    private static final String APPLICATION = "application";
    private static final String IMAGE = "image";
    private static final String DASH = "-";
    protected Map<String, Integer> minSizeMap = new HashMap<>();
    protected String tikaSignaturePath = "";

    protected MimeTypes mimeTypes;

    /**
     * The remote constructor
     */
    public TikaFilePlace(String cfgInfo, String dir, String placeLoc) throws IOException {
        super(cfgInfo, dir, placeLoc);
        configurePlace();
    }

    /**
     * The static standalone (test) constructor. The cfgInfo argument is an absolute path to some configuration file.
     *
     * @param cfgInfo absolute path to a configuration file
     */
    public TikaFilePlace(String cfgInfo) throws IOException {
        super(cfgInfo, "TestTikaFilePlace.foo.com:8003");
        configurePlace();
    }

    /**
     * The local constructor.
     */
    public TikaFilePlace() throws IOException {
        configurePlace();
    }

    /**
     * Set up place specific information from the config file, load JNI, etc.
     */
    protected void configurePlace() throws IOException {

        configureIdPlace(); // pick up ID_IGNORE types
        tikaSignaturePath = configG.findStringEntry("TIKA_SIGNATURE_FILE", DEFAULT_TIKA_SIGNATURE_FILE);

        File mfile = new File(tikaSignaturePath);
        if (!mfile.exists() || !mfile.canRead()) {
            throw new IOException("Missing or unreadable TIKA_SIGNATURE_FILE " + tikaSignaturePath);
        }
        logger.debug("Tika Signature File:  " + tikaSignaturePath);

        try (InputStream in = new FileInputStream(tikaSignaturePath)) {
            mimeTypes = MimeTypesFactory.create(in);
        } catch (MimeTypeException e) {
            logger.error("Error loading tika configuration: " + tikaSignaturePath, e);
            throw new IOException("Error loading tika configuration" + tikaSignaturePath);
        }

        for (Map.Entry<String, String> entry : configG.findStringMatchMap("MIN_SIZE_").entrySet()) {
            try {
                minSizeMap.put(entry.getKey(), Integer.parseInt(entry.getValue()));
            } catch (NumberFormatException ex) {
                logger.info("Must be numeric MIN_SIZE_" + entry.getKey() + " = " + entry.getValue());
            }
        }
    }

    /**
     * Use the Tika mime type (magic) detector to identify the file type
     *
     * @param d the IBaseDataObject payload to evaluate
     * @return mediaType
     */
    private MediaType detectType(IBaseDataObject d) throws Exception {
        try (InputStream input = TikaInputStream.get(Channels.newInputStream(d.getDataContainer().channel()))) {
            Metadata metadata = new Metadata();
            MediaType mediaType = mimeTypes.detect(input, metadata);
            logger.debug("Tika type: " + mediaType.toString());
            return mediaType;
        }
    }

    /**
     * Consume a DataObject, and return a transformed one.
     *
     * @param d the IBaseDataObject payload to evaluate
     */
    @Override
    public void process(IBaseDataObject d) {
        // Bail out on empty data
        if (d.getDataContainer().length() == 0) {
            d.setCurrentForm(emissary.core.Form.EMPTY);
            d.setFileType(emissary.core.Form.EMPTY);
            return;
        }

        try {
            MediaType mediaType = detectType(d);
            long payloadLength = d.getDataContainer().length();

            if (mediaType == null || ignores.contains(mediaType.toString()) || StringUtils.isBlank(mediaType.getType())
                    || StringUtils.isBlank(mediaType.getSubtype())) {
                logger.debug("Tika did not detect a file type.");
                return;
            }

            StringBuilder currentForm = new StringBuilder();
            if (!APPLICATION.equalsIgnoreCase(mediaType.getType()) && !IMAGE.equalsIgnoreCase(mediaType.getType())) {
                currentForm.append(mediaType.getType().toUpperCase()).append(DASH);
            }
            currentForm.append(mediaType.getSubtype().toUpperCase());
            String newForm = currentForm.toString();

            if (minSizeMap.containsKey(newForm) && payloadLength < minSizeMap.get(newForm)) {
                logger.debug("Type {} does not meet min size requirement {} < {}", newForm, minSizeMap.get(newForm), payloadLength);
                return;
            }

            logger.debug("Setting current form to " + currentForm);
            d.setCurrentForm(renamedForm(newForm));
            d.setFileTypeIfEmpty(newForm);
        } catch (Exception e) {
            logger.error("Could not run Tika Detection", e);
            return;
        }
        return;
    }

    /**
     * Main to run standalone test of the place
     *
     * @param args The file to test
     */
    public static void main(String[] args) {
        mainRunner(TikaFilePlace.class.getName(), args);
    }
}
