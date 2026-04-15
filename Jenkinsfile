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

        stage('Test') {
            steps {
                echo 'Starting Grid + Healenium + Running tests...'
                sh 'docker-compose -f $COMPOSE_FILE up --build --abort-on-container-exit postgres-db healenium selector-imitator selenium-hub chrome firefox test-runner'
            }
        }

        stage('Report') {
            steps {
                echo 'Publishing Allure report...'
                allure([
                    includeProperties: false,
                    jdk: '',
                    reportBuildPolicy: 'ALWAYS',
                    results: [[path: 'target/allure-results']]
                ])
            }
        }

    }

    post {
        always {
            echo 'Cleaning up containers...'
            sh 'docker-compose down'
        }
        success {
            echo 'Pipeline passed!'
        }
        failure {
            echo 'Pipeline failed! Check logs above.'
        }
    }

}

