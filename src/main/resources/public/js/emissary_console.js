var COMMAND_HISTORY = new Array();
var HISTORY_SIZE = 50;
var COMMAND_HISTORY_POS = 0;


// Handle the console form xhr
function consoleFormHook(cform, infotarget){
    // Check conditions
    if (!(cform && cform.s && cform.c)){
        log(infotarget,"Error in form setup!!!","errortext");
        return false;
    }

    // See what the input form
    var ctype = cform.c.value;
    var command = cform.s.value;
    var curl = cform.action;

    // Chomp
    if (command.charAt(command.length-1) == '\n'){
        command = command.substr(0,command.length-1);
    }

    // Remove the prompt and log the prompt
    if (command.substr(0,3) == "=> "){
        command = command.substr(3);
        logprompt(infotarget);
    }

    // Save the command to the history
    while (COMMAND_HISTORY.length >= HISTORY_SIZE){
        COMMAND_HISTORY.shift();
    }
    COMMAND_HISTORY.push(command);
    COMMAND_HISTORY_POS = COMMAND_HISTORY.length;

    // Log the command to the output area
    log(infotarget,command,'commandtext');

    // POST the ajax request with callbacks and a custom header
    var res = $.post(
        `${curl}?c=${ctype}&s=${encodeURIComponent(command)}`,
        function (req) { log(infotarget,req,'responsetext'); }
    ).fail(
        function () { log(infotarget,'Error!!!','errortext'); }
    );

    clear(cform);
    // Stop normal form submission
    return false;
}

// log the prompt
function logprompt(infotarget) {
    if (infotarget) {
        infotarget.html(`${infotarget.html()}<span class='prompttext'>=&gt; </span>`);
        // Scroll to the bottom
        infotarget.scrollTop(infotarget[0].scrollHeight);
    }
}

// Append a log message to the target and scroll to the bottom
function log(infotarget,message,ttype) {
    var crstr = '<br />\n';

    if (infotarget) {
        infotarget.html(`${infotarget.html()}<span class="${ttype}">${message}</span>${crstr}`);
        // Scroll to the bottom
        infotarget.scrollTop(infotarget[0].scrollHeight);
    }
}

// Reset the console text and xhr over the mesage
function resetConsole(that,infotarget) {
    // POST the ajax request with callbacks and a custom header
    var res = $.post(`${that.action}?c=reset`, function(){});
    if (infotarget){
        infotarget.html('');
        log(infotarget,'Console reset','errortext');
    }
    clear(that);
    // Stop normal form submission
    return false;
}

function clear(form) {
    form.s.value = "=> ";
    form.s.focus();
}

function consoleKeyHandler(e) {
    var code = e.originalEvent.keyCode;
    var ctrl = e.originalEvent.ctrlKey;
    if ((code==13 ||code == 77) && ctrl) { // We have a Cntrl-Enter add a cr to the text area
        var ta = $('#s');
        if (ta) {
            ta.val(ta.val() + "\n");
            e.stopPropagation();
            return false;
        }
    } else if (code == 13 && !ctrl) { // We have a regular enter keypress, submit the form
        e.stopPropagation();
        consoleFormHook($('#f')[0], $('#output'));
        return false;
    } else if (code == 38) { // up arrow, check command history
        historyUpArrow();
        e.stopPropagation();
        return false;
    } else if (code == 40) { // down arrow
        historyDownArrow();
        e.stopPropagation();
        return false;
    }
}

function historyUpArrow() {
    if (COMMAND_HISTORY_POS-1 >= 0 && COMMAND_HISTORY.length > COMMAND_HISTORY_POS-1) {
        COMMAND_HISTORY_POS--;
        writeCommand(COMMAND_HISTORY[COMMAND_HISTORY_POS]);
    }
}

function historyDownArrow() {
    if (COMMAND_HISTORY_POS+1 < COMMAND_HISTORY.length) {
        COMMAND_HISTORY_POS++;
        writeCommand(COMMAND_HISTORY[COMMAND_HISTORY_POS]);
    }
}

// Replace the command in the buffer with one from the history
function writeCommand(cmd) {
    var buf = $('#s');
    if (buf) {
        buf.val('=> ' + cmd);
        // Move cursor to end
        buf.focus();
        buf[0].setSelectionRange(buf.val().length, buf.val().length);
    }
}

$(document).ready(function () {
    $(document).keyup(consoleKeyHandler);
    $('#s').focus();
    $('#clear').on('click', function(){
        $('#output').html('');
        $('#s').focus();
    });
});