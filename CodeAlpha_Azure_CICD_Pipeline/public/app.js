(function () {
  var fields = ['version', 'build', 'commit', 'environment', 'hostname', 'startedAt', 'node'];
  var badge = document.getElementById('health');

  function setBadge(state, text) {
    badge.setAttribute('data-state', state);
    badge.textContent = text;
  }

  fetch('/api/info')
    .then(function (res) { return res.json(); })
    .then(function (info) {
      fields.forEach(function (key) {
        var value = info[key];
        if (key === 'startedAt' && value) {
          value = new Date(value).toLocaleString();
        }
        document.getElementById(key).textContent = value || '-';
      });
    })
    .catch(function () {
      document.getElementById('version').textContent = 'could not load /api/info';
    });

  fetch('/health')
    .then(function (res) { setBadge(res.ok ? 'ok' : 'down', res.ok ? 'healthy' : 'unhealthy'); })
    .catch(function () { setBadge('down', 'unreachable'); });
})();
