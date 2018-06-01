var COMMAND_HISTORY = new Array();
var HISTORY_SIZE = 50;
var COMMAND_HISTORY_POS = 0;


// Handle the console form xhr
function consoleFormHook(cform, infotarget)
{
    // Check conditions
    if (!(cform && cform.s && cform.c))
    {
        log(infotarget,"Error in form setup!!!","errortext");
        return false;
    }

    // See what the input form
    var ctype = cform.c.value;
    var command = cform.s.value;
    var curl = cform.action;

    // Chomp
    if (command.charAt(command.length-1) == '\n')
    {
        command = command.substr(0,command.length-1);
    }

    // Remove the prompt and log the prompt
    if (command.substr(0,3) == "=> ")
    {
        command = command.substr(3);
        logprompt(infotarget);
    }

    // Save the command to the history
    while (COMMAND_HISTORY.length >= HISTORY_SIZE)
    {
        COMMAND_HISTORY.shift();
    }
    COMMAND_HISTORY.push(command);
    COMMAND_HISTORY_POS = COMMAND_HISTORY.length;

    // Log the command to the output area
    log(infotarget,command,'commandtext');

    var doAppend = function (req) {
        log(infotarget,req.responseText,'responsetext');
    };

    var doAppendError = function () {
        log(infotarget,'Error!!!','errortext');
    };

    // POST the ajax request with callbacks and a custom header
    var res = postSimpleXMLHttpRequest(curl+"?c="+ctype+"&s="+encodeURIComponent(command));
    res.addCallbacks(doAppend,doAppendError);

    // Clear out text area with a new prompt for the next command
    cform.s.value = "=> ";
    cform.s.focus();

    // Stop normal form submission
    return false;
}

// log the prompt
function logprompt(infotarget)
{
    if (infotarget)
    {
        infotarget.innerHTML = infotarget.innerHTML +
            "<span class='prompttext'>=&gt; </span>";

        // Scroll to the bottom
        infotarget.scrollTop = infotarget.scrollHeight;
    }
}

// Append a log message to the target and scroll to the bottom
function log(infotarget,message,ttype)
{
    var crstr = '<br />\n';

    if (infotarget)
    {
        infotarget.innerHTML = infotarget.innerHTML +
            "<span class='" + ttype + "'>" + message + "</span>" + crstr;

        // Scroll to the bottom
        infotarget.scrollTop = infotarget.scrollHeight;
    }
}

// Reset the console text and xhr over the mesage
function resetConsole(that,infotarget)
{
    // POST the ajax request with callbacks and a custom header
    var res = postSimpleXMLHttpRequest(that.action+"?c=reset");
    if (infotarget)
    {
        infotarget.innerHTML = '';
        log(infotarget,'Console reset','errortext');
    }
    that.s.value = "=> ";
    that.s.focus();
    return false;
}

// Props to Ian Bicking on
//http://groups.google.com/group/mochikit/browse_thread/thread/\
//   2ebf5fd36d4087b7/53b0b3e320500def?lnk=gst&q=post+content#53b0b3e320500def
function postSimpleXMLHttpRequest(url/*, ...*/)
{
    var self = MochiKit.Async;
    var req = self.getXMLHttpRequest();
    var postBody = '';
    if (arguments.length > 1)
    {
        var m = MochiKit.Base;
        postBody = m.queryString.apply(null, m.extend(null,arguments, 1));
    }
    req.open("POST", url, true);
    req.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');
    req.setRequestHeader('X-Requested-With','XMLHttpRequest');
    return self.sendXMLHttpRequest(req, postBody);
}


// Here is the function that we will use onclick for async hrefs
var clicklink = function (url) {
    return function (evt) {
        // prevent the normal 'click' action for a link
        evt.stopPropagation();
        evt.preventDefault();

        var doReplace = function (req) {
            $('infotarget').innerHTML = req.responseText;
        };

        var doReplaceError = function () {
            $('infotarget').innerHTML = 'Error!!!';
        };

        var res = MochiKit.Async.doSimpleXMLHttpRequest(url);
        res.addCallbacks(doReplace,doReplaceError);
    }
};

this.consoleKeyHandler = function(e) {
    var code = e.key().code;
    var ctrl = e.modifier()['ctrl'];
    
    if ((code==13 ||code == 77) && ctrl) // We have a Cntrl-Enter add a cr to the text area
    {
        var ta = document.getElementById('s');
        if (ta)
        {
            ta.value = ta.value + "\n";
            e.stop();
            return false;
        }
    }
    else if (code == 13 && !ctrl) // We have a regular enter keypress, submit the form
    {
        e.stop();
        consoleFormHook(document.getElementById('f'),document.getElementById('output'));
        return false;
    }
    else if (code == 38) // up arrow, check command history
    {
        historyUpArrow();
        e.stop();
        return false;
    }
    else if (code == 40) // down arrow
    {
        historyDownArrow();
        e.stop();
        return false;
    }
//     else
//     {
//         log(document.getElementById('output'),'key is ' + code + ' ctrl=' + ctrl,'errortext');
//     }
}

function historyUpArrow()
{
    if (COMMAND_HISTORY_POS-1 >= 0 && COMMAND_HISTORY.length > COMMAND_HISTORY_POS-1)
    {
        COMMAND_HISTORY_POS--;
        writeCommand(COMMAND_HISTORY[COMMAND_HISTORY_POS]);
    }
}

function historyDownArrow()
{
    if (COMMAND_HISTORY_POS+1 < COMMAND_HISTORY.length)
    {
        COMMAND_HISTORY_POS++;
        writeCommand(COMMAND_HISTORY[COMMAND_HISTORY_POS]);
    }
}

// Replace the command in the buffer with one from the history
function writeCommand(cmd)
{
    var buf = document.getElementById('s');
    if (buf)
    {
        buf.value = '=> ' + cmd;
        // Move cursor to end
        buf.selectionStart = buf.textLength;
        buf.selectionEnd = buf.textLength;
    }
}

// Run the converter to add the onclicks to the links
var convertA = function (linkelement) {
    MochiKit.DOM.addToCallStack(linkelement,'onclick',clicklink(linkelement.href));
};

// Hook in when page loaded to convert the links
var initpage = function () {
    MochiKit.Base.map(convertA,MochiKit.DOM.getElementsByTagAndClassName('a','async'));
    MochiKit.Signal.connect(document,"onkeyup",this,"consoleKeyHandler");
};

// Hook into the page load mechanism
MochiKit.DOM.addLoadEvent(initpage);
MochiKit.DOM.focusOnLoad("s");
