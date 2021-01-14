$(document).ready(function () {

    $('header').prepend('<nav class="navbar navbar-expand-lg navbar-dark fixed-top bg-dark"></nav>');
    $('nav.navbar').append('<a class="navbar-brand" href="/">Emissary</a>');
    $('nav.navbar').append('<button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarSupportedContent" aria-controls="navbarSupportedContent" aria-expanded="false" aria-label="Toggle navigation"><span class="navbar-toggler-icon"></span></button>');
    $('nav.navbar').append('<div class="collapse navbar-collapse" id="navbarSupportedContent"><ul class="navbar-nav mr-auto"></ul><ul class="navbar-nav ml-auto"></ul></div>');

    $('ul.navbar-nav.mr-auto').append('<li class="nav-item"><a class="nav-link" href="/">Agents</a></li>');
    $('ul.navbar-nav.mr-auto').append('<li class="nav-item"><a class="nav-link" href="/emissary/Namespace.action">Namespace</a></li>');
    $('ul.navbar-nav.mr-auto').append('<li class="nav-item"><a class="nav-link" href="/emissary/DumpDirectory.action">Directories</a></li>');
    $('ul.navbar-nav.mr-auto').append('<li class="nav-item"><a class="nav-link" href="/emissary/Threaddump.action">Threads</a></li>');
    $('ul.navbar-nav.mr-auto').append('<li class="nav-item"><a class="nav-link" href="/emissary/Environment.action">Env</a></li>');
    $('ul.navbar-nav.mr-auto').append('<li class="nav-item"><button id="shutdown" class="btn btn-danger navbar-btn">Shutdown</a></li>');

    $('ul.navbar-nav.ml-auto').append('<li class="nav-item"><span id="emissary-version" class="nav-link"></span></li>');

    var url = window.location.pathname + window.location.search;
    $('ul.navbar-nav a[href="'+ url +'"]').parent().addClass('active');
    $('ul.navbar-nav a').filter(function() {
         return this.href == url;
    }).parent().addClass('active');

    // set the version
    $.get('/api/version', function( data ) {
        $('#emissary-version').html(data['response'][window.location.hostname + ":" + window.location.port]);
    });

    $('#shutdown').on('click', function(){
        if (confirm('Are you sure?')) {
            window.location = '/emissary/Shutdown.action';
        }
    });
});
