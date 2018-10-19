package emissary.server.mvc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import emissary.core.IBaseDataObject;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.kff.KffDataObjectHandler;
import emissary.parser.ParserException;
import emissary.parser.ParserFactory;
import emissary.parser.SessionParser;
import emissary.parser.SessionProducer;
import emissary.place.sample.WebSubmissionPlace;
import emissary.pool.MoveSpool;
import emissary.util.PayloadUtil;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.server.mvc.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("")
// context is emissary
public class DocumentAction {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentAction.class);

    public static final String UUID_TOKEN = "token";
    public static final String SUBMISSION_TOKEN = "SUBMISSION_TOKEN";

    @GET
    @Path("/Document.action")
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/document_form")
    public Map<String, Object> documentForm() {
        Map<String, Object> map = new HashMap<>();
        return map;
    }

    @GET
    @Path("/Document.action/{uuid}")
    @Produces(MediaType.APPLICATION_XML)
    public Response documentShow(@Context HttpServletRequest request, @PathParam("uuid") String uuid) {

        try {
            final WebSubmissionPlace wsp = (WebSubmissionPlace) Namespace.lookup("WebSubmissionPlace");
            final List<IBaseDataObject> payload = wsp.take(uuid);
            if (payload != null) {
                LOG.debug("Found payloads for token {}", uuid);
                List<IBaseDataObject> uncheckedPayloadList = (List<IBaseDataObject>) payload;
                List<IBaseDataObject> payloadList = new ArrayList<IBaseDataObject>();
                for (Object o : uncheckedPayloadList) {
                    if (o instanceof IBaseDataObject) {
                        payloadList.add((IBaseDataObject) o);
                    }
                }
                String xml = PayloadUtil.toXmlString(payloadList);
                return Response.ok().entity(xml).build();
            } else {
                return Response.status(400).entity("<error>uuid " + uuid + " not found</error>").build();
            }
        } catch (NamespaceException e) {
            LOG.error("WebSubmissionPlace error", e);
            return Response.status(500).entity("<error>" + e.getMessage() + "</error>").build();
        } catch (Exception e) {
            LOG.error("Error on {}", uuid, e);
            return Response.status(500).entity("<error>" + e.getMessage() + "</error>").build();

        }
    }

    @POST
    @Path("/Document.action")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/document_submit")
    public Map<String, Object> documentSubmit(@Context HttpServletRequest request, @FormDataParam("source") String source,
            @FormDataParam("initial_form") String initialForm, @FormDataParam("document") InputStream uploadedInputStream,
            @FormDataParam("document") FormDataContentDisposition fileDetail) {
        Map<String, Object> map = new HashMap<>();
        // setup uuid for link
        final String uuid = UUID.randomUUID().toString();

        String uploadedFileLocation = System.getProperty("java.io.tmpdir") + fileDetail.getFileName();
        try {
            uploadFile(uploadedInputStream, fileDetail, uploadedFileLocation);
            map.put("uuid", uuid);
            map.put("link", request.getContextPath() + "/Document.action/" + uuid);
        } catch (IOException e) {
            LOG.error("Failed to upload {}", uploadedFileLocation);
            map.put("error", true);
            map.put("error-message", "Failed to upload " + fileDetail.getFileName() + ": " + e.getMessage());
        }

        try {
            processDocument(source, initialForm, fileDetail.getFileName(), uuid, uploadedFileLocation);
        } catch (NamespaceException | ParserException | IOException e) {
            LOG.error("Error processing file", e);
            map.put("error", true);
            map.put("error-message", "Failed to process" + fileDetail.getFileName() + ": " + e.getMessage());
        }

        return map;
    }

    private void processDocument(String source, String initialForm, String filename, final String uuid, String uploadedFileLocation)
            throws NamespaceException, IOException, ParserException {
        LOG.debug("Initial form: {} ", initialForm);
        LOG.debug("Source: {}", source);
        String webSubmissionProxyForm = null;
        if (source != null && source.equals("form")) {
            // store the data in RAM for later retrieval
            final WebSubmissionPlace wsp = (WebSubmissionPlace) Namespace.lookup("WebSubmissionPlace");
            webSubmissionProxyForm = wsp.getPrimaryProxy();
            LOG.debug("Set webSubmissionProxyForm to {}, storing document in RAM for later retrieval", webSubmissionProxyForm);
        }
        KffDataObjectHandler kff =
                new KffDataObjectHandler(KffDataObjectHandler.TRUNCATE_KNOWN_DATA, KffDataObjectHandler.SET_FORM_WHEN_KNOWN,
                        KffDataObjectHandler.SET_FILE_TYPE);

        int sessionNum = 0;
        final SessionParser sp = new ParserFactory().makeSessionParser(Files.newByteChannel(Paths.get(uploadedFileLocation)));
        final SessionProducer producer = new SessionProducer(sp, "WEB_SUBMISSION", null);
        final List<IBaseDataObject> payloadList = new ArrayList<IBaseDataObject>();

        while (true) {
            try {
                final String sessionName = filename + "-" + (sessionNum + 1);
                final IBaseDataObject payload = producer.getNextSession(sessionName);
                sessionNum++;
                for (final String current_form : initialForm.split(",")) {
                    payload.enqueueCurrentForm(current_form.trim());
                }
                payloadList.add(payload);

                // Add the form for the web submission storage place
                // If this is null, a copy of the results will NOT be
                // stored in RAM for later retrieval. Regardless of this
                // setting, all results will still go through their normal
                // output paths (i.e. DropOff)
                if (webSubmissionProxyForm != null) {
                    payload.enqueueCurrentForm(webSubmissionProxyForm);
                }

                // Run the kff chain and set up initial params
                kff.hash(payload);
                payload.setParameter(SUBMISSION_TOKEN, uuid);
                payload.setParameter("Original-Filename", filename);
                payload.setParameter("FILE_DATE", emissary.util.TimeUtil.getCurrentDateISO8601());
            } catch (emissary.parser.ParserEOFException eof) {
                break; // expected and end of file
            }
        }

        LOG.debug("Ready to assign payload to an agent");
        final MoveSpool spool = MoveSpool.lookup();
        spool.send(payloadList);
        LOG.debug("Payload has been spooled ({})", payloadList.size());
    }

    private void uploadFile(InputStream uploadedInputStream, FormDataContentDisposition fileDetail, String uploadedFileLocation) throws IOException {
        File objFile = new File(uploadedFileLocation);
        if (objFile.exists()) {
            objFile.delete();
        }
        OutputStream out = null;
        int read = 0;
        byte[] bytes = new byte[1024];

        out = new FileOutputStream(new File(uploadedFileLocation));
        while ((read = uploadedInputStream.read(bytes)) != -1) {
            out.write(bytes, 0, read);
        }
        out.flush();
        out.close();
        LOG.debug("Uploaded file to {}", uploadedFileLocation);
    }

}
