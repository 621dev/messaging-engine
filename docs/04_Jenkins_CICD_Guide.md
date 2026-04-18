# 04. Jenkins CI/CD 가이드

## 구성 개요

| 서버 | IP | 역할 |
|---|---|---|
| Nginx + Jenkins | 192.168.0.209 | LB + CI/CD 서버 |
| Spring Boot #1 | 192.168.0.121 | 배포 대상 |
| Spring Boot #2 | 192.168.0.233 | 배포 대상 |
| Spring Boot #3 | 192.168.0.103 | 배포 대상 |
| Redis #1 | 192.168.0.136 | 메시지 큐 |
| Redis #2 | 192.168.0.45 | 메시지 큐 |

```
[ 개발 PC ]
    │  git push
    ▼
[ Git 저장소 (GitHub / Gitea) ]
    │  Webhook 또는 Poll
    ▼
[ Jenkins (192.168.0.209) ]
    │  mvnw package → JAR 빌드
    ├── scp + ssh → Spring Boot #1 (192.168.0.121)
    ├── scp + ssh → Spring Boot #2 (192.168.0.233)
    └── scp + ssh → Spring Boot #3 (192.168.0.103)
```

---

## Step 1. Jenkins 설치 (192.168.0.209)

### 1-1. Java 설치 확인

최신 Jenkins(2.463+)는 **Java 21 이상**이 필요하다.

```bash
java -version
# 없으면 설치
sudo dnf install -y java-21-openjdk

# 기본 Java 변경
sudo alternatives --config java
# 목록에서 java-21 번호 입력 후 확인
java -version
```

### 1-2. Jenkins 저장소 추가 및 설치

```bash
sudo wget -O /etc/yum.repos.d/jenkins.repo \
  https://pkg.jenkins.io/redhat-stable/jenkins.repo

sudo rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.io-2023.key

sudo dnf install -y jenkins
```

### 1-3. Jenkins 서비스 시작

```bash
sudo systemctl enable jenkins
sudo systemctl start jenkins
sudo systemctl status jenkins
```

### 1-4. 방화벽 포트 오픈 (8080 충돌 주의)

Spring Boot가 8080을 사용하므로 Jenkins는 **9090 포트**로 변경한다.
최신 Jenkins는 `/etc/sysconfig/jenkins` 파일이 없으므로 systemd override로 설정한다.

```bash
sudo mkdir -p /etc/systemd/system/jenkins.service.d
sudo vi /etc/systemd/system/jenkins.service.d/override.conf
```

아래 내용 입력:
```ini
[Service]
Environment="JENKINS_PORT=9090"
Environment="JAVA_HOME=/usr/lib/jvm/java-21-openjdk"
```

```bash
sudo systemctl daemon-reload
sudo systemctl restart jenkins
sudo firewall-cmd --permanent --add-port=9090/tcp
sudo firewall-cmd --reload
```

### 1-5. 초기 비밀번호 확인

```bash
sudo cat /var/lib/jenkins/secrets/initialAdminPassword
```

브라우저에서 `http://192.168.0.209:9090` 접속 → 위 비밀번호 입력 → **Install suggested plugins** 선택

---

## Step 2. Jenkins에 Maven 및 JDK 설정

Jenkins 관리 → **Tools** → 아래 항목 설정:

| 항목 | 설정 |
|---|---|
| JDK | Name: `JDK21`, JAVA_HOME: `/usr/lib/jvm/java-21-openjdk` |
| Maven | Name: `Maven3`, 자동 설치 체크 또는 경로 지정 |

---

## Step 3. SSH 키 설정 (Jenkins → Spring Boot 서버)

Jenkins 서버에서 SSH 키를 생성하고 각 Spring Boot 서버에 등록한다.

### 3-1. Jenkins 서버에서 키 생성

```bash
sudo -u jenkins ssh-keygen -t rsa -b 4096 -f /var/lib/jenkins/.ssh/id_rsa -N ""
```

### 3-2. 각 Spring Boot 서버에 공개키 등록

