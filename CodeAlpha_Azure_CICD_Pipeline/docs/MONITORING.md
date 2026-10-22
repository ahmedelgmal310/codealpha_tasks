# Monitoring the pipeline and the app

## The pipeline

| Where | What you see |
|---|---|
| **Pipelines → Runs** | Every run, its trigger (push / PR / manual), duration and the stage that failed |
| Run summary → **Tests** tab | Results from `reports/junit.xml`: pass rate, failed tests with their output, trend across runs |
| **Pipelines → Environments → production** | Deployment history: which run and commit is live, when it was deployed |
| **Pipelines → Analytics** | Pass rate, average duration and the flakiest tests over time |
| Project settings → **Notifications** | Email when a build fails or a deployment completes |

Things the pipeline checks on its own so a bad release doesn't slip through:

- Failed unit tests fail the Build stage (`failTaskOnFailedTests: true`), so nothing gets packaged.
- Package, Deploy and Verify only run if the previous stage succeeded.
- The Verify stage waits until `/api/info` reports the new build number. If App Service is still serving the old container after 5 minutes, the run fails with an error annotation.

Add a status badge to a README from **Pipelines → (pipeline) → ⋯ → Status badge**.

## The running app

**Health check.** App Service calls `/health` every minute (`healthCheckPath` in the Bicep template). Instances that keep failing are taken out of rotation and replaced. The result shows under **Web App → Monitoring → Health check**.

**Live logs** from the container:

```bash
az webapp log config --name codealpha-ahmed-release-board --resource-group rg-codealpha-cicd --docker-container-logging filesystem
az webapp log tail   --name codealpha-ahmed-release-board --resource-group rg-codealpha-cicd
```

**Log Analytics.** The Bicep template sends console, HTTP and platform logs to a Log Analytics workspace. In the portal open the workspace → **Logs** and try:

```kusto
// Startup messages and errors written by the app
AppServiceConsoleLogs
| where TimeGenerated > ago(1h)
| project TimeGenerated, ResultDescription
| order by TimeGenerated desc

// Requests per status code over the last day
AppServiceHTTPLogs
| where TimeGenerated > ago(1d)
| summarize requests = count() by ScStatus, bin(TimeGenerated, 1h)
| render timechart

// Slowest endpoints
AppServiceHTTPLogs
| summarize p95_ms = percentile(TimeTaken, 95), requests = count() by CsUriStem
| order by p95_ms desc

// Container pulls, restarts and crashes
AppServicePlatformLogs
| where TimeGenerated > ago(1d)
| project TimeGenerated, Level, Message
| order by TimeGenerated desc
```

**Metrics.** Web App → Monitoring → Metrics: `Requests`, `Http5xx`, `Response Time`, `CPU Time`, `Memory working set`. Create an alert rule (for example `Http5xx > 5 in 5 minutes`) to get an email when something breaks.

## Troubleshooting a failed deployment

| Symptom | Likely cause |
|---|---|
| Package stage: `unauthorized: authentication required` | The `acr-connection` service connection points to a different registry or lacks push rights |
| Site shows "Application Error" | Container failed to start. Check `az webapp log tail` and `AppServicePlatformLogs` |
| Platform log: `ImagePullFailure` / `unauthorized` | The `AcrPull` role assignment is missing or hasn't propagated yet (can take a few minutes after provisioning) |
| Platform log: `didn't respond to HTTP pings on port` | `WEBSITES_PORT` doesn't match the port the app listens on (3000) |
| Verify stage times out but the site works | The new container is still starting. Check that `webAppUrl` in the pipeline matches the URL from the Bicep output |
