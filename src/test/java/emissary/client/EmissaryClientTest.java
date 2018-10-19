package emissary.client;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

import emissary.config.ConfigUtil;
import emissary.test.core.UnitTest;
import org.apache.http.client.config.RequestConfig;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmissaryClientTest extends UnitTest {

    private static final Logger logger = LoggerFactory.getLogger(EmissaryClientTest.class);

    @Test
    public void testDefaultRequestConfig() {
        logger.debug("Starting testDefaultRequestConfig");
        Path origCfg = Paths.get(ConfigUtil.getProjectBase() + "/classes/emissary/client/EmissaryClient.cfg");
        Path hiddenCfg = Paths.get(ConfigUtil.getProjectBase() + "/classes/emissary/client/EmissaryClient.cfg.hideme");
        try {
            // remove EmissaryClient.cfg from classpath
            Files.move(origCfg, hiddenCfg, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            EmissaryClient.configure();
            EmissaryClient client = new EmissaryClient();
            RequestConfig requestConfig = client.getRequestConfig();
            assertThat(requestConfig.getConnectTimeout(), equalTo(EmissaryClient.DEFAULT_CONNECTION_TIMEOUT.intValue()));
            assertThat(requestConfig.getConnectionRequestTimeout(), equalTo(EmissaryClient.DEFAULT_CONNECTION_MANAGER_TIMEOUT.intValue()));
            assertThat(requestConfig.getSocketTimeout(), equalTo(EmissaryClient.DEFAULT_SO_TIMEOUT.intValue()));
        } catch (IOException e) {
            logger.error("Problem moving {}", origCfg.toAbsolutePath().toString(), e);
        } finally {
            // put that file back
            if (!Files.exists(origCfg)) {
                try {
                    Files.move(hiddenCfg, origCfg, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    logger.error("Problem moving {} to {}", hiddenCfg.toAbsolutePath().toString(), origCfg.toAbsolutePath().toString(), e);
                }
            }
        }
    }

    @Test
    public void testDefaultRequestConfigFromClasspath() {
        logger.debug("Starting testDefaultRequestConfigFromClasspath");
        // reading the EmissaryClient.cfg from the classpath
        EmissaryClient.configure();
        EmissaryClient client = new EmissaryClient();
        RequestConfig requestConfig = client.getRequestConfig();
        // asserted values are copied from src/main/resource/emissary/client/EmissaryClient.cfg, which is packaged
        // with the jar
        assertThat(requestConfig.getConnectTimeout(), equalTo(new Long(TimeUnit.MINUTES.toMillis(10)).intValue()));
        assertThat(requestConfig.getConnectionRequestTimeout(), equalTo(new Long(TimeUnit.MINUTES.toMillis(5)).intValue()));
        assertThat(requestConfig.getSocketTimeout(), equalTo(new Long(TimeUnit.SECONDS.toMillis(90)).intValue()));
    }

    @Test
    public void testRequestConfigFromConfigDir() {
        logger.debug("Starting testRequestConfigFromConfigDir");
        Path cfgFile = Paths.get(ConfigUtil.getConfigDirs().get(0) + "/emissary.client.EmissaryClient.cfg");
        OutputStream out = null;
        try {
            out = new BufferedOutputStream(Files.newOutputStream(cfgFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND));
            int newConnectionTimeout = 5000;
            int newConnectionManagerTimeout = 4000;
            int newSocketTimeout = 3000;
            String cfg =
                    "connectionTimeout = " + newConnectionTimeout + "\n" + "connectionManagerTimeout = " + newConnectionManagerTimeout + "\n"
                            + "soTimeout = " + newSocketTimeout;
            byte[] data = cfg.getBytes();
            out.write(data, 0, data.length);
            out.close();
            EmissaryClient.configure();
            EmissaryClient client = new EmissaryClient();
            RequestConfig requestConfig = client.getRequestConfig();
            assertThat(requestConfig.getConnectTimeout(), equalTo(newConnectionTimeout));
            assertThat(requestConfig.getConnectionRequestTimeout(), equalTo(newConnectionManagerTimeout));
            assertThat(requestConfig.getSocketTimeout(), equalTo(newSocketTimeout));
        } catch (IOException e) {
            logger.error("Problem with {}", cfgFile.toAbsolutePath().toString(), e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // maybe already closed, just swallow
                }
            }
            // put that file back
            if (Files.exists(cfgFile)) {
                try {
                    Files.delete(cfgFile);
                } catch (IOException e) {
                    logger.error("Problem removing {}", cfgFile.toAbsolutePath().toString(), e);
                }
            }
        }

    }

    @Test
    public void testPassingInRequestConfig() {
        logger.debug("Starting testPassingInRequestConfig");
        EmissaryClient.configure();
        EmissaryClient client = new EmissaryClient();
        RequestConfig requestConfig = client.getRequestConfig();
        // initial value from config file on classpath
        int valueInCfgOnClasspath = new Long(TimeUnit.MINUTES.toMillis(10)).intValue();
        assertThat(requestConfig.getConnectTimeout(), equalTo(valueInCfgOnClasspath));
        int newTimeout = new Long(TimeUnit.MINUTES.toMillis(3)).intValue();
        client.setConnectionTimeout(newTimeout);
        // did it get reset?
        assertThat(client.getRequestConfig().getConnectTimeout(), equalTo(new Long(newTimeout).intValue()));
        // ensure it didn't override config for new instances
        assertThat(new EmissaryClient().getRequestConfig().getConnectTimeout(), equalTo(valueInCfgOnClasspath));
    }

}
