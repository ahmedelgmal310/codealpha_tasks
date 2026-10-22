// Job DSL seed script, run by JCasC when the controller starts.
// The pipeline definitions live in ../pipelines/*.Jenkinsfile and are copied into the image.

def pipelinesDir = '/opt/jenkins/pipelines'

def demoPipeline = { String name, String text ->
    pipelineJob(name) {
        description(text)
        logRotator {
            numToKeep(20)
        }
        definition {
            cps {
                script(new File("${pipelinesDir}/${name}.Jenkinsfile").text)
                sandbox(true)
            }
        }
    }
}

demoPipeline('multi-arch-build',
        'Builds and runs the same Java program on the amd64 and the arm64 agent in parallel.')

demoPipeline('distributed-load',
        'Splits work into 6 shards that run in parallel on whichever linux executor is free.')

demoPipeline('node-isolation-check',
        'Verifies node isolation: no builds on the controller, non-root agents, separate agent networks.')

// Builds the Task 3 Gradle project on a remote agent, using the Jenkinsfile stored in the repository
def repoUrl = System.getenv('PIPELINES_REPO_URL') ?: 'https://github.com/ahmedelgmal310/codealpha_tasks.git'

pipelineJob('task3-gradle-build') {
    description("Checks out ${repoUrl} and runs CodeAlpha_Java_Gradle_App/Jenkinsfile on the amd64 agent.")
    logRotator {
        numToKeep(10)
    }
    definition {
        cpsScm {
            scm {
                git {
                    remote {
                        url(repoUrl)
                    }
                    branch('*/main')
                }
            }
            scriptPath('CodeAlpha_Java_Gradle_App/Jenkinsfile')
            lightweight(true)
        }
    }
}
