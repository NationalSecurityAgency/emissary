/*
  $Id$
 */

package emissary.util.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Url {

    private static final Logger logger = LoggerFactory.getLogger(Url.class);

    public static final int UNINITIALIZED = 0;
    public static final int GET = 1;
    public static final int POST = 2;
    public static final int HEAD = 3;

    static final String[] theMethodString = {"GET", "POST", "HEAD"};

    /**
     * process a url depending on the method specified
     * 
     * @param toProcess the URL resource to process with GET HEAD or POST
     */
    public static UrlData doUrl(final UrlData toProcess) {
        if (toProcess == null) {
            throw new IllegalArgumentException("Url.doUrl: null UrlData arg");
        }

        final int method = toProcess.getTheMethod();

        if (method == Url.GET) {
            return getUrl(toProcess);
        }

        if (method == Url.POST) {
            return postUrl(toProcess);
        }

        throw new IllegalArgumentException("UrlData method " + method + " not implemented in UrlData.doUrl");
    }

    /**
     * get a url
     * 
     * @param toGet describe where to GET from
     */
    public static UrlData getUrl(final UrlData toGet) {
        return getUrl(toGet.getTheUrl(), toGet.getProps());
    }

    /**
     * get a Url without any extra properties
     * 
     * @param urlString the URL resource to GET
     */
    public static UrlData getUrl(final String urlString) {
        return getUrl(urlString, null);
    }

    /**
     * Get a url, specifying additional header properties
     * 
     * @param urlString the URL resource
     * @param props properties to use on the connection
     */
    public static UrlData getUrl(final String urlString, final UrlRequestProperty[] props) {
        return processUrl(urlString, props, null, Url.GET);
    }

    /**
     * post to a url
     * 
     * @param toPost descript where to POST
     */
    public static UrlData postUrl(final UrlData toPost) {
        return postUrl(toPost.getTheUrl(), toPost.getProps(), null);
    }

    /**
     * post to a url
     * 
     * @param toPost describe where to POST
     * @param parms the POST data
     */
    public static UrlData postUrl(final UrlData toPost, final HttpPostParameters parms) {
        return postUrl(toPost.getTheUrl(), toPost.getProps(), parms);
    }

    /**
     * post a Url without any extra properties
     * 
     * @param urlString the URL resource to POST on
     */
    public static UrlData postUrl(final String urlString) {
        return postUrl(urlString, null, null);
    }

    /**
     * Post on a url, specifying additional header properties and params
     * 
     * @param urlString the URL resource
     * @param props array of properties to use
     * @param parms the POST data
     */
    public static UrlData postUrl(final String urlString, final UrlRequestProperty[] props, final HttpPostParameters parms) {

        // props.addProp(new UrlRequestProperty("Content-length",parms.length()));

        final UrlData u = new UrlData(urlString);
        u.setTheMethod(Url.POST);
        if (props != null) {
            u.setProps(props);
        }
        if (parms != null) {
            u.addProp(new UrlRequestProperty("Content-length", parms.length()));
        }

        return processUrl(u.getTheUrl(), u.getProps(), parms, u.getTheMethod());
    }

    /**
     * process (GET|POST|HEAD) a Url
     * 
     * @param urlString the URL resource
     * @param props array of properties to use as headers, must have Content-length if POSTing
     * @param parms parameters to use when POSTing
     * @param method GET, HEAD, POST
     */
    private static UrlData processUrl(final String urlString, final UrlRequestProperty[] props, final HttpPostParameters parms, final int method) {
        final UrlData response = new UrlData(urlString);

        final StringBuilder theOutput = new StringBuilder();
        OutputStream os = null;
        BufferedReader dis = null;
        try {
            final URL theUrl = new URL(urlString);
            final HttpURLConnection conn = (HttpURLConnection) theUrl.openConnection();

            // Set up for POST or other
            if (method == Url.POST) {
                conn.setDoOutput(true);
            } else {
                conn.setDoOutput(false);
            }
            conn.setDoInput(true);
            conn.setUseCaches(false);


            // Set user requested properties
            if (props != null) {
                for (int i = 0; i < props.length; i++) {
                    conn.setRequestProperty(props[i].getKey(), props[i].getValue());
                }
            }


            // Write post data if POSTing
            if (method == Url.POST && parms != null) {
                os = conn.getOutputStream();
                os.write(parms.toPostString().getBytes());
                os.flush();
            }

            // Get response code
            response.setResponseCode(conn.getResponseCode());

            // Get the headers
            final Map<String, String> headers = new HashMap<String, String>();
            String s;
            int hdr = 0;
            while ((s = conn.getHeaderField(hdr)) != null) {
                final String key = conn.getHeaderFieldKey(hdr);
                if (key != null) {
                    headers.put(key, s);
                } else {
                    headers.put("", s);
                }
                hdr++;
            }

            // Load headers into properties array
            final UrlRequestProperty[] theProps = new UrlRequestProperty[headers.size()];
            hdr = 0;

            for (final Map.Entry<String, String> entry : headers.entrySet()) {
                theProps[hdr] = new UrlRequestProperty(entry.getKey(), entry.getValue());
            }
            response.setProps(theProps);

            // Get page unless HEADing
            if (method != Url.HEAD) {
                dis = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                // Get the content
                String line;
                while ((line = dis.readLine()) != null) {
                    theOutput.append(line).append(System.getProperty("line.separator", "\n"));
                }

                response.setTheContent(theOutput.toString().getBytes());
            }
        } catch (MalformedURLException e) {
            logger.warn("Bad URL " + urlString, e);
        } catch (IOException e) {
            logger.warn("Read error on " + urlString, e);
        } catch (OutOfMemoryError e) {
            logger.warn("Not enough space for read on " + urlString, e);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ioe) {
                    logger.warn("Unable to close stream", ioe);
                }
            }
            if (dis != null) {
                try {
                    dis.close();
                } catch (IOException ioe) {
                    logger.warn("Unable to close stream", ioe);
                }
            }
        }

        return response;
    }

    /** This class is not meant to be instantiated. */
    private Url() {}
}
