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
                sh 'docker-compose -f $COMPOSE_FILE up --build -V --abort-on-container-exit postgres-db healenium selector-imitator selenium-hub chrome firefox test-runner'
                sh 'docker ps -a | grep test-runner'
                sh '''
                    CONTAINER_ID=$(docker-compose ps -a -q test-runner)
                    if [ -z "$CONTAINER_ID" ]; then
                        echo "ERROR: No container found for service test-runner"
                        exit 1
                    fi
                    echo "Container ID: $CONTAINER_ID"
                    # Try to find allure-results directory
                    if docker cp $CONTAINER_ID:/app/target/allure-results/. ./target/allure-results/ 2>/dev/null; then
                        echo "Copied from /app/target/allure-results"
                    elif docker cp $CONTAINER_ID:/app/allure-results/. ./target/allure-results/ 2>/dev/null; then
                        echo "Copied from /app/allure-results (fallback)"
                    else
                        echo "ERROR: allure-results not found in container"
                        exit 1
                    fi
                    ls -la ./target/allure-results/
                '''
            }
        }

        stage('Report') {
            steps {
                echo 'Publishing Allure report...'
                allure tool: 'Allure', results: [[path: 'target/allure-results']]
            }
        }

    }

    post {
        always {
            echo 'Cleaning up containers...'
            sh 'docker-compose down -v'
        }
        success {
            echo 'Pipeline passed!'
        }
        failure {
            echo 'Pipeline failed! Check logs above.'
        }
    }

}