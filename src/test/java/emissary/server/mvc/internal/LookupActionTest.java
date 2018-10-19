package emissary.server.mvc.internal;

import static org.junit.Assert.assertEquals;

import javax.ws.rs.core.Response;

import emissary.core.Namespace;
import emissary.server.mvc.EndpointTestBase;
import org.junit.Test;

public class LookupActionTest extends EndpointTestBase {

    @Test
    public void test() {
        String example = "something";
        Namespace.bind(example, "jomama");
        Response response = target("/Lookup.action").queryParam(LookupAction.NAME_PARAMETER, example).request().get();
        System.out.println(response.getStatus() + " : " + response.toString());
        assertEquals(200, response.getStatus());
        String body = response.readEntity(String.class);
        assertEquals(body, "jomama");
    }

}
