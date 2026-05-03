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
                sh 'mvn test -Dsurefire.suiteXmlFiles=testNgXmls/api.xml'
            }
        }
        stage('Test') {
            steps {
                echo 'Starting Grid + Healenium + Running tests...'
                sh 'docker-compose -f $COMPOSE_FILE up --build --abort-on-container-exit postgres-db healenium selector-imitator selenium-hub chrome firefox test-runner'
                sh 'docker ps -a | grep test-runner'
                sh 'ls -la ./target/allure-results/'
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