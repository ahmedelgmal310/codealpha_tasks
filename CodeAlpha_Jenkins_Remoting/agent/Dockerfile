# Inbound agent image. jenkins/inbound-agent is multi-arch (amd64, arm64, ...), so the same
# Dockerfile builds the x86_64 and the aarch64 agent; docker compose picks the platform.
FROM jenkins/inbound-agent:latest-jdk21

COPY --chmod=755 agent/connect.sh /usr/local/bin/connect.sh

# Already the default in the base image, repeated so it's explicit: the agent never runs as root
USER jenkins

ENTRYPOINT ["/usr/local/bin/connect.sh"]
