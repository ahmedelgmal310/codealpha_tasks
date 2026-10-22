// Azure resources for the Release Board app:
//   - Azure Container Registry (stores the images built by the pipeline)
//   - Linux App Service plan + Web App for Containers (runs the image)
//   - Log Analytics workspace + diagnostic settings (app and HTTP logs for monitoring)
//   - AcrPull role so the Web App pulls images with its managed identity (no registry passwords)
//
// Deploy:
//   az group create -n rg-codealpha-cicd -l westeurope
//   az deployment group create -g rg-codealpha-cicd -f infra/main.bicep -p infra/main.parameters.json

@description('Location for all resources')
param location string = resourceGroup().location

@description('Web App name, becomes <name>.azurewebsites.net, must be globally unique')
param webAppName string = 'codealpha-release-board-${uniqueString(resourceGroup().id)}'

@description('Container registry name: 5-50 letters and numbers, globally unique')
@minLength(5)
@maxLength(50)
param acrName string = 'codealphaacr${uniqueString(resourceGroup().id)}'

@description('Repository name of the image inside the registry')
param imageRepository string = 'release-board'

@description('Image tag used for the first deployment. The pipeline replaces it on every release.')
param imageTag string = 'latest'

@description('App Service plan SKU. B1 is the cheapest tier that supports Always On.')
@allowed([
  'B1'
  'B2'
  'S1'
  'P0v3'
])
param planSku string = 'B1'

var acrPullRoleId = '7f951dda-4ed3-4680-a7ca-43fe172d538d'

resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: acrName
  location: location
  sku: {
    name: 'Basic'
  }
  properties: {
    adminUserEnabled: false
  }
}

resource plan 'Microsoft.Web/serverfarms@2023-12-01' = {
  name: '${webAppName}-plan'
  location: location
  kind: 'linux'
  sku: {
    name: planSku
  }
  properties: {
    reserved: true // required for Linux plans
  }
}

resource webApp 'Microsoft.Web/sites@2023-12-01' = {
  name: webAppName
  location: location
  kind: 'app,linux,container'
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    serverFarmId: plan.id
    httpsOnly: true
    siteConfig: {
      linuxFxVersion: 'DOCKER|${acr.properties.loginServer}/${imageRepository}:${imageTag}'
      acrUseManagedIdentityCreds: true
      alwaysOn: true
      healthCheckPath: '/health'
      ftpsState: 'Disabled'
      minTlsVersion: '1.2'
      http20Enabled: true
      appSettings: [
        {
          name: 'WEBSITES_PORT'
          value: '3000'
        }
        {
          name: 'APP_ENV'
          value: 'production'
        }
        {
          name: 'WEBSITES_ENABLE_APP_SERVICE_STORAGE'
          value: 'false'
        }
      ]
    }
  }
}

resource acrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(acr.id, webApp.id, acrPullRoleId)
  scope: acr
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', acrPullRoleId)
    principalId: webApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: '${webAppName}-logs'
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: 30
  }
}

// Sends container stdout/stderr, HTTP access logs and platform events to Log Analytics
resource webAppDiagnostics 'Microsoft.Insights/diagnosticSettings@2021-05-01-preview' = {
  name: 'send-to-log-analytics'
  scope: webApp
  properties: {
    workspaceId: logs.id
    logs: [
      {
        category: 'AppServiceConsoleLogs'
        enabled: true
      }
      {
        category: 'AppServiceHTTPLogs'
        enabled: true
      }
      {
        category: 'AppServicePlatformLogs'
        enabled: true
      }
    ]
    metrics: [
      {
        category: 'AllMetrics'
        enabled: true
      }
    ]
  }
}

output acrLoginServer string = acr.properties.loginServer
output acrName string = acr.name
output webAppName string = webApp.name
output webAppUrl string = 'https://${webApp.properties.defaultHostName}'
output logAnalyticsWorkspace string = logs.name
