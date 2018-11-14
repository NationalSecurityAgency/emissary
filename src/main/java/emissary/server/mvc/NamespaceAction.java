package emissary.server.mvc;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import emissary.core.Namespace;
import emissary.core.NamespaceException;
import org.glassfish.jersey.server.mvc.Template;

@Path("")
// context is emissary
public class NamespaceAction {


    @GET
    @Path("/Namespace.action")
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/namespace")
    public Map<String, Object> getNamespace(@Context HttpServletRequest request) {
        Map<String, Object> model = new HashMap<String, Object>();
        Set<NamespaceInfo> namespaces = new LinkedHashSet<>();

        int rowCount = 0;
        for (Iterator<String> i = Namespace.keySet().iterator(); i.hasNext();) {
            String key = i.next();
            String clz = rowCount++ % 2 == 0 ? "even" : "odd";
            String context = request.getContextPath();
            namespaces.add(new NamespaceInfo(key, clz, context));
        }

        model.put("namespaces", namespaces);

        return model;
    }

    static class NamespaceInfo {
        final String key;
        final String clz;
        final Object value;
        final String valueClassName;
        final String dumpUrl;
        final String transferUrl;
        final boolean isDir;

        public NamespaceInfo(String key, String clz, String context) {
            this.key = key;
            this.clz = clz;
            Object tempValue = null;
            try {
                tempValue = Namespace.lookup(key);
            } catch (NamespaceException e) {
                tempValue = e.getMessage();
            }
            this.value = tempValue;
            this.valueClassName = this.value.getClass().getName();
            this.isDir = key.toString().indexOf("DirectoryPlace") > -1;
            this.dumpUrl = context + "/DumpDirectory.action?targetDir=" + key;
            this.transferUrl = context + "/TransferDirectory.action?targetDir=" + key;
        }

    }

}
