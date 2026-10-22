// Runs the same build on two CPU architectures at the same time.
// The matrix creates one branch per ARCH value, and each branch asks for an agent with that label.
pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {
        stage('Build on every architecture') {
            matrix {
                axes {
                    axis {
                        name 'ARCH'
                        values 'amd64', 'arm64'
                    }
                }

                agent { label "linux && ${ARCH}" }

                stages {
                    stage('Agent info') {
                        steps {
                            sh '''
                                echo "node:    $NODE_NAME"
                                echo "machine: $(uname -m)"
                                echo "kernel:  $(uname -sr)"
                                echo "user:    $(id -un)"
                                java -version 2>&1 | head -n 1
                            '''
                        }
                    }

                    stage('Compile and run') {
                        steps {
                            writeFile file: 'src/Hello.java', text: '''
public class Hello {
    public static void main(String[] args) {
        System.out.printf("Hello from %s, Java %s, %d CPUs%n",
                System.getProperty("os.arch"), Runtime.version(), Runtime.getRuntime().availableProcessors());
    }
}
'''
                            sh '''
                                rm -rf out && mkdir -p out
                                javac -d out src/Hello.java
                                java -cp out Hello | tee "build-info-$ARCH.txt"
                                echo "built on $NODE_NAME ($(uname -m)) at $(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "build-info-$ARCH.txt"
                            '''
                            archiveArtifacts artifacts: "build-info-${ARCH}.txt", fingerprint: true
                        }
                    }
                }
            }
        }
    }
}
