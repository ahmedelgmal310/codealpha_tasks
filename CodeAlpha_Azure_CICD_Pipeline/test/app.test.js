const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');

const { createApp, buildInfo } = require('../src/app');

let server;
let baseUrl;

before(async () => {
  const app = createApp({ BUILD_ID: '42', GIT_COMMIT: 'abc1234def5678', APP_ENV: 'test' });
  await new Promise((resolve) => {
    server = app.listen(0, resolve);
  });
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

after(() => {
  server.close();
});

test('GET /health returns ok', async () => {
  const res = await fetch(`${baseUrl}/health`);
  assert.equal(res.status, 200);

  const body = await res.json();
  assert.equal(body.status, 'ok');
  assert.equal(typeof body.uptimeSeconds, 'number');
});

test('GET /api/info returns the build metadata', async () => {
  const res = await fetch(`${baseUrl}/api/info`);
  assert.equal(res.status, 200);

  const body = await res.json();
  assert.equal(body.build, '42');
  assert.equal(body.commit, 'abc1234');
  assert.equal(body.environment, 'test');
  assert.ok(body.hostname);
});

test('GET / serves the dashboard page', async () => {
  const res = await fetch(`${baseUrl}/`);
  assert.equal(res.status, 200);
  assert.match(res.headers.get('content-type'), /text\/html/);
  assert.match(await res.text(), /Release Board/);
});

test('unknown routes return a JSON 404', async () => {
  const res = await fetch(`${baseUrl}/nope`);
  assert.equal(res.status, 404);
  assert.deepEqual(await res.json(), { error: 'Not found', path: '/nope' });
});

test('X-Powered-By header is not sent', async () => {
  const res = await fetch(`${baseUrl}/health`);
  assert.equal(res.headers.get('x-powered-by'), null);
});

test('buildInfo falls back to local defaults', () => {
  const info = buildInfo({});
  assert.equal(info.build, 'local');
  assert.equal(info.commit, 'unknown');
  assert.equal(info.environment, 'development');
});
