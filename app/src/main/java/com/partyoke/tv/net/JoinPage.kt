package com.partyoke.tv.net

/**
 * The single-page web app served to phones. Pure HTML/CSS/JS with no build step
 * and no external CDN, so it works fully offline on the local Wi-Fi.
 *
 * Flow:
 *   1. Guest scans QR -> opens "/" on their phone browser.
 *   2. Enters a name -> POST /join -> gets a playerId stored in localStorage.
 *   3. Opens a WebSocket to /ws for live party state (queue, now playing).
 *   4. Can tap "Shuffle/Skip my song" (/skip) and "Applause" (/applaud).
 *   5. If this phone is the admin (first player == state.adminPlayerId), it
 *      additionally sees an Admin panel: rename party, pick accent color, and
 *      manage the queue (bump / remove).
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
  :root { --accent:#ff2e92; --purple:#7c3aed; --bg:#140a24; }
  * { box-sizing:border-box; -webkit-tap-highlight-color:transparent; }
  body { margin:0; font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;
         background:var(--bg); color:#fff; min-height:100vh; }
  .wrap { max-width:460px; margin:0 auto; padding:24px; }
  h1 { font-size:34px; margin:12px 0 2px; background:linear-gradient(90deg,var(--accent),var(--purple));
       -webkit-background-clip:text; background-clip:text; color:transparent; }
  .sub { color:#b9a9d6; margin:0 0 24px; }
  input { width:100%; padding:16px; font-size:18px; border-radius:14px; border:none;
          background:#241539; color:#fff; margin-bottom:14px; }
  button { width:100%; padding:16px; font-size:18px; font-weight:700; border:none; border-radius:14px;
           background:linear-gradient(90deg,var(--accent),var(--purple)); color:#fff; cursor:pointer; }
  button.secondary { background:#241539; margin-top:10px; }
  button.small { width:auto; padding:8px 12px; font-size:14px; margin:0 0 0 6px; }
  .card { background:#1d1033; border-radius:18px; padding:20px; margin-top:18px; }
  .card.admin { border:1px solid var(--accent); }
  .label { color:#b9a9d6; font-size:13px; text-transform:uppercase; letter-spacing:1px; }
  .badge { display:inline-block; background:var(--accent); color:#140a24; font-weight:800;
           border-radius:999px; padding:2px 10px; font-size:12px; margin-left:8px; }
  .song { font-size:26px; font-weight:800; margin:6px 0 2px; }
  .hidden { display:none; }
  .pill { display:inline-block; background:#2c1a47; border-radius:999px; padding:6px 12px; margin:4px 4px 0 0; }
  .dot { color:var(--accent); }
  .applause { font-size:40px; padding:18px; }
  .row { display:flex; align-items:center; justify-content:space-between; padding:10px 0;
         border-bottom:1px solid #2c1a47; }
  .row:last-child { border-bottom:none; }
  .row .name { font-weight:600; }
  .row .meta { color:#b9a9d6; font-size:13px; }
  .lb-rank { color:var(--accent); font-weight:800; margin-right:10px; }
  .swatches { display:flex; gap:10px; margin-top:10px; flex-wrap:wrap; }
  .swatch { width:40px; height:40px; border-radius:10px; border:2px solid transparent; cursor:pointer; }
  .swatch.sel { border-color:#fff; }
  .mini { display:flex; align-items:center; }
</style>
</head>
<body>
<div class="wrap">
  <h1 id="title">PartyOke</h1>
  <p class="sub">Scan. Sing. Party.</p>

  <div id="joinView">
    <input id="name" placeholder="Your name" autocomplete="off" maxlength="24" />
    <button id="joinBtn">Join the Party</button>
  </div>

  <div id="playerView" class="hidden">
    <div class="card">
      <div class="label">You're in, <span id="me"></span> <span class="dot">●</span>
        <span id="adminBadge" class="badge hidden">ADMIN</span>
      </div>
      <div class="label" style="margin-top:14px;">Your next song</div>
      <div class="song" id="mySong">—</div>
      <button class="secondary" id="skipBtn">🔀 Shuffle / Skip my song</button>
    </div>

    <div class="card">
      <div class="label">Now singing</div>
      <div class="song" id="nowPlaying">—</div>
      <button class="applause" id="applauseBtn">👏 Applause</button>
    </div>

    <div class="card">
      <div class="label">🏆 Leaderboard</div>
      <div id="leaderboard"></div>
    </div>

    <div class="card">
      <div class="label">Up next</div>
      <div id="queue"></div>
    </div>

    <!-- Admin-only panel -->
    <div id="adminPanel" class="card admin hidden">
      <div class="label">⚙️ Admin controls</div>

      <div class="label" style="margin-top:14px;">Party name</div>
      <input id="partyNameInput" maxlength="32" placeholder="Party name" />
      <button class="secondary" id="renameBtn">Rename party</button>

      <div class="label" style="margin-top:14px;">Accent color</div>
      <div class="swatches" id="swatches"></div>

      <div class="label" style="margin-top:18px;">Manage queue</div>
      <div id="adminQueue"></div>
    </div>
  </div>
</div>

<script>
  var playerId = localStorage.getItem('partyoke_pid');
  var playerName = localStorage.getItem('partyoke_name');
  var lastState = null;

  var ACCENTS = ['#ff2e92','#7c3aed','#00c2ff','#28d17c','#ff8a00','#ff4d4d'];

  function show(view) {
    document.getElementById('joinView').classList.toggle('hidden', view !== 'join');
    document.getElementById('playerView').classList.toggle('hidden', view !== 'player');
  }

  function post(path, body) {
    return fetch(path, {
      method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify(body || {})
    });
  }

  document.getElementById('joinBtn').onclick = function () {
    var name = document.getElementById('name').value.trim();
    if (!name) { return; }
    post('/join', { name: name }).then(function(r){ return r.json(); }).then(function(d){
      playerId = d.id; playerName = d.name;
      localStorage.setItem('partyoke_pid', playerId);
      localStorage.setItem('partyoke_name', playerName);
      document.getElementById('me').textContent = playerName;
      show('player');
    });
  };

  document.getElementById('skipBtn').onclick = function () {
    post('/skip', { playerId: playerId });
  };

  document.getElementById('applauseBtn').onclick = function () {
    post('/applaud', { playerId: playerId });
  };

  document.getElementById('renameBtn').onclick = function () {
    var n = document.getElementById('partyNameInput').value.trim();
    if (n) { post('/admin/rename', { playerId: playerId, name: n }); }
  };

  function isAdmin(state) { return state && state.adminPlayerId === playerId; }

  function applyAccent(hex) {
    document.documentElement.style.setProperty('--accent', hex || '#ff2e92');
  }

  function renderSwatches(selected) {
    var box = document.getElementById('swatches');
    box.innerHTML = '';
    ACCENTS.forEach(function(c){
      var s = document.createElement('div');
      s.className = 'swatch' + (c.toLowerCase() === (selected||'').toLowerCase() ? ' sel' : '');
      s.style.background = c;
      s.onclick = function(){ post('/admin/accent', { playerId: playerId, accent: c }); };
      box.appendChild(s);
    });
  }

  function renderLeaderboard(list) {
    var el = document.getElementById('leaderboard');
    el.innerHTML = '';
    if (!list || !list.length) { el.innerHTML = '<div class="meta">No applause yet</div>'; return; }
    list.slice(0, 8).forEach(function(e, i){
      var row = document.createElement('div');
      row.className = 'row';
      row.innerHTML = '<div class="mini"><span class="lb-rank">' + (i+1) +
        '</span><span class="name">' + escapeHtml(e.playerName) + '</span></div>' +
        '<div class="meta">👏 ' + e.applause + '</div>';
      el.appendChild(row);
    });
  }

  function renderAdminQueue(queue) {
    var el = document.getElementById('adminQueue');
    el.innerHTML = '';
    if (!queue || !queue.length) { el.innerHTML = '<div class="meta">Queue is empty</div>'; return; }
    queue.forEach(function(t){
      var row = document.createElement('div');
      row.className = 'row';
      var left = document.createElement('div');
      left.className = 'name';
      left.textContent = t.playerName + ' — ' + t.songTitle;
      var ctrls = document.createElement('div');
      ctrls.className = 'mini';
      var bump = document.createElement('button');
      bump.className = 'small'; bump.textContent = '⏭ Next';
      bump.onclick = function(){ post('/admin/queue/bump', { playerId: playerId, turnId: t.id }); };
      var rm = document.createElement('button');
      rm.className = 'small secondary'; rm.textContent = '✕';
      rm.onclick = function(){ post('/admin/queue/remove', { playerId: playerId, turnId: t.id }); };
      ctrls.appendChild(bump); ctrls.appendChild(rm);
      row.appendChild(left); row.appendChild(ctrls);
      el.appendChild(row);
    });
  }

  function escapeHtml(s) {
    return (s || '').replace(/[&<>"']/g, function(c){
      return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];
    });
  }

  var sessionChecked = false;

  function render(state) {
    lastState = state;

    // Party branding (name + accent) from TV state.
    applyAccent(state.accentColor);
    document.getElementById('title').textContent = state.partyName || 'PartyOke';
    document.title = state.partyName || 'PartyOke';

    if (playerId && !sessionChecked) {
      var found = (state.players || []).some(function(p){ return p.id === playerId; });
      if (!found && (state.players || []).length === 0) {
        localStorage.removeItem('partyoke_pid');
        localStorage.removeItem('partyoke_name');
        playerId = null; playerName = null;
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

    // Applause only enabled when someone else is singing.
    var canApplaud = !!np && np.playerId !== playerId;
    document.getElementById('applauseBtn').disabled = !canApplaud;
    document.getElementById('applauseBtn').style.opacity = canApplaud ? '1' : '0.4';

    renderLeaderboard(state.leaderboard);

    var q = document.getElementById('queue');
    q.innerHTML = '';
    (state.queue || []).slice(0, 8).forEach(function(t){
      var el = document.createElement('span');
      el.className = 'pill';
      el.textContent = t.playerName;
      q.appendChild(el);
    });

    // Admin panel toggle + contents.
    var admin = isAdmin(state);
    document.getElementById('adminBadge').classList.toggle('hidden', !admin);
    document.getElementById('adminPanel').classList.toggle('hidden', !admin);
    if (admin) {
      renderSwatches(state.accentColor);
      renderAdminQueue(state.queue);
      var nameInput = document.getElementById('partyNameInput');
      if (document.activeElement !== nameInput) { nameInput.value = state.partyName || ''; }
    }
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
