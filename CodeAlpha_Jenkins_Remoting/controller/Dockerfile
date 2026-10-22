# Jenkins controller, fully configured at startup by Configuration as Code (JCasC).
# Build context is the task folder: docker compose builds it with context "." and this Dockerfile
FROM jenkins/jenkins:lts-jdk21

# Skip the setup wizard: users, security, agents and jobs all come from casc/
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false" \
    CASC_JENKINS_CONFIG=/opt/jenkins/casc/jenkins.yaml

COPY controller/plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

COPY controller/casc/ /opt/jenkins/casc/
COPY pipelines/ /opt/jenkins/pipelines/

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD ["curl", "-fsS", "-o", "/dev/null", "http://localhost:8080/login"]