```bash
# Spring Boot #1
ssh-copy-id -i /var/lib/jenkins/.ssh/id_rsa.pub user@192.168.0.121

# Spring Boot #2
ssh-copy-id -i /var/lib/jenkins/.ssh/id_rsa.pub user@192.168.0.233

# Spring Boot #3
ssh-copy-id -i /var/lib/jenkins/.ssh/id_rsa.pub user@192.168.0.103
```

### 3-3. 연결 테스트

```bash
sudo -u jenkins ssh user@192.168.0.121 "echo OK"
sudo -u jenkins ssh user@192.168.0.233 "echo OK"
sudo -u jenkins ssh user@192.168.0.103 "echo OK"
```

---

## Step 4. Jenkins Credentials 등록

Jenkins 관리 → **Credentials** → System → Global credentials → Add Credentials:

| 항목 | 값 |
|---|---|
| Kind | SSH Username with private key |
| ID | `deploy-key` |
| Username | 서버 로그인 유저명 |
| Private Key | `/var/lib/jenkins/.ssh/id_rsa` 내용 붙여넣기 |

---

## Step 5. Pipeline 생성

### 5-1. 새 Item 생성

Jenkins 대시보드 → **New Item** → 이름: `messaging-engine` → **Pipeline** 선택

### 5-2. Jenkinsfile 작성

프로젝트 루트에 `Jenkinsfile` 생성:

```groovy
pipeline {
    agent any

    tools {
        jdk 'JDK21'
        maven 'Maven3'
    }

    environment {
        JAR_NAME = 'messaging-engine-0.0.1-SNAPSHOT.jar'
        DEPLOY_PATH = '/opt/messaging/engine.jar'
        REDIS_HOST = '192.168.0.136'
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
                            'user@192.168.0.121',
                            'user@192.168.0.233',
                            'user@192.168.0.103'
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
```

### 5-3. Pipeline 설정

Jenkins 파이프라인 설정에서:
- **Definition**: Pipeline script from SCM
- **SCM**: Git
- **Repository URL**: Git 저장소 주소
- **Script Path**: `Jenkinsfile`

---

## Step 6. Spring Boot 서버 사전 준비

각 Spring Boot 서버(#1~#3)에서 아래 작업을 해둔다.

### 6-1. 배포 디렉토리 생성

```bash
sudo mkdir -p /opt/messaging
sudo chown user:user /opt/messaging
```

### 6-2. systemd 서비스 등록

```bash
sudo vi /etc/systemd/system/messaging-engine.service
```

```ini
[Unit]
Description=Messaging Engine
After=network.target

[Service]
User=user
ExecStart=/usr/bin/java -jar /opt/messaging/engine.jar \
  --spring.data.redis.host=192.168.0.136 \
  --spring.data.redis.port=6379
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable messaging-engine
```

---

## Step 7. 배포 실행

Jenkins 대시보드 → `messaging-engine` → **Build Now** 클릭

빌드 로그에서 각 단계 확인:
```
[Checkout] ✓
[Build]    ✓
[Deploy]   ✓ 192.168.0.121
           ✓ 192.168.0.233
           ✓ 192.168.0.103
```

배포 후 확인:
```bash
curl -s http://192.168.0.209/api/messages/status
```

---

## 전체 흐름 요약

```
git push
  → Jenkins: Checkout → Build (mvnw package) → Deploy
      ├── scp JAR → 192.168.0.121 → systemctl restart
      ├── scp JAR → 192.168.0.233 → systemctl restart
      └── scp JAR → 192.168.0.103 → systemctl restart
```

---

## 검증 체크리스트

- [ ] Jenkins 설치 및 9090 포트 접속 확인
- [ ] SSH 키로 Spring Boot 서버 3대 무비밀번호 접속 확인
- [ ] Jenkinsfile 프로젝트 루트에 추가
- [ ] Pipeline 빌드 성공 확인
- [ ] 각 서버에서 `systemctl status messaging-engine` 확인
- [ ] Nginx LB를 통한 API 응답 확인
