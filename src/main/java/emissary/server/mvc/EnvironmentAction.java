package emissary.server.mvc;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.server.mvc.Template;

@Path("")
// context is emissary
public class EnvironmentAction {

    @GET
    @Path("/Environment.action")
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/environment")
    public Map<String, Object> getEnvironment() {
        Map<String, Object> model = new HashMap<String, Object>();

        Set<JustAString> systemProperties = new TreeSet<>();
        Enumeration<?> e = System.getProperties().propertyNames();
        Set<String> keys = new TreeSet<String>();
        while (e.hasMoreElements()) {
            keys.add((String) e.nextElement());
        }
        for (String prop : keys) {
            systemProperties.add(new JustAString(prop + ": " + System.getProperty(prop)));
        }

        Set<JustAString> environmentVariables = new TreeSet<>();
        Map<String, String> m = System.getenv();
        for (String s : new TreeSet<String>(m.keySet())) {
            environmentVariables.add(new JustAString(s + ": " + m.get(s)));
        }

        model.put("systemproperties", systemProperties);
        model.put("environmentvariables", environmentVariables);

        return model;
    }

    static class JustAString implements Comparable<JustAString> {
        public String string;

        public JustAString(String string) {
            this.string = string;
        }

        @Override
        public int compareTo(JustAString o) {
            int len1 = string.length();
            int len2 = o.string.length();
            int lim = Math.min(len1, len2);
            char v1[] = string.toCharArray();
            char v2[] = o.string.toCharArray();

            int k = 0;
            while (k < lim) {
                char c1 = v1[k];
                char c2 = v2[k];
                if (c1 != c2) {
                    return c1 - c2;
                }
                k++;
            }
            return len1 - len2;
        }
    }
}
