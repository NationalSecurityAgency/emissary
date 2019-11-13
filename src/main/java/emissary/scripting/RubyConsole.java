package emissary.scripting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;

import emissary.config.ConfigUtil;
import emissary.config.Configurator;
import emissary.core.EmissaryException;
import emissary.core.Namespace;
import emissary.core.NamespaceException;
import emissary.directory.DirectoryPlace;
import emissary.directory.EmissaryNode;
import emissary.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide access to the running emissary node via with a ruby scripting interpreter The RubyConsole is normally run on
 * a new Thread per user session since ruby non-global variables are created on Java ThreadLocal context and we cannot
 * tell which thread an incoming user request might operate on. It might be one of the Jetty Listener Pool threads if
 * coming from our console.jsp on an XHR.
 *
 * This means, at a mininum, that we need to be very careful about leaking threads, hence the session binding listener
 * to make sure everything gets cleaned up
 */
public class RubyConsole implements HttpSessionBindingListener, Runnable {
    // Our logger
    private Logger logger = LoggerFactory.getLogger(RubyConsole.class);

    // The basic ruby/irb prompt
    public static final String PROMPT = "jruby-emissary> ";

    // The configuration feed
    protected Configurator config;

    // For thread naming
    protected static final String THREAD_NAME = "RubyConsole-";
    protected static int THREAD_COUNTER = 1;
    protected String threadName;

    // For thread control
    protected boolean timeToQuit = false;

    // Current string to eval and result
    protected volatile String stringToEval = null;
    protected volatile Object result = null;
    protected volatile boolean resultCompleted = false;

    // IO for the engine
    protected ByteArrayInputStream stdin = new ByteArrayInputStream(new byte[0]);
    protected ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    protected ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    /**
     * We need one script engine manager
     */
    protected ScriptEngineManager manager;

    /**
     * One Script engine and context for ruby per place
     */
    protected ScriptEngine rubyEngine;
    protected ScriptContext rubyContext;

    static {
        System.setProperty("org.jruby.embed.localcontext.scope", "concurrent");
        System.setProperty("org.jruby.embed.localvariable.behavior", "persistent");
    }

    /**
     * Create a new ruby console on a new thread
     */
    public static RubyConsole getConsole() throws IOException {
        RubyConsole console = new RubyConsole();
        Thread thread = new Thread(console, THREAD_NAME + (THREAD_COUNTER++));
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        console.threadName = thread.getName();
        thread.start();
        return console;
    }

    /**
     * Create a ruby console
     */
    public RubyConsole() throws IOException {
        loadEngine();
        loadContext();
        loadNamespaceIntoContext();
    }

    /**
     * Create a ruby console with the specified config data
     *
     * @param config the config data
     */
    public RubyConsole(Configurator config) throws IOException {
        this.config = config;
        loadEngine();
        loadContext();
        loadNamespaceIntoContext();
    }

    protected void loadEngine() throws IOException {
        if (manager == null) {
            manager = new ScriptEngineManager();
        }
        rubyEngine = manager.getEngineByName("jruby");
        if (rubyEngine == null) {
            logger.warn("Cannot load jruby execution engine, missing jruby-engine.jar?");
            throw new IOException("Cannot load jruby execution engine");
        }
        rubyContext = rubyEngine.getContext();
    }


