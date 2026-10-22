# Task 1 - CI/CD Pipeline using Azure

An automated CI/CD pipeline in **Azure Pipelines** that tests a Node.js web app, builds it into a Docker image, stores the image in **Azure Container Registry (ACR)** and deploys it to **Azure App Service**, then verifies that the new build is live.

The app (*Release Board*) is intentionally small: it shows which build, commit and environment is currently deployed, so you can see every release land.

## Pipeline overview

```
push to main (GitHub)
      │
      ▼
Azure Pipelines
  ├── Build    npm ci → node --test → publish JUnit results
  ├── Package  docker build (BUILD_ID, GIT_COMMIT) → docker push → Azure Container Registry
  ├── Deploy   AzureWebAppContainer@1 → App Service pulls release-board:<BuildId>
  └── Verify   poll /api/info until the new build is live → check /health
```

| Stage | Runs on | What it does |
|---|---|---|
| **Build** | every push and PR | Installs dependencies (cached), runs the tests, publishes JUnit results to the **Tests** tab |
| **Package** | `main` only | Builds the image with build number and commit baked in, pushes `:<BuildId>` and `:latest` to ACR |
| **Deploy** | `main` only | Deployment job in the `production` environment: App Service switches to the new tag |
| **Verify** | `main` only | Waits until `/api/info` reports the new build number, then checks `/health` |

Each stage runs only if the previous one succeeded, so a failing test never reaches production.

## What's in this folder

```
CodeAlpha_Azure_CICD_Pipeline/
├── azure-pipelines.yml       # the CI/CD pipeline
├── Dockerfile                # multi-stage image, non-root, health check, build metadata
├── src/                      # Express app (app.js) and server with graceful shutdown (server.js)
├── public/                   # Release Board page
├── test/                     # node:test unit/integration tests
├── infra/
│   ├── main.bicep            # ACR, App Service plan, Web App, AcrPull role, Log Analytics
│   └── main.parameters.json
├── scripts/provision-azure.sh
└── docs/
    ├── SETUP.md              # step-by-step Azure + Azure DevOps setup
    └── MONITORING.md         # watching the pipeline and the running app
```

## Run the app locally

```bash
cd CodeAlpha_Azure_CICD_Pipeline
npm ci
npm test          # 6 tests
npm start         # http://localhost:3000
```

Or the same way the pipeline builds it:

```bash
docker build --build-arg BUILD_ID=local-1 --build-arg GIT_COMMIT=$(git rev-parse HEAD) -t release-board .
docker run -d -p 3000:3000 --name release-board release-board
curl localhost:3000/api/info
```

## Deploy it to Azure

Full walkthrough in **[docs/SETUP.md](docs/SETUP.md)**. In short:

1. `az login` and `./scripts/provision-azure.sh` to create ACR, App Service and Log Analytics from Bicep.
2. In Azure DevOps create two service connections: `acr-connection` (Docker Registry → ACR) and `azure-rm-connection` (Azure Resource Manager), both with workload identity federation.
3. New pipeline → GitHub → this repo → existing YAML `/CodeAlpha_Azure_CICD_Pipeline/azure-pipelines.yml`.
4. Push to `main` and watch the four stages run.

## Key DevOps ideas in this task

- **Continuous integration**: every push and pull request is built and tested automatically; test results are published, not just printed.
- **Continuous deployment**: a green build on `main` goes to production with no manual steps (an approval gate can be added on the `production` environment).
- **Immutable, traceable artifacts**: each image is tagged with the build ID and carries the commit SHA, so you always know what's running and can roll back by redeploying an older tag.
- **Infrastructure as code**: all Azure resources are in `infra/main.bicep`, reviewed and versioned like the app.
- **No stored secrets**: the pipeline authenticates with workload identity federation, and App Service pulls from ACR with a managed identity (`AcrPull` role) instead of registry passwords.
- **Health checks and verification**: the container, App Service and the pipeline itself all check `/health`; the Verify stage proves the new version actually went live.
- **Monitoring**: test analytics and deployment history in Azure DevOps, container/HTTP logs in Log Analytics. See [docs/MONITORING.md](docs/MONITORING.md).

A GitHub Actions workflow ([task1-azure-app-checks.yml](../.github/workflows/task1-azure-app-checks.yml)) also runs the tests, builds the image and compiles the Bicep template on every push, as a quick check before the Azure pipeline.
