package emissary.server.mvc;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.collect.ImmutableMap;
import emissary.scripting.RubyConsole;
import org.glassfish.jersey.server.mvc.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("")
// context is emissary
public class ConsoleAction {

    private static final Logger LOG = LoggerFactory.getLogger(ConsoleAction.class);

    /**
     * Key for storing the console in the session is '{@value} '
     */
    public static final String RUBY_CONSOLE_ATTR = "rubyconsole";
    public static final String CONSOLE_COMMAND = "c";
    public static final String CONSOLE_COMMAND_STRING = "s";


    @GET
    @Path("/Console.action")
    @Produces(MediaType.TEXT_HTML)
    @Template(name = "/console")
    public Map<String, Object> rubyConsole() {
        Map<String, Object> map = new HashMap<>();
        map.put("emissary", ImmutableMap.of("version", new emissary.util.Version().toString()));
        return map;
    }

    @POST
    @Path("/Console.action")
    @Produces(MediaType.TEXT_PLAIN)
    public Response rubyConsolePost(@Context HttpServletRequest request) {

        RubyConsole console = getOrCreateConsole(request);

        String consoleOutput = null;

        try {
            final String cmd = request.getParameter(CONSOLE_COMMAND);
            if (cmd == null) {
                LOG.debug("Illegal <null> command to ruby console");
            } else if ("reset".equals(cmd)) {
                // reset command
                LOG.debug("Sending reset to ruby console");
                console.reset();
                // make sure we create a new console on the next run
                request.getSession(true).setAttribute(RUBY_CONSOLE_ATTR, null);
                consoleOutput = "Console Reset, Namespace reloaded";
            } else if ("eval".equals(cmd)) {
                // Eval command
                String commandString = request.getParameter(CONSOLE_COMMAND_STRING);
                if (commandString != null) {
                    // In case it comes in via html rather than xhr, strip the prompt
                    if (commandString.startsWith(console.getPrompt())) {
                        commandString = commandString.substring(console.getPrompt().length());
                    }

                    LOG.debug("Sending command " + commandString + " to ruby console");
                    final Object result;
                    final String stdout;
                    try {
                        result = console.evalAndWait(commandString, 60000);
                    } finally {
                        stdout = console.getStdout();
                        console.getStderr(); // to keep it from accumulating
                    }

                    if (result == null) {
                        consoleOutput = stdout + "nil";
                    } else {
                        consoleOutput = stdout + result.toString();
                    }

                }
                LOG.debug("Command was eval, but there was no command string!");
            } else {
                // Illegal command
                LOG.debug("Received an unexpected command " + cmd);
                consoleOutput = "Unexpected command '" + cmd + "'";
            }

            consoleOutput = consoleOutput.replaceAll("\n", "<br />\n");
        } catch (Exception ex) {
            LOG.debug("Exception in ruby console: " + ex.getMessage(), ex);
            consoleOutput = ex.getMessage();
        }

        // Set up stuff for the return
        request.setAttribute("MESSAGE", consoleOutput);
        request.setAttribute("PROMPT", console.getPrompt());

        // Figure out what type of response to send
        String reqBy = request.getHeader("X-Requested-With");
        if (reqBy == null) {
            reqBy = "unknown";
        }
        LOG.debug("xhr? " + reqBy);

        return Response.ok().entity(consoleOutput).build();
    }

    private RubyConsole getOrCreateConsole(HttpServletRequest request) {
        // Set up session and console, creating as needed
        final HttpSession session = request.getSession(true);
        RubyConsole console = (RubyConsole) session.getAttribute(RUBY_CONSOLE_ATTR);
        if (console == null) {
            LOG.debug("Creating a new ruby console, none found on session");
            try {
                console = RubyConsole.getConsole();
                session.setAttribute(RUBY_CONSOLE_ATTR, console);
            } catch (IOException rubyx) {
                LOG.warn("Cannot create ruby console", rubyx);
                return null; // TODO: do better here
            }
        } else {
            LOG.debug("Console already exists on session " + console);
        }
        return console;
    }


}
