#!/usr/bin/env bash
# Creates the Azure resources for the pipeline from infra/main.bicep.
# Needs the Azure CLI and an account logged in with `az login`.
#
# Usage: ./scripts/provision-azure.sh [resource-group] [location]
set -euo pipefail

RESOURCE_GROUP="${1:-rg-codealpha-cicd}"
LOCATION="${2:-westeurope}"

cd "$(dirname "$0")/.."

echo "Subscription: $(az account show --query name -o tsv)"

echo "Registering resource providers (only needed once per subscription)..."
for ns in Microsoft.ContainerRegistry Microsoft.Web Microsoft.OperationalInsights Microsoft.Insights; do
  az provider register --namespace "$ns" --wait
done

echo "Creating resource group $RESOURCE_GROUP in $LOCATION..."
az group create --name "$RESOURCE_GROUP" --location "$LOCATION" --output none

echo "Deploying infra/main.bicep..."
az deployment group create \
  --resource-group "$RESOURCE_GROUP" \
  --name release-board-infra \
  --template-file infra/main.bicep \
  --parameters infra/main.parameters.json \
  --output none

az deployment group show \
  --resource-group "$RESOURCE_GROUP" \
  --name release-board-infra \
  --query properties.outputs \
  --output table

cat <<EOF

Done. Copy these values into the variables block of azure-pipelines.yml:
  acrLoginServer, webAppName, webAppUrl

Next: create the two service connections described in docs/SETUP.md.
To delete everything later:  az group delete --name $RESOURCE_GROUP --yes
EOF
