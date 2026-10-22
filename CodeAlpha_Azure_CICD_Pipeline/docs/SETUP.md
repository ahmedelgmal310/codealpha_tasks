# Setting up the pipeline in Azure DevOps

Everything the pipeline needs, in order. Budget: the App Service **B1** plan and ACR **Basic** together cost roughly 18 USD/month, and new Azure accounts get free credit that covers it. Delete the resource group when you're done.

## 1. Create the Azure resources

Install the [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli), then:

```bash
az login
./scripts/provision-azure.sh rg-codealpha-cicd westeurope
```

This deploys `infra/main.bicep`:

| Resource | Purpose |
|---|---|
| Container Registry (Basic) | Private registry for the images the pipeline builds |
| App Service plan (Linux, B1) | The VM that runs the container |
| Web App for Containers | Runs `release-board:<tag>` from the registry, health check on `/health` |
| Role assignment `AcrPull` | Lets the Web App pull images with its managed identity, no registry password stored anywhere |
| Log Analytics workspace | Collects container logs, HTTP logs and platform events |

Registry and web app names must be globally unique. If `codealphaahmedacr` or `codealpha-ahmed-release-board` is taken, change it in `infra/main.parameters.json` **and** in the `variables` block of `azure-pipelines.yml`.

The Web App can't start until the first image is pushed. That's expected: the first pipeline run fixes it.

## 2. Create an Azure DevOps project

1. Go to https://dev.azure.com and create an organization and a project.
2. New organizations need a **free parallel job grant** for Microsoft-hosted agents. Request it at https://aka.ms/azpipelines-parallelism-request (approval usually takes 1-3 business days). Until then you can register a [self-hosted agent](https://learn.microsoft.com/azure/devops/pipelines/agents/linux-agent) on your own machine.

## 3. Create the service connections

**Project settings → Service connections → New service connection**

| Type | Name (must match the pipeline) | Settings |
|---|---|---|
| Docker Registry → Azure Container Registry | `acr-connection` | Authentication: Workload Identity federation, pick your subscription and the registry |
| Azure Resource Manager | `azure-rm-connection` | Workload Identity federation (automatic), scope: resource group `rg-codealpha-cicd` |

Workload identity federation means no client secrets that expire or leak; Azure DevOps gets short-lived tokens instead.

## 4. Create the pipeline

1. **Pipelines → New pipeline → GitHub** and authorize the Azure Pipelines app.
2. Select the `codealpha_tasks` repository.
3. **Existing Azure Pipelines YAML file**, branch `main`, path `/CodeAlpha_Azure_CICD_Pipeline/azure-pipelines.yml`.
4. **Run**.

The first run asks you to permit the pipeline to use the service connections and the `production` environment. Click **Permit** on each.

## 5. (Optional) Add an approval before production

**Pipelines → Environments → production → Approvals and checks → Approvals**, add yourself. The Deploy stage will now wait for your approval on every release.

## What happens on every push to main

```
push to main
   │
   ▼
Build    npm ci ─▶ node --test ─▶ publish JUnit results (Tests tab)
   │
   ▼
Package  docker build (BUILD_ID, GIT_COMMIT baked in) ─▶ push :<BuildId> and :latest to ACR
   │
   ▼
Deploy   App Service switches to <acr>/release-board:<BuildId>  (recorded in Environments > production)
   │
   ▼
Verify   poll /api/info until it reports the new build number, then check /health
```

Pull requests to `main` only run the **Build** stage, so broken code is caught before it's merged.

## Rolling back

Every image is tagged with its build ID, so rolling back is redeploying an older tag:

```bash
az webapp config container set \
  --resource-group rg-codealpha-cicd \
  --name codealpha-ahmed-release-board \
  --container-image-name codealphaahmedacr.azurecr.io/release-board:<older-build-id>
```

Or re-run the Deploy stage of an older pipeline run from the Azure DevOps UI.
