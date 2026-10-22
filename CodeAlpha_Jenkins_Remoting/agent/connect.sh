#!/usr/bin/env bash
# Entry point for the agent containers.
# 1. Waits for the controller and downloads this node's connection secret, using an
#    account that only has the Agent/Connect permission.
# 2. Hands over to the standard jenkins-agent script, which starts remoting.jar
#    and connects to the controller on TCP port 50000.
set -euo pipefail

: "${JENKINS_URL:?JENKINS_URL is required}"
: "${JENKINS_AGENT_NAME:?JENKINS_AGENT_NAME is required}"
: "${AGENT_CONNECT_USER:?AGENT_CONNECT_USER is required}"
: "${AGENT_CONNECT_PASSWORD:?AGENT_CONNECT_PASSWORD is required}"

jnlp_url="${JENKINS_URL%/}/computer/${JENKINS_AGENT_NAME}/jenkins-agent.jnlp"
jnlp=""

echo "[$JENKINS_AGENT_NAME] $(uname -m), waiting for the controller at $JENKINS_URL"
for attempt in $(seq 1 60); do
  if jnlp=$(curl -fsS -u "${AGENT_CONNECT_USER}:${AGENT_CONNECT_PASSWORD}" "$jnlp_url" 2>/dev/null); then
    break
  fi
  sleep 5
done

# The secret is the 64-character hex string in the JNLP file
secret=$(printf '%s' "$jnlp" | grep -oE '[a-f0-9]{64}' | head -n 1 || true)
if [ -z "$secret" ]; then
  echo "[$JENKINS_AGENT_NAME] could not get the agent secret from $jnlp_url" >&2
  exit 1
fi

echo "[$JENKINS_AGENT_NAME] got the secret, connecting"
export JENKINS_SECRET="$secret"
unset AGENT_CONNECT_PASSWORD

exec /usr/local/bin/jenkins-agent "$@"
