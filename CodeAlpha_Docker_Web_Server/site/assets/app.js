// Polls the /health endpoint and shows the result on the page.
(function () {
  var statusEl = document.getElementById('status');
  var statusText = document.getElementById('status-text');
  var bodyEl = document.getElementById('health-body');
  var timeEl = document.getElementById('health-time');
  var checkedEl = document.getElementById('health-checked');

  function setStatus(state, text) {
    statusEl.setAttribute('data-state', state);
    statusText.textContent = text;
  }

  function check() {
    var started = performance.now();

    fetch('/health', { cache: 'no-store' })
      .then(function (res) {
        return res.text().then(function (body) {
          var elapsed = Math.round(performance.now() - started);
          bodyEl.textContent = res.status + ' ' + body;
          timeEl.textContent = elapsed + ' ms';
          checkedEl.textContent = new Date().toLocaleTimeString();
          setStatus(res.ok ? 'ok' : 'down', res.ok ? 'Server is healthy' : 'Health check failed');
        });
      })
      .catch(function () {
        checkedEl.textContent = new Date().toLocaleTimeString();
        setStatus('down', 'Server is not reachable');
      });
  }

  check();
  setInterval(check, 10000);
})();
