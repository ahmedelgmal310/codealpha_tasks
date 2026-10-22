#!/usr/bin/env bash
# Connects a real remote machine (another PC, a VM, a Raspberry Pi...) to the controller
# as an inbound agent. The machine only needs Java 17+ and network access to the
# controller's ports 8080 (HTTP) and 50000 (agent TCP port).
#
# Before running it:
#   1. In Jenkins: Manage Jenkins -> Nodes -> New Node, type "Permanent Agent".
#      Remote root directory: e.g. /home/<user>/jenkins-agent
#      Labels: e.g. "linux arm64 raspberrypi"
#      Launch method: "Launch agent by connecting it to the controller"
#   2. Open the new node's page and copy its secret.
#
# Usage on the remote machine:
#   JENKINS_SECRET=<secret> ./connect-remote-agent.sh http://<controller-ip>:8080 <node-name> [work-dir]
set -euo pipefail

JENKINS_URL="${1:?usage: JENKINS_SECRET=... $0 <jenkins-url> <node-name> [work-dir]}"
NODE_NAME="${2:?node name is required}"
WORK_DIR="${3:-$HOME/jenkins-agent}"
: "${JENKINS_SECRET:?set JENKINS_SECRET to the secret shown on the node page}"

command -v java > /dev/null || { echo "Java 17+ is required on this machine" >&2; exit 1; }

mkdir -p "$WORK_DIR"
cd "$WORK_DIR"

# agent.jar always comes from the controller, so its version matches the controller's remoting version
curl -fsSLo agent.jar "${JENKINS_URL%/}/jnlpJars/agent.jar"

# Keep the secret in a private file instead of the command line, where `ps` would show it
umask 077
printf '%s' "$JENKINS_SECRET" > secret
unset JENKINS_SECRET

echo "Connecting $(hostname) ($(uname -m)) to $JENKINS_URL as '$NODE_NAME'"
exec java -jar agent.jar \
  -url "$JENKINS_URL" \
  -name "$NODE_NAME" \
  -secret @secret \
  -workDir "$WORK_DIR"
