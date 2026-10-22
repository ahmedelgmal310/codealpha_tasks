// Checks the isolation rules of this setup and fails if any of them is broken.
pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 15, unit: 'MINUTES')
    }

    stages {
        stage('Controller runs no builds') {
            steps {
                script {
                    // The built-in node has 0 executors, so this request should wait in the queue until the timeout
                    def ranOnController = false
                    try {
                        timeout(time: 20, unit: 'SECONDS') {
                            node('built-in') {
                                ranOnController = true
                            }
                        }
                    } catch (err) {
                        echo "OK: no executor available on the controller (${err})"
                    }
                    if (ranOnController) {
                        error 'A build was able to run on the controller'
                    }
                }
            }
        }

        stage('Agent checks') {
            matrix {
                axes {
                    axis {
                        name 'ARCH'
                        values 'amd64', 'arm64'
                    }
                }

                agent { label "linux && ${ARCH}" }

                stages {
                    stage('Runs as non-root') {
                        steps {
                            sh '''
                                uid=$(id -u)
                                if [ "$uid" -eq 0 ]; then
                                    echo "FAIL: agent runs as root"
                                    exit 1
                                fi
                                echo "OK: running as $(id -un) (uid $uid)"
                            '''
                        }
                    }

                    stage('No extra privileges') {
                        steps {
                            sh '''
                                grep -E '^(CapEff|NoNewPrivs)' /proc/self/status
                                grep -qE '^CapEff:\\s+0+$' /proc/self/status || { echo "FAIL: process has Linux capabilities"; exit 1; }
                                grep -qE '^NoNewPrivs:\\s+1$' /proc/self/status || { echo "FAIL: no-new-privileges is not set"; exit 1; }
                                echo "OK: no capabilities, privilege escalation blocked"
                            '''
                        }
                    }

                    stage('Network isolation') {
                        steps {
                            sh '''
                                if [ "$ARCH" = "amd64" ]; then other=agent-arm64; else other=agent-amd64; fi

                                if getent hosts "$other" > /dev/null; then
                                    echo "FAIL: $NODE_NAME can reach $other"
                                    exit 1
                                fi
                                echo "OK: $other is not reachable from $NODE_NAME"

                                getent hosts jenkins > /dev/null && echo "OK: controller is reachable"
                            '''
                        }
                    }
                }
            }
        }
    }
}
