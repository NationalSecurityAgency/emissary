package emissary.directory;

import emissary.util.xml.SaferJDOMUtil;

import jakarta.annotation.Nullable;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * This class acts as a container and producer for turning a directory entry list into a full xml document
 */
public class DirectoryXmlContainer {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryXmlContainer.class);

    public static final String DIRECTORY = "directory";
    public static final String LOC_ATTR = "location";
    public static final String DATAID_ATTR = "dataid";

    /**
     * Build an xml document from the contents of a directory place
     */
    public static Document buildDocument(final IDirectoryPlace dir) {
        final Element root = new Element(DIRECTORY);
        root.setAttribute(LOC_ATTR, dir.getKey());

        // Each directory entry
        for (final String dataId : dir.getEntryKeys()) {
            final DirectoryEntryList list = dir.getEntryList(dataId);
            if (list != null) {
                final Element listEl = list.getXml();
                listEl.setAttribute(DATAID_ATTR, dataId);
                root.addContent(listEl);
            }
        }

        return new Document(root);
    }

    /**
     * Build an xml document from the map.
     * 
     * @param map the map of entries
     * @param loc directory location key
     */
    public static Document buildDocument(final DirectoryEntryMap map, final String loc) {
        final Element root = new Element(DIRECTORY);
        root.setAttribute(LOC_ATTR, loc);

        // Each directory entry
        for (final Map.Entry<String, DirectoryEntryList> entry : map.entrySet()) {
            final String dataId = entry.getKey();
            final DirectoryEntryList list = entry.getValue();
            if (list != null) {
                final Element listEl = list.getXml();
                listEl.setAttribute(DATAID_ATTR, dataId);
                root.addContent(listEl);
            }
        }

        return new Document(root);
    }

    /**
     * Build an xml document from the contents of the directory as if proxied through the place itself. Except when the
     * entries actually belong to the requester.
     * 
     * @param dir the directory that will act as a proxy
     * @param requester the directory doing the requesting
     */
    public static Document buildProxyDocument(final IRemoteDirectory dir, final String requester) {
        final String proxyKey = dir.getKey();
        final Element root = new Element(DIRECTORY);
        root.setAttribute(LOC_ATTR, proxyKey);

        logger.debug("Building proxy view of dir contents for {}", requester);

        // Each directory entry
        for (final String dataId : dir.getEntryKeys()) {
            final DirectoryEntryList list = dir.getEntryList(dataId);
            logger.debug("List of {} for {} has {} entries", dataId, requester, list.size());
            // set up proxy for each entry
            for (final DirectoryEntry e : list) {
                e.proxyFor(proxyKey);
            }

            // Add them to the xml
            if (!list.isEmpty()) {
                final Element listEl = list.getXml();
                listEl.setAttribute(DATAID_ATTR, dataId);
                root.addContent(listEl);
            }
        }
        return new Document(root);
    }

    /**
     * Build an xml string from the contents of a directory place This method pulls the main entryMap of the directory.
     * 
     * @param dir the directory to pull the entryMap contents from
     * @return xml string representing the keys
     */
    public static String toXmlString(final IDirectoryPlace dir) {
        final Document jdom = buildDocument(dir);
        return SaferJDOMUtil.toString(jdom);
    }

    /**
     * Build an xml string from the contents of a directory place making it appear that the place represented by proxyKey
     * acts as a proxy for all or the keys in the directory map
     * 
     * @param dir the directory to pull the entryMap contents from
     * @param proxyKey place to appear as the proxy
     * @param requester the key of the remote directory that is requesting the local directory contents
     * @return xml string representing the proxied keys
     */
    public static String toXmlString(final IDirectoryPlace dir, @Nullable final String proxyKey, final String requester) {
        logger.debug("Building xml string for {}", requester);
        final String xml;
        if ((proxyKey == null) || !(dir instanceof IRemoteDirectory)) {
            xml = toXmlString(dir);
        } else {
            final Document jdom = buildProxyDocument((IRemoteDirectory) dir, requester);
            xml = SaferJDOMUtil.toString(jdom);
        }
        return xml;
    }

    /**
     * Build a DirectoryEntryList map from string xml
     */
    public static DirectoryEntryMap buildEntryListMap(final String xml) throws JDOMException {
        final Document jdom = SaferJDOMUtil.createDocument(xml);
        return buildEntryListMap(jdom);
    }

    /**
     * Build a DirectoryEntryList map from a JDOM document
     */
    public static DirectoryEntryMap buildEntryListMap(final Document jdom) {
        final Element el = jdom.getRootElement();
        return buildEntryListMap(el);
    }

    /**
     * Build a DirectoryEntryMap from a JDOM Element
     */
    public static DirectoryEntryMap buildEntryListMap(final Element el) {
        final DirectoryEntryMap map = new DirectoryEntryMap();
        final List<Element> entryLists = el.getChildren(DirectoryEntryList.ENTRYLIST);
        for (final Element listElement : entryLists) {
            final DirectoryEntryList d = DirectoryEntryList.fromXml(listElement);
            final String dataId = listElement.getAttributeValue(DATAID_ATTR);
            map.put(dataId, d);
        }
        logger.debug("Constructed map of {} directory entry lists from xml", map.size());

        return map;
    }

    /** This class is not meant to be instantiated. */
    private DirectoryXmlContainer() {}
}
