package emissary.client;

import emissary.client.response.BaseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.core.MediaType;
import org.apache.commons.io.IOUtils;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@SuppressWarnings("AvoidObjectArrays")
public class EmissaryResponse {

    private static final Logger logger = LoggerFactory.getLogger(EmissaryResponse.class);

    final int status;
    @Nullable
    final Object content;
    final String contentType;
    final Header[] headers;
    final ObjectMapper objectMapper = new ObjectMapper();

    public EmissaryResponse(ClassicHttpResponse response) {
        int tempStatus = response.getCode();
        String tempContent;
        headers = response.getHeaders();
        Header[] contentHeaders = response.getHeaders(HttpHeaders.CONTENT_TYPE);
        if (contentHeaders.length > 0) {
            contentType = contentHeaders[0].getValue();
            if (contentHeaders.length > 1) {
                logger.warn("Too many content headers: {}", contentHeaders.length);
                if (logger.isDebugEnabled()) {
                    Arrays.stream(contentHeaders).sequential().forEach(ch -> logger.debug("Header -> {}", ch));
                }
            }
        } else {
            logger.debug("No content type header, setting to plain text");
            contentType = MediaType.TEXT_PLAIN;
        }
        try {
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                logger.debug("No entity");
                tempContent = "";
            } else {
                tempContent = IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8);
            }
        } catch (UnsupportedOperationException | IOException e) {
            tempContent = e.getMessage();
            tempStatus = 500;
            logger.error("There was an issue generating the response", e);
        }
        logger.debug("response was: {} with content: {}", tempStatus, tempContent);
        status = tempStatus;
        content = tempContent;
    }

    public int getStatus() {
        return status;
    }

    public Header[] getHeaders() {
        return headers;
    }

    @Nullable
    public String getContentString() {
        if (content == null) {
            return null;
        }

        if (status == HttpStatus.SC_OK) {
            return content.toString();
        } else {
            return "Bad request -> status: " + status + " message: " + content;
        }
    }

    public <T extends BaseEntity> T getContent(Class<T> mapper) {
        if (content == null) {
            return null;
        }
        try {
            if (status == HttpStatus.SC_OK) {
                return objectMapper.readValue(content.toString(), mapper);
            } else {
                return makeErrorEntity(content.toString(), mapper);
            }
        } catch (IOException e) {
            logger.error("Error mapping object to {}", mapper.getCanonicalName(), e);
            return makeErrorEntity(content.toString(), mapper);
        }
    }

    private static <T extends BaseEntity> T makeErrorEntity(String msg, Class<T> mapper) {
        T r = null;
        try {
            Object c = Class.forName(mapper.getName()).getDeclaredConstructor().newInstance();
            r = mapper.cast(c);
        } catch (ReflectiveOperationException e) {
            logger.error("Problem creating new {}", mapper.getName(), e);
        }
        if (r != null) {
            r.addError(msg);
        }
        return r;
    }

    public static class EmissaryResponseHandler implements HttpClientResponseHandler<EmissaryResponse> {

        @Override
        public EmissaryResponse handleResponse(ClassicHttpResponse response) throws IOException {
            HttpEntity entity = response.getEntity();
            EmissaryResponse er = new EmissaryResponse(response);
            EntityUtils.consume(entity);
            return er;
        }
    }

}
