package emissary.directory;

import emissary.client.EmissaryClient;
import emissary.client.EmissaryResponse;
import emissary.test.core.junit5.UnitTest;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.core.MediaType;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeartbeatManagerTest extends UnitTest {

    @Test
    void testBadGetHearbeatHasBAD_RESPONSE() {
        String fromPlace = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://localhost:8001/DirectoryPlace";
        String toPlace = "*.*.*.http://localhost:1233/DirectoryPlace";
        EmissaryResponse response = HeartbeatManager.getHeartbeat(fromPlace, toPlace);
        assertTrue(response.getContentString().contains(HeartbeatManager.BAD_RESPOSNE));
    }

    @Test
    void testBadHeartbeat() {
        String directoryKey = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://localhost:8001/DirectoryPlace";
        HeartbeatManager mgr = new HeartbeatManager(directoryKey, 30, 30);
        boolean isUp = mgr.heartbeat("*.*.*.http://localhost:1222/DirectoryPlace");
        assertFalse(isUp);
    }

    @Test
    void testSlowHeartbeat() throws IOException {
        // peer didn't respond before the timeout, throws org.apache.http.NoHttpResponseException which is still and
        // IOException
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        when(mockClient.execute(any(HttpUriRequest.class), any(HttpContext.class))).thenThrow(
                new NoHttpResponseException("localhost:1222 failed to respond"));

        EmissaryClient client = new EmissaryClient(mockClient);

        String fromPlace = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://localhost:8001/DirectoryPlace";
        String toPlace = "*.*.*.http://localhost:1233/DirectoryPlace";
        EmissaryResponse response = HeartbeatManager.getHeartbeat(fromPlace, toPlace, client); // use that client
        assertTrue(response.getContentString().contains(HeartbeatManager.BAD_RESPOSNE));
    }

    @Test
    void testUnauthorizedHeartbeat() throws IOException {
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        HttpEntity mockHttpEntity = mock(HttpEntity.class);

        when(mockClient.execute(any(HttpUriRequest.class), any(HttpContext.class))).thenReturn(mockResponse);
        when(mockResponse.getCode()).thenReturn(401);
        when(mockResponse.getEntity()).thenReturn(mockHttpEntity);
        String responseString = "Unauthorized heartbeat man";
        when(mockHttpEntity.getContent()).thenReturn(IOUtils.toInputStream(responseString, StandardCharsets.UTF_8));
        BasicHeader header1 = new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN);
        Header[] headers = new Header[] {header1};
        when(mockResponse.getHeaders(any())).thenReturn(headers);

        EmissaryClient client = new EmissaryClient(mockClient);

        String fromPlace = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://localhost:8001/DirectoryPlace";
        String toPlace = "*.*.*.http://localhost:1233/DirectoryPlace";
        EmissaryResponse response = HeartbeatManager.getHeartbeat(fromPlace, toPlace, client); // use that client
        assertTrue(response.getContentString().contains("Bad request -> status: 401 message: " + responseString));
    }

}
