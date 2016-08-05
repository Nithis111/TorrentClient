# Search Engine format

```javascript
{
  // version of file, if different from loaded by client will update
  "version": 3,
  // showed in navigation view
  "name": "rutracker.org",
  // home page of search engine, not used right now
  "home": {
    "get": "http://rutracker.org"
  },
  // optional. if present additional action possible - login
  "login": {
    // GET what url should we to login?
    "get": "http://rutracker.org/forum/login.php",
    // POST what url should we to login?
    "post": "http://rutracker.org/forum/login.php",
    // what login form field name?
    "post_login": "login_username",
    // what password form field name?
    "post_password": "login_password",
    // optional. additional params url encoded splitted by ";" and "="
    "post_params": "login=вход",
    // optional. second login option, what browser page sholud we use for manual login?
    "details": "http://rutracker.org/forum/login.php",
    // optional. js we need to execute after page loaded
    "js": "if(document.querySelectorAll('a[href^=\\\\.\\\\/profile\\\\.php]').length==0){alert('Not Logged In')}"
  },
  // search operation
  "search": {
    // POST url
    "post": "http://rutracker.org/forum/tracker.php",
    // post search arg query
    "post_search": "nm",
    // optional. js we need to execute after page loaded
    "js": "if(document.querySelectorAll('a[href^=\\\\.\\\\/profile\\\\.php]').length==0){alert('Not Logged In')};ee=document.querySelectorAll('a.tLink');for(i=0;i<ee.length;i++){e=ee[i];h=e.getAttribute('href');h='http://rutracker.org/forum/'+h;e.setAttribute('href',h);};",
    // list torrents selector
    "list": ".forumline.tablesorter tbody tr",
    // title of torrent
    "title": "a.tLink:regex(<a[^>]*>(.*)</a>)",
    // optional. details url to show if user click on torrent
    "details": "a.tLink:regex(.*href=[\"'](.*)[\"'].*)",
    // optional. js to execute after page shown
    "details_js": "aa=document.querySelectorAll('a.dl-stub');for(i=0;i<aa.length;i++){a=aa[i];h=a.getAttribute('href');a.setAttribute('href','#');id=h.split('t=')[1];function c(){location.href=h};a.onclick=c}",
    // next page url
    "next" :""
    // optional. torrent magnet url
    "magnet": "a[href^=magnet]:regex(.*href=[\"']([^'^\"]*).*)",
    // optional. torrent file url
    "torrent": "a[href^=torrent]:regex(.*href=[\"']([^'^\"]*).*)",
    // optional.  size to show
    "size": "a.dl-stub:regex(<a[^>]*>(.*)</a>)",
    // optional.  seed count
    "seed": "b.seedmed:regex(<b[^>]*>(.*)</b>)",
    // optional.  leech count
    "leech": "td.leechmed b:regex(<b[^>]*>(.*)</b>)"
  }
}
```
