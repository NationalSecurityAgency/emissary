package emissary.util;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.TreeMap;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Map HTML entities
 */
public class HtmlEntityMap {

    private static final Logger logger = LoggerFactory.getLogger(HtmlEntityMap.class);

    protected Map<String, String> entityMap = new TreeMap<String, String>();

    public HtmlEntityMap() {
        configure();
    }

    protected void configure() {
        try {
            Configurator configG = ConfigUtil.getConfigInfo(this.getClass());
            Map<String, String> emap = configG.findStringMatchMap("ENTITY_", true);
            entityMap.putAll(emap);
            logger.debug("Read " + entityMap.size() + " entities from config");
        } catch (IOException ex) {
            logger.warn("Cannot read configuration", ex);
        }
    }

    /**
     * Give the value for the specified entity from the configuration file
     * 
     * @param entity can be with or without ampersand and semi-colon
     * @return the configured value or null if unknown
     */
    public String getValueForHTMLEntity(String entity) {
        if (entity.startsWith("&")) {
            if (entity.endsWith(";")) {
                entity = entity.substring(1, entity.length() - 1);
            } else {
                entity = entity.substring(1, entity.length());
            }
        }
        return entityMap.get(entity);
    }

    public void dumpTestPage(PrintStream out) {
        out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml11-strict.dtd\">");
        out.println("<html lang='en'>");
        out.println("  <head>");
        out.println("    <meta http-equiv='Content-Type' content='text/html; charset=utf-8' />");
        out.println("    <title>Html Entity Test Page</title>");
        out.println("  </head>");
        out.println("  <body>");
        out.println("    <table>");
        out.println("      <thead>");
        out.println("        <tr>");
        out.println("          <th>Name</th><th>Number</th><th>As entity</th><th>As Numeric</th><th>As UTF-8</th>");
        out.println("        </tr>");
        out.println("      </thead>");
        out.println("      <tbody>");
        for (String ent : entityMap.keySet()) {
            String val = getValueForHTMLEntity(ent);
            int cpc = val.codePointCount(0, val.length());
            out.print("        <tr><td>&amp;" + ent + ";</td>");
            if (cpc == 1) {
                out.print("<td>" + val.codePointAt(0) + "</td>");
            } else {
                out.print("<td></td>");
            }
            out.print("<td>&" + ent + ";</td>");
            if (cpc == 1) {
                out.print("<td>&#" + val.codePointAt(0) + ";</td>");
            } else {
                out.println("<td></td>");
            }
            out.print("<td>" + val + "</td>");
            out.println("</tr>");
        }
        out.println("      </tbody>");
        out.println("    </table>");
        out.println("  </body>");
        out.println("</html>");
    }

    public static void main(String[] args) {
        HtmlEntityMap h = new HtmlEntityMap();
        h.dumpTestPage(System.out);
    }
}
