package com.partyoke.tv.net

/**
 * The single-page web app served to phones. Pure HTML/CSS/JS with no build step
 * and no external CDN, so it works fully offline on the local Wi-Fi.
 *
 * Flow:
 *   1. Guest scans QR -> opens "/" on their phone browser.
 *   2. Enters a name -> POST /join -> gets a playerId stored in localStorage.
 *   3. Opens a WebSocket to /ws for live party state (queue, now playing).
 *   4. Can tap "Skip my song" which POSTs /skip.
 */
object JoinPage {
    val HTML: String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
<title>PartyOke</title>
<style>
  :root { --pink:#ff2e92; --purple:#7c3aed; --bg:#140a24; }
  * { box-sizing:border-box; -webkit-tap-highlight-color:transparent; }
  body { margin:0; font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;
         background:var(--bg); color:#fff; min-height:100vh; }
  .wrap { max-width:460px; margin:0 auto; padding:24px; }
  h1 { font-size:34px; margin:12px 0 2px; background:linear-gradient(90deg,var(--pink),var(--purple));
       -webkit-background-clip:text; background-clip:text; color:transparent; }
  .sub { color:#b9a9d6; margin:0 0 24px; }
  input { width:100%; padding:16px; font-size:18px; border-radius:14px; border:none;
          background:#241539; color:#fff; margin-bottom:14px; }
  button { width:100%; padding:16px; font-size:18px; font-weight:700; border:none; border-radius:14px;
           background:linear-gradient(90deg,var(--pink),var(--purple)); color:#fff; cursor:pointer; }
  button.secondary { background:#241539; margin-top:10px; }
  .card { background:#1d1033; border-radius:18px; padding:20px; margin-top:18px; }
  .label { color:#b9a9d6; font-size:13px; text-transform:uppercase; letter-spacing:1px; }
  .song { font-size:26px; font-weight:800; margin:6px 0 2px; }
  .hidden { display:none; }
  .pill { display:inline-block; background:#2c1a47; border-radius:999px; padding:6px 12px; margin:4px 4px 0 0; }
  .dot { color:var(--pink); }
</style>
</head>
<body>
<div class="wrap">
  <h1>PartyOke</h1>
  <p class="sub">Scan. Sing. Party.</p>

  <div id="joinView">
    <input id="name" placeholder="Your name" autocomplete="off" maxlength="24" />
    <button id="joinBtn">Join the Party</button>
  </div>

  <div id="playerView" class="hidden">
    <div class="card">
      <div class="label">You're in, <span id="me"></span> <span class="dot">●</span></div>
      <div class="label" style="margin-top:14px;">Your next song</div>
      <div class="song" id="mySong">—</div>
      <button class="secondary" id="skipBtn">🔀 Shuffle / Skip my song</button>
    </div>
    <div class="card">
      <div class="label">Now singing</div>
      <div class="song" id="nowPlaying">—</div>
    </div>
    <div class="card">
      <div class="label">Up next</div>
      <div id="queue"></div>
    </div>
  </div>
</div>

<script>
  var playerId = localStorage.getItem('partyoke_pid');
  var playerName = localStorage.getItem('partyoke_name');

  function show(view) {
    document.getElementById('joinView').classList.toggle('hidden', view !== 'join');
    document.getElementById('playerView').classList.toggle('hidden', view !== 'player');
  }

  document.getElementById('joinBtn').onclick = function () {
    var name = document.getElementById('name').value.trim();
    if (!name) { return; }
    fetch('/join', {
      method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({ name: name })
    }).then(function(r){ return r.json(); }).then(function(d){
      playerId = d.id; playerName = d.name;
      localStorage.setItem('partyoke_pid', playerId);
      localStorage.setItem('partyoke_name', playerName);
      document.getElementById('me').textContent = playerName;
      show('player');
    });
  };

  document.getElementById('skipBtn').onclick = function () {
    fetch('/skip', {
      method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({ playerId: playerId })
    });
  };

  var sessionChecked = false;

  function render(state) {
    if (playerId && !sessionChecked) {
      var found = (state.players || []).some(function(p){ return p.id === playerId; });
      if (!found && (state.players || []).length === 0) {
        localStorage.removeItem('partyoke_pid');
        localStorage.removeItem('partyoke_name');
        playerId = null;
        playerName = null;
        show('join');
        sessionChecked = true;
        return;
      }
      sessionChecked = true;
    }

    var mine = (state.queue || []).filter(function(t){ return t.playerId === playerId; });
    var np = state.nowPlaying;
    if (np && np.playerId === playerId) {
      document.getElementById('mySong').textContent = np.songTitle + ' (singing now!)';
    } else if (mine.length) {
      document.getElementById('mySong').textContent = mine[0].songTitle;
    } else {
      document.getElementById('mySong').textContent = 'Waiting…';
    }
    document.getElementById('nowPlaying').textContent =
      np ? (np.playerName + ' — ' + np.songTitle) : 'Nobody yet';
    var q = document.getElementById('queue');
    q.innerHTML = '';
    (state.queue || []).slice(0, 8).forEach(function(t){
      var el = document.createElement('span');
      el.className = 'pill';
      el.textContent = t.playerName;
      q.appendChild(el);
    });
  }

  function connect() {
    var proto = location.protocol === 'https:' ? 'wss' : 'ws';
    var ws = new WebSocket(proto + '://' + location.host + '/ws');
    ws.onmessage = function (ev) {
      try { render(JSON.parse(ev.data)); } catch (e) {}
    };
    ws.onclose = function () { setTimeout(connect, 1500); };
  }

  if (playerId) {
    document.getElementById('me').textContent = playerName || '';
    show('player');
  } else {
    show('join');
  }
  connect();
</script>
</body>
</html>
""".trimIndent()
}
