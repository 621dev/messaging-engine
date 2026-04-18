pipeline {
    agent any

    tools {
        jdk 'JDK21'
        maven 'Maven3'
    }

    environment {
        JAR_NAME = 'messaging-engine-0.0.1-SNAPSHOT.jar'
        DEPLOY_PATH = '/opt/messaging/engine.jar'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh './mvnw clean package -DskipTests'
            }
        }

        stage('Deploy') {
            steps {
                sshagent(['deploy-key']) {
                    script {
                        def servers = [
                            'root@192.168.0.121',
                            'root@192.168.0.233',
                            // 'root@192.168.0.103'  // 추후 활성화
                        ]
                        servers.each { server ->
                            sh """
                                scp -o StrictHostKeyChecking=no \
                                    target/${JAR_NAME} ${server}:${DEPLOY_PATH}
                                ssh -o StrictHostKeyChecking=no ${server} \
                                    'sudo systemctl restart messaging-engine'
                            """
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo '배포 완료'
        }
        failure {
            echo '배포 실패 — 로그를 확인하세요'
        }
    }
}