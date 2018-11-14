package emissary.directory;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import javax.ws.rs.core.MediaType;

import emissary.client.EmissaryClient;
import emissary.client.EmissaryResponse;
import emissary.test.core.UnitTest;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.junit.Test;

public class HeartbeatManagerTest extends UnitTest {

    @Test
    public void testBadGetHearbeatHasBAD_RESPONSE() {
        String fromPlace = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://localhost:8001/DirectoryPlace";
        String toPlace = "*.*.*.http://localhost:1233/DirectoryPlace";
        EmissaryResponse response = HeartbeatManager.getHeartbeat(fromPlace, toPlace);
        assertThat(response.getContentString(), containsString(HeartbeatManager.BAD_RESPOSNE));
    }

    @Test
    public void testBadHeartbeat() {
        String directoryKey = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://localhost:8001/DirectoryPlace";
        HeartbeatManager mgr = new HeartbeatManager(directoryKey, 30, 30);
        boolean isUp = mgr.heartbeat("*.*.*.http://localhost:1222/DirectoryPlace");
        assertThat(isUp, equalTo(false));
    }

    @Test
    public void testSlowHeartbeat() throws ClientProtocolException, IOException {
        // peer didn't respond before the timeout, throws org.apache.http.NoHttpResponseException which is still and
        // IOException
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        when(mockClient.execute(any(HttpUriRequest.class), any(HttpContext.class))).thenThrow(
                new org.apache.http.NoHttpResponseException("localhost:1222 failed to respond"));

        EmissaryClient client = new EmissaryClient(mockClient);

        String fromPlace = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://localhost:8001/DirectoryPlace";
        String toPlace = "*.*.*.http://localhost:1233/DirectoryPlace";
        EmissaryResponse response = HeartbeatManager.getHeartbeat(fromPlace, toPlace, client); // use that client
        assertThat(response.getContentString(), containsString(HeartbeatManager.BAD_RESPOSNE));
    }

    @Test
    public void testUnauthorizedHeartbeat() throws ClientProtocolException, IOException {
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        HttpEntity mockHttpEntity = mock(HttpEntity.class);
        StatusLine mockStatusLine = mock(StatusLine.class);

        when(mockClient.execute(any(HttpUriRequest.class), any(HttpContext.class))).thenReturn(mockResponse);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(401);
        when(mockResponse.getEntity()).thenReturn(mockHttpEntity);
        String responseString = "Unauthorized heartbeat man";
        when(mockHttpEntity.getContent()).thenReturn(IOUtils.toInputStream(responseString));
        BasicHeader header1 = new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN);
        Header[] headers = new Header[] {header1};
        when(mockResponse.getHeaders(any())).thenReturn(headers);

        EmissaryClient client = new EmissaryClient(mockClient);

        String fromPlace = "EMISSARY_DIRECTORY_SERVICES.DIRECTORY.STUDY.http://localhost:8001/DirectoryPlace";
        String toPlace = "*.*.*.http://localhost:1233/DirectoryPlace";
        EmissaryResponse response = HeartbeatManager.getHeartbeat(fromPlace, toPlace, client); // use that client
        assertThat(response.getContentString(), containsString("Bad request -> status: 401 message: " + responseString));
    }

}
