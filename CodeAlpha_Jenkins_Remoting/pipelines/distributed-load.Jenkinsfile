// Splits a job into shards and lets Jenkins schedule each shard on any free "linux" executor.
// With 2 agents x 2 executors, 4 shards run at once and the rest queue until an executor frees up.
pipeline {
    agent none

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    parameters {
        string(name: 'SHARDS', defaultValue: '6', description: 'Number of parallel shards')
    }

    stages {
        stage('Run shards') {
            steps {
                script {
                    def count = Integer.parseInt(params.SHARDS)
                    def branches = [:]

                    for (int i = 1; i <= count; i++) {
                        def shard = i
                        branches["shard-${shard}"] = {
                            node('linux') {
                                sh """
                                    echo "shard ${shard}/${count} on \$NODE_NAME (\$(uname -m)), executor \$EXECUTOR_NUMBER"
                                    # simulate some CPU work
                                    seq 1 200000 | sha256sum > /dev/null
                                    sleep 5
                                    echo "${shard},\$NODE_NAME,\$(uname -m)" > shard-${shard}.csv
                                """
                                stash name: "shard-${shard}", includes: "shard-${shard}.csv"
                            }
                        }
                    }

                    parallel branches
                }
            }
        }

        stage('Report') {
            agent { label 'linux' }
            steps {
                script {
                    def count = Integer.parseInt(params.SHARDS)
                    for (int i = 1; i <= count; i++) {
                        unstash "shard-${i}"
                    }
                }
                sh '''
                    echo "shard,node,arch" > distribution.csv
                    cat shard-*.csv | sort -n >> distribution.csv
                    echo "Shards per node:"
                    tail -n +2 distribution.csv | cut -d, -f2 | sort | uniq -c
                '''
                archiveArtifacts artifacts: 'distribution.csv'
            }
        }
    }
}
