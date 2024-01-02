package emissary.util;

import jakarta.xml.bind.DatatypeConverter;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class PkiUtil {

    private static final String FILE_PRE = "file://";
    private static final Pattern ENV_VARIABLE_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    private static final String BASE_64_ENCODED_DATA = "base64EncodedData";
    // get between 64 and 4096 characters of Base64-encoded cert data
    private static final Pattern CERT_PATTERN = Pattern.compile(
            "^-----BEGIN CERTIFICATE-----$(?<" + BASE_64_ENCODED_DATA + ">(?i)[a-z0-9+/=\\r\\n]{64,4096})^-----END CERTIFICATE-----$",
            Pattern.MULTILINE);

    private static final Logger log = LoggerFactory.getLogger(PkiUtil.class);

    /* build the key/trust store from props */
    public static KeyStore buildStore(@Nullable final String path, final char[] pazz, final String type)
            throws IOException, GeneralSecurityException {
        if ((path == null) || path.isEmpty()) {
            return null;
        }
        final KeyStore keyStore = KeyStore.getInstance(type);
        String pemData = FileUtils.readFileToString(new File(path), US_ASCII);
        if (isPemCertificate(pemData)) {
            loadKeyStore(keyStore, pemData);
        } else {
            try (final InputStream is = Files.newInputStream(Paths.get(path))) {
                keyStore.load(is, pazz);
            }
        }
        return keyStore;
    }

    protected static boolean isPemCertificate(String data) {
        return CERT_PATTERN.matcher(data).find();
    }

    private static void loadKeyStore(KeyStore keyStore, String pemData)
            throws CertificateException, IOException, NoSuchAlgorithmException, KeyStoreException {
        // initialize the keystore
        keyStore.load(null, null);

        int certCount = 0;
        int matcherPosition = 0;
        Matcher matcher = CERT_PATTERN.matcher(pemData);
        while (matcher.find(matcherPosition)) {
            byte[] derBytes = DatatypeConverter.parseBase64Binary(matcher.group(BASE_64_ENCODED_DATA).trim());
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate x509Certificate = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(derBytes));
            keyStore.setCertificateEntry("cert_" + certCount++, x509Certificate);
            matcherPosition = matcher.end();
        }
    }

    /*
     * Build char array from password, load from file or read from environment variable.
     */
    public static char[] loadPW(@Nullable final String pazz) throws IOException {
        if (pazz == null) {
            return null;
        }
        String realPW;
        if (pazz.startsWith(FILE_PRE)) {
            final String pth = pazz.substring(FILE_PRE.length());
            log.debug("Loading key password from file " + pth);
            try (BufferedReader r = new BufferedReader(new FileReader(pth))) {
                realPW = r.readLine();
            }
            if (realPW == null) {
                throw new IOException("Unable to load store password from " + pazz);
            }
        } else {
            Matcher matcher = ENV_VARIABLE_PATTERN.matcher(pazz);
            if (matcher.matches()) {
                realPW = System.getenv(matcher.group(1));
            } else {
                realPW = pazz;
            }
        }
        return realPW.toCharArray();
    }
}
