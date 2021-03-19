$(document).ready(function () {
    $.ajaxSetup({
        beforeSend: function(xhr, settings) {
            if (!(/^(GET|HEAD|OPTIONS)$/.test(settings.type)) && !this.crossDomain) {
                xhr.setRequestHeader('X-Requested-By', 'emissary');
            }
        }
    });

    $.get('/api/nav', function( data ) {
        document.title = document.title + ' - ' + data.appName;
        $('header').prepend('<nav class="navbar navbar-expand-lg navbar-dark fixed-top bg-dark"></nav>');
        $('nav.navbar').append('<a class="navbar-brand" href="/">' + data.appName + '</a>');
        $('nav.navbar').append('<button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarSupportedContent" aria-controls="navbarSupportedContent" aria-expanded="false" aria-label="Toggle navigation"><span class="navbar-toggler-icon"></span></button>');
        $('nav.navbar').append('<div class="collapse navbar-collapse" id="navbarSupportedContent"><ul class="navbar-nav mr-auto"></ul><ul class="navbar-nav ml-auto"></ul></div>');

        $.each(data.navItems, function (display, link) {
            $('ul.navbar-nav.mr-auto').append('<li class="nav-item"><a class="nav-link" href="'+link+'">'+display+'</a></li>');
        });

        $.each(data.navButtons, function (display, link) {
            $('ul.navbar-nav.mr-auto').append('<li class="nav-item"><button id="'+display+'" class="btn btn-danger navbar-btn" style="margin: 2px 2px 0px 0px;">'+display+'</a></li>');
            $('#' + display).on('click', function(){
                if (confirm('Are you sure?')) {
                    window.location = link;
                }
            });
        });

        $('ul.navbar-nav.ml-auto').append('<li class="nav-item"><span id="emissary-version" class="nav-link">'+data.appVersion+'</span></li>');

        var url = window.location.pathname;
        $('ul.navbar-nav a[href="'+ url +'"]').parent().addClass('active');
        $('ul.navbar-nav a').filter(function() {
             return this.href == url;
        }).parent().addClass('active');
    });
});

function doPost( url, messageHolderId) {
    $.post(url)
        .done(function(data){
            $("#" + messageHolderId).append(data);
        })
        .fail(function(){
            $("#" + messageHolderId).append("request failed!");
        });
}
