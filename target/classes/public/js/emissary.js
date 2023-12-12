$(document).ready(function () {
    $.ajaxSetup({
        beforeSend: function(xhr, settings) {
            if (!(/^(GET|HEAD|OPTIONS)$/.test(settings.type)) && !this.crossDomain) {
                xhr.setRequestHeader('X-Requested-By', 'emissary');
            }
        }
    });

    $.get('/emissary/Nav.action', function( data ) {
        $('header').prepend(data);

        var url = window.location.pathname;
        $('ul.navbar-nav a[href="'+ url +'"]').parent().addClass('active');
        $('ul.navbar-nav a').filter(function() {
             return this.href == url;
        }).parent().addClass('active');

        document.title = document.title + ' - ' + $('#app-name').text();
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