package emissary.util;

import emissary.test.core.junit5.UnitTest;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PkiUtilTest extends UnitTest {
    private static final String projectBase = System.getenv("PROJECT_BASE"); // set in surefire config

    @Test
    void testIsPemCertificate() throws IOException {
        String data = getAsciiString("/certs/testcertwithcomments.pem");
        boolean isPem = PkiUtil.isPemCertificate(data);
        assertTrue(isPem, "Expected a PEM file");

        data = getAsciiString("/certs/testkeystore.jks");
        isPem = PkiUtil.isPemCertificate(data);
        assertFalse(isPem, "Expected a JKS file");
    }

    private static String getAsciiString(String resourceName) throws IOException {
        String path = getAbsoluteFilePath(resourceName);
        return FileUtils.readFileToString(new File(path), StandardCharsets.US_ASCII);
    }

    @Test
    void testbuildStoreWithPem() throws IOException, GeneralSecurityException {
        String path = getAbsoluteFilePath("/certs/testcertwithcomments.pem");
        KeyStore keyStore = PkiUtil.buildStore(path, null, "JKS");

        Certificate keyStoreCertificate = keyStore.getCertificate("cert_0");
        assertInstanceOf(X509Certificate.class, keyStoreCertificate);
        assertEquals("CN=Apache Tika,OU=Apache Tika,O=Tika,L=Apache,ST=Apache Tika,C=ZZ",
                ((X509Certificate) keyStoreCertificate).getIssuerX500Principal().getName());
        assertEquals("28e5ff97573af326ba8e77de449f2e3fd92f571f", ((X509Certificate) keyStoreCertificate).getSerialNumber().toString(16));

        keyStoreCertificate = keyStore.getCertificate("cert_1");
        assertInstanceOf(X509Certificate.class, keyStoreCertificate);
        assertEquals("CN=testca,O=Internet Widgits Pty Ltd,ST=Some-State,C=AU",
                ((X509Certificate) keyStoreCertificate).getIssuerX500Principal().getName());
        assertEquals("3ad73a827ac85d83b0595e773b5c4728d8fb705c", ((X509Certificate) keyStoreCertificate).getSerialNumber().toString(16));
    }

    @Test
    void testbuildStoreWithJks() throws IOException, GeneralSecurityException {
        char[] pazz = "password".toCharArray();
        String alias = "emissary-test";

        // Test loading a JKS with private eky to Keystore
        String path = getAbsoluteFilePath("/certs/testkeystore.jks");
        KeyStore keyStore = PkiUtil.buildStore(path, pazz, "JKS");
        Key key = keyStore.getKey(alias, pazz);
        assertInstanceOf(PrivateKey.class, key);
        assertEquals("PKCS#8", key.getFormat());

        // Test loading a JKS with X509 certificate to Keystore
        path = getAbsoluteFilePath("/certs/testtruststore.jks");
        keyStore = PkiUtil.buildStore(path, pazz, "JKS");
        Certificate keyStoreCertificate = keyStore.getCertificate(alias);
        assertInstanceOf(X509Certificate.class, keyStoreCertificate);
        assertEquals("CN=emissary,OU=emissary,O=emissary,L=emissary,ST=Unknown,C=Unknown",
                ((X509Certificate) keyStoreCertificate).getIssuerX500Principal().getName());
        assertEquals("3e2adf6", ((X509Certificate) keyStoreCertificate).getSerialNumber().toString(16));
    }

    @Test
    void testLoadPWFromFile() throws Exception {
        char[] password = PkiUtil.loadPassword("file:///" + getAbsoluteFilePath("/emissary/util/web/password.file"));
        assertNotNull(password, "Failed to read password from file");
        assertEquals("password", String.valueOf(password));
    }

    /**
     * Read a known environment variable configured during setup
     * {@link emissary.test.core.junit5.UnitTest#setupSystemProperties()}
     *
     * @throws Exception thrown when an error occurs
     */
    @Test
    void testLoadPWFromEnv() throws Exception {
        char[] password = PkiUtil.loadPassword("${PROJECT_BASE}");
        assertNotNull(password, "Failed to read environment variable");
        assertEquals(projectBase, String.valueOf(password));
    }

    private static String getAbsoluteFilePath(String name) {
        URL resource = PkiUtilTest.class.getResource(name);
        assertNotNull(resource);
        return resource.getFile();
    }
}
