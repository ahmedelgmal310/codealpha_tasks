const express = require('express');
const os = require('os');
const path = require('path');

const pkg = require('../package.json');

// Build metadata is passed in by the pipeline as Docker build args (see Dockerfile),
// so the running app can tell you exactly which build is deployed.
function buildInfo(env = process.env) {
  return {
    app: pkg.name,
    version: pkg.version,
    build: env.BUILD_ID || 'local',
    commit: (env.GIT_COMMIT || 'unknown').slice(0, 7),
    environment: env.APP_ENV || 'development',
  };
}

function createApp(env = process.env) {
  const app = express();
  const startedAt = Date.now();

  app.disable('x-powered-by');
  app.use(express.static(path.join(__dirname, '..', 'public')));

  // Used by the Docker HEALTHCHECK, the App Service health check and the pipeline smoke test
  app.get('/health', (req, res) => {
    res.json({
      status: 'ok',
      uptimeSeconds: Math.round((Date.now() - startedAt) / 1000),
    });
  });

  app.get('/api/info', (req, res) => {
    res.json({
      ...buildInfo(env),
      hostname: os.hostname(),
      node: process.version,
      startedAt: new Date(startedAt).toISOString(),
    });
  });

  app.use((req, res) => {
    res.status(404).json({ error: 'Not found', path: req.path });
  });

  return app;
}

module.exports = { createApp, buildInfo };
