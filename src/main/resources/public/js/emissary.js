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

function searchTable() {
  let input, filter, table, tr, td, i, txtValue;
  let result = 0;
  input = document.getElementById("searchInput");
  filter = input.value.toUpperCase();
  table = document.getElementById("tableToSearch");
  tr = table.getElementsByTagName("tr");

  for (i = 0; i < tr.length; i++) {
    td = tr[i].getElementsByTagName("td")[0];
    if (td) {
      txtValue = td.textContent || td.innerText;
      if (txtValue.toUpperCase().indexOf(filter) > -1) {
        tr[i].style.display = "";
        if (tr[i - 1].getElementsByTagName("td").length == 1) {
          tr[i - 1].style.display = "";
        }
        result += 1;
      } else {
        tr[i].style.display = "none";
      }
    }
  }
  if (result == 0) {
    document.getElementById("empty-message").style.display = "";
  } else {
    document.getElementById("empty-message").style.display = "none";
  }
}