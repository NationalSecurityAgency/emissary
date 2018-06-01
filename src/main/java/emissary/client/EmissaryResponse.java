package emissary.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import emissary.client.response.BaseEntity;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmissaryResponse {

    private static final Logger logger = LoggerFactory.getLogger(EmissaryResponse.class);

    final int status;
    final Object content;
    final String contentType;
    final Header[] headers;
    final ObjectMapper objectMapper = new ObjectMapper();

    public EmissaryResponse(HttpResponse response) {
        int tempStatus = response.getStatusLine().getStatusCode();
        String tempContent;
        headers = response.getAllHeaders();
        Header[] contentHeaders = response.getHeaders(HttpHeaders.CONTENT_TYPE);
        if (contentHeaders.length > 0) {
            contentType = contentHeaders[0].getValue();
        } else if (contentHeaders.length > 1) {
            logger.warn("Too many content headers: {}", contentHeaders.length);
            if (logger.isDebugEnabled()) {
                for (int i = 0; i < contentHeaders.length; i++) {
                    logger.debug("Header -> {}", contentHeaders[i]);
                }
            }
            contentType = contentHeaders[0].getValue();
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
            e.printStackTrace();
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

    public String getContentString() {
        if (content == null) {
            return null;
        }
        try {
            if (status == HttpStatus.OK_200) {
                return content.toString();
            } else {
                return "Bad request -> status: " + status + " message: " + content.toString();
            }
        } catch (Exception e) {
            logger.error("Error getting string content", e);
            return e.getMessage();
        }

    }

    public <T extends BaseEntity> T getContent(Class<T> mapper) {
        if (content == null) {
            return null;
        }
        try {
            if (status == HttpStatus.OK_200) {
                return objectMapper.readValue(content.toString(), mapper);
            } else {
                return makeErrorEntity(content.toString(), mapper);
            }
        } catch (IOException e) {
            logger.error("Error mapping object to {}", mapper.getCanonicalName(), e);
            return makeErrorEntity(content.toString(), mapper);
        }
    }

    private <T extends BaseEntity> T makeErrorEntity(String msg, Class<T> mapper) {
        T r = null;
        try {
            Object c = Class.forName(mapper.getName()).newInstance();
            r = mapper.cast(c);
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            logger.error("Problem creating new {}", mapper.getName(), e);
        }
        r.addError(msg);
        return r;
    }


}
