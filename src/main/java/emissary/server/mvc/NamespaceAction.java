package emissary.server.mvc;

import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.server.util.BaseResourcePathUtil;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.server.mvc.Template;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Path("")
// context is emissary
public class NamespaceAction {


    @GET
    @Path("/Namespace.action")
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/namespace")
    public Map<String, Object> getNamespace() {
        Map<String, Object> model = new HashMap<>();
        String baseResourcePath = BaseResourcePathUtil.getBaseResourcePath();
        model.put("baseResourcePath", baseResourcePath);
        Set<NamespaceInfo> namespaces = new LinkedHashSet<>();

        int rowCount = 0;
        for (String key : Namespace.keySet()) {
            String clz = rowCount++ % 2 == 0 ? "even" : "odd";
            namespaces.add(new NamespaceInfo(key, clz, baseResourcePath));
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
            Object tempValue;
            try {
                tempValue = Namespace.lookup(key);
            } catch (NamespaceException e) {
                tempValue = e.getMessage();
            }
            this.value = tempValue;
            this.valueClassName = this.value.getClass().getName();
            this.isDir = key.contains("DirectoryPlace");
            this.dumpUrl = context + "/DumpDirectory.action?targetDir=" + key;
            this.transferUrl = context + "/TransferDirectory.action?targetDir=" + key;
        }

    }

}
