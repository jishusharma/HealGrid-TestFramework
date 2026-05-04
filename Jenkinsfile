pipeline {
    agent any
    environment {
        COMPOSE_FILE = 'docker-compose.yml'
    }
    triggers {
        githubPush()
    }
    stages {
        stage('Checkout') {
            steps {
                echo 'Checking out code from GitHub...'
                checkout scm
            }
        }
        stage('Build') {
            steps {
                echo 'Compiling project...'
                sh 'mvn clean compile -q'
            }
        }
        stage('API Tests') {
            steps {
                echo 'Running REST Assured API tests...'
                withCredentials([string(credentialsId: 'REQRES_API_KEY', variable: 'REQRES_API_KEY')]) {
                    sh 'mvn test -Dsurefire.suiteXmlFiles=testNgXmls/api.xml'
                }
                sh 'cp target/surefire-reports/TEST-TestSuite.xml target/surefire-reports/TEST-API-TestSuite.xml'
            }
        }
        stage('Test') {
            steps {
                echo 'Starting Grid + Healenium + Running tests...'
                sh 'mkdir -p target/surefire-reports/junitreports'
                sh 'docker-compose -f $COMPOSE_FILE up --build --abort-on-container-exit postgres-db healenium selector-imitator selenium-hub chrome firefox test-runner'
                sh 'docker-compose logs test-runner | tail -50'
                sh 'docker ps -a | grep test-runner'
                sh 'mkdir -p target/surefire-reports'
                sh 'docker cp $(docker-compose -f $COMPOSE_FILE ps -q --all test-runner):/app/target/surefire-reports/. target/surefire-reports/'
                sh 'ls -la target/surefire-reports/'
            }
        }
        stage('AI Failure Analysis') {
            steps {
                echo 'Analysing failures with Claude AI...'
                withCredentials([string(credentialsId: 'GROQ_API_KEY', variable: 'CLAUDE_API_KEY')]) {
                    sh 'ls -la target/surefire-reports/TEST-*.xml || echo "No TEST- xml files found"'
                    sh 'cat target/surefire-reports/TEST-TestSuite.xml || echo "File not found"'
                    sh 'find target/surefire-reports/ -name "TEST-*.xml" | xargs grep -l "failures=" || echo "No TEST xml files found"'
                    sh 'find target/surefire-reports/ -name "*.xml" | head -20'
                    sh 'mvn exec:java -Dexec.mainClass=ai.AiFailureAnalyzer -Dexec.classpathScope=runtime -Dfork=true'
                }
                script {
                    if (fileExists('target/ai-failure-report.json')) {
                        archiveArtifacts artifacts: 'target/ai-failure-report.json', allowEmptyArchive: true
                        echo 'AI failure report archived.'
                    } else {
                        echo 'No failures detected — report not generated.'
                    }
                }
            }
        }
        stage('Rerun RERUN-tagged Tests') {
            steps {
                script {
                    if (fileExists('target/ai-failure-report.json')) {
                        def report = readFile('target/ai-failure-report.json')
                        def json = new groovy.json.JsonSlurperClassic().parseText(report)
                        def rerunTests = json.clusters
                            .findAll { it.decision == 'RERUN' }
                            .collectMany { it.tests }
                            .unique()
                            .join(',')

                        if (rerunTests) {
                            echo "Rerunning RERUN-tagged tests: ${rerunTests}"
                            sh "mvn test -Dtest=${rerunTests} -DfailIfNoTests=false"
                        } else {
                            echo 'No tests marked RERUN. Skipping.'
                        }
                    } else {
                        echo 'No AI report found. Skipping rerun.'
                    }
                }
            }
        }
        stage('Report') {
            steps {
                echo 'Publishing Allure report...'
                sh 'allure generate target/allure-results --clean -o target/allure-report'
                publishHTML([
                    allowMissing: false,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'target/allure-report',
                    reportFiles: 'index.html',
                    reportName: 'Allure Report'
                ])
            }
        }
    }
    post {
        always {
            echo 'Cleaning up containers...'
            sh 'docker-compose stop postgres-db healenium selector-imitator selenium-hub chrome firefox test-runner'
            sh 'docker-compose rm -f postgres-db healenium selector-imitator selenium-hub chrome firefox test-runner'
        }
        success {
            echo 'Pipeline passed!'
        }
        failure {
            echo 'Pipeline failed! Check logs above.'
        }
    }
}