    /**
     * Required Runnable interface,used to start the console thread Runs until timeToQuit is set by calling stop
     */
    @Override
    public void run() {
        while (!timeToQuit) {
            synchronized (this) {
                if (stringToEval == null) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // empty catch block
                    }
                } else {
                    try {
                        result = this.eval(stringToEval);
                        resultCompleted = true;
                        if (logger.isDebugEnabled()) {
                            logger.debug("Stored result type " + (result != null ? result.getClass().getName() : "<null>"));
                        }
                    } catch (Exception ex) {
                        result = ex;
                        resultCompleted = true;
                        if (logger.isDebugEnabled()) {
                            logger.debug("Stored result type Exception");
                        }
                    } finally {
                        stringToEval = null;
                        if (logger.isDebugEnabled()) {
                            logger.debug("Done eval on thread, ready to notifyAll");
                        }
                        notifyAll();
                        Thread.yield();
                    }
                }
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Off the end of the ruby console thread");
        }
    }

    /**
     * Load some basic context into the ruby interpreter Lines to load come from the config file for this class
     */
    protected void loadContext() {
        int lineNumber = 1;
        if (config == null) {
            try {
                config = ConfigUtil.getConfigInfo(RubyConsole.class);
            } catch (Exception ex) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Cannot get default config stream", ex);
                }
                return;
            }
        }

        // Setup io
        rubyContext.setWriter(new PrintWriter(stdout, true));
        rubyContext.setErrorWriter(new PrintWriter(stderr, true));

        // Get lines of context from the config
        List<String> lines = config.findEntries("CONTEXT");

        // Run each line in order
        for (String expression : lines) {
            try {
                eval(expression);
            } catch (Exception ex) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Exception in init (" + lineNumber + "): " + expression);
                }
            } finally {
                lineNumber++;
            }
        }

        // Declare the logger into the scripting context
        rubyContext.setAttribute("@logger", logger, ScriptContext.ENGINE_SCOPE);
    }

    /**
     * Load everything that is in this node's namespace into the JRuby context so that the scripting engine can access all
     * of these things by the name they were bound with
     */
    protected void loadNamespaceIntoContext() {
        for (String key : Namespace.keySet()) {
            try {
                Object obj = Namespace.lookup(key);
                // Get the short name if there is one
                if (key.indexOf("/") > 0) {
                    key = key.substring(key.lastIndexOf("/") + 1);
                }
                // Clean up meta chars
                key = key.replaceAll("[-'\"?><{}\\[\\]&$!@#^*()+=]", "");
                // Convert to lower-camel-case
                if (Character.isUpperCase(key.charAt(0))) {
                    key = Character.toLowerCase(key.charAt(0)) + key.substring(1);
                }

                rubyContext.setAttribute("@" + key, obj, ScriptContext.ENGINE_SCOPE);
                if (logger.isDebugEnabled()) {
                    logger.debug("Declared attribute @" + key);
                }
            } catch (NamespaceException ex) {
                logger.warn("Unable to provide scripting access to " + key + ": " + ex.getMessage());
            } catch (Exception ex) {
                logger.warn("Scripting Error declaring " + key, ex);
            }
        }
        rubyContext.setAttribute("@showMobileAgents", Version.mobileAgents, ScriptContext.ENGINE_SCOPE);
    }

    /**
     * Set up a ruby expression to be evaluated, wait for it to be done and return the result. The evaluation happens on the
     * ruby console thread, not the callers thread
     *
     * @param expression the ruby expression to evaluate
     * @param limit the max time to wait for the answer in millis sending a limit of 0 causes nowait and must call getResult
     *        yourself if you want the result (async model). Calling limit with a number greater than 0 will wait at most
     *        that length of time for the result to be ready. A limit of &lt; 0 is currently undefined.
     */
    public Object evalAndWait(String expression, long limit) throws Exception {
        synchronized (this) {
            result = null;
            resultCompleted = false;
            stringToEval = expression;
            notifyAll(); // fire the run loop to pick up our expression
            Thread.yield();
        }

        // no wait
        if (limit == 0) {
            return null;
        }

        Object result = getResult(limit);

        if (logger.isDebugEnabled()) {
            logger.debug("GetResult gives back a " + (result != null ? result.getClass().getName() : "<null>"));
        }

        if (result != null && result instanceof Exception) {
            throw (Exception) result;
        }

        else if (result != null && result instanceof org.jruby.RubyNil) {
            return null;
        }

        return result;
    }

    /**
     * Get the accumulated stdout and clear the buffer
     */
    public String getStdout() {
        String s = stdout.toString();
        stdout.reset();
        return s;
    }

    /**
     * Get the accumulated stderr and clear the buffer
     */
    public String getStderr() {
        String s = stderr.toString();
        stderr.reset();
        return s;
    }


    /**
     * Wait for the result of a ruby expression to be ready
     *
     * @param limit the max time to wait for the answer in millis
     */
    public Object getResult(long limit) {
        long start = System.currentTimeMillis();
        long now = start;
        while (now < (start + limit) && !resultCompleted) {
            try {
                synchronized (this) {
                    wait(limit);
                }
            } catch (InterruptedException e) {
                // empty catch block
            }
            now = System.currentTimeMillis();
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Total time in getResult(" + limit + ") was " + (now - start) + "ms");
        }
        return result;
    }

    /**
     * Evaluate an incoming ruby expression on the callers thread
     *
     * @param expression ruby expression to evaluate
     * @return the result of the expression
     */
    public Object eval(String expression) throws Exception {
        Object result = null;
        try {
            result = rubyEngine.eval(expression, rubyContext);
            if (logger.isDebugEnabled()) {
                logger.debug("eval(" + expression + ") => " + (result != null ? result.toString() : "<null>") + " ["
                        + (result != null ? result.getClass().getName() : "<null>") + "]");
            }
        } catch (Exception ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("eval(" + expression + ") => " + ex.getMessage(), ex);
                if (ex.getCause() != null) {
                    logger.debug("Caused by: ", ex.getCause());
                }
            }
            throw ex;
        }
        return result;
    }

    /**
     * Set the console thread to stop soon
     */
    public void stop() {
        timeToQuit = true;
        synchronized (this) {
            notifyAll();
        }
    }

    /**
     * Reset the context
     */
    public void reset() throws IOException {
        if (rubyEngine != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Terminating previous engine");
            }
            rubyEngine = null;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("RubyConsole reset, creating a new Engine");
        }
        loadEngine();
        loadContext();
        loadNamespaceIntoContext();
    }

    public String getPrompt() {
        return PROMPT;
    }

    /**
     * From javax.servlet.http.SessionBindingListener
     */
    @Override
    public void valueBound(HttpSessionBindingEvent e) {
        if (logger.isDebugEnabled()) {
            logger.debug("Received value bound event on " + e.getName() + " from session " + e.getSession().getId());
        }
    }

    /**
     * From javax.servlet.http.SessionBindingListener
     */
    @Override
    public void valueUnbound(HttpSessionBindingEvent e) {
        if (logger.isDebugEnabled()) {
            logger.debug("Received value unbound event on " + e.getName() + " from session " + e.getSession().getId());
        }
        this.stop();
    }

    /**
     * Simulate some type of context when running from a static main
     */
    public static void createEmissaryContext() throws EmissaryException {
        // Do some of the things that the normal context initializer does
        boolean pseudoNodeCreated = false;
        try {
            EmissaryNode node = new EmissaryNode();
            if (!node.isValid()) {
                throw new EmissaryException("Invalid EmissaryNode node:" + node.getNodeName() + " port:" + node.getNodePort() + " type:"
                        + node.getNodeType());
                // System.setProperty(EmissaryNode.NODE_NAME_PROPERTY, "localhost");
                // System.setProperty(EmissaryNode.NODE_PORT_PROPERTY, "8001");
                // node = new EmissaryNode();
                // pseudoNodeCreated = true;
            }
            new DirectoryPlace("EMISSARY_DIRECTORY_SERVICES.STUDY.DIRECTORY_PLACE.http://" + node.getNodeName() + ":" + node.getNodePort()
                    + "/DirectoryPlace", node);
        } catch (IOException iox) {
            // logger.debug("Could not create standalone directory",iox);
        } finally {
            if (pseudoNodeCreated) {
                // System.clearProperty(EmissaryNode.NODE_NAME_PROPERTY);
                // System.clearProperty(EmissaryNode.NODE_PORT_PROPERTY);
            }
        }
    }


    /**
     * Run a console from the command line
     */
    public static void main(String[] args) throws Exception {
        createEmissaryContext();
        RubyConsole console = new RubyConsole();
        if (args.length == 0) {
            console.shell();
        } else if (args[0].equals("-e")) {
            if (args.length > 2) {
                console.rubyContext.setAttribute("ARGS", Arrays.copyOfRange(args, 2, args.length), ScriptContext.ENGINE_SCOPE);
            }
            console.evalConsoleInput(args[1], false);
        } else {
            if (args.length > 1) {
                console.rubyContext.setAttribute("ARGS", Arrays.copyOfRange(args, 1, args.length), ScriptContext.ENGINE_SCOPE);
            }
            console.evalConsoleInput(new String(emissary.util.shell.Executrix.readDataFromFile(args[0])), false);
        }
    }


    public void shell() {
        // JDK 1.6 +
        Console jconsole = System.console();
        if (jconsole == null) {
            System.out.println("no tty");
            return;
        }

        // Put some nice words on screen for the user
        System.out.println("Emissary Ruby Console v" + new emissary.util.Version());
        String input;
        do {
            try {
                System.out.print(getPrompt());
                input = jconsole.readLine();
                if (input == null || input.equals("quit")) {
                    break;
                }

                if (input.equals("reset")) {
                    reset();
                    continue;
                }

                evalConsoleInput(input, true);
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        } while (true);
    }

    protected void evalConsoleInput(String input, boolean interactive) throws Exception {
        Object result = eval(input);
        String sout = getStdout();
        if (sout != null && sout.length() > 0) {
            System.out.print(sout);
        }
        String serr = getStderr();
        if (serr != null && serr.length() > 0) {
            System.err.print(serr);
        }
        if (interactive) {
            System.out.println("=> " + result);
        }
    }

}
