$(document).ready(function(){
  openPageOnContent("content.html");
  openPageOnLeft("menu.html");
  addFooter("FOOTER");
});

function openPageOnContent(page){
  $('#content').load(page);
}

function openPageOnLeft(page){
  $('#left').load(page);
}

function addFooter(msg){
  $('#footer').html($('#footer').html() + "<p>" + msg + "</p>");
}
