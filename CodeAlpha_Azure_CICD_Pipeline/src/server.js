const { createApp, buildInfo } = require('./app');

const port = Number(process.env.PORT) || 3000;
const info = buildInfo();

const server = createApp().listen(port, () => {
  console.log(`${info.app} v${info.version} (build ${info.build}, ${info.environment}) listening on port ${port}`);
});

// App Service and `docker stop` send SIGTERM before killing the container.
// Close the server so in-flight requests can finish.
function shutdown(signal) {
  console.log(`${signal} received, shutting down`);
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(1), 10000).unref();
}

process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
