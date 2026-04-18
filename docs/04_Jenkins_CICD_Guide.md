# 04. Jenkins CI/CD 가이드

## 구성 개요

| 서버 | IP | 역할 |
|---|---|---|
| Nginx + Jenkins | 192.168.0.209 | LB + CI/CD 서버 |
| Spring Boot #1 | 192.168.0.121 | 배포 대상 |
| Spring Boot #2 | 192.168.0.233 | 배포 대상 |
| Spring Boot #3 | 192.168.0.103 | 배포 대상 (추후 활성화) |
| Redis #1 | 192.168.0.136 | 메시지 큐 |
| Redis #2 | 192.168.0.45 | 메시지 큐 (추후 활성화) |

```
[ 개발 PC ]
    │  git push
    ▼
[ Git 저장소 (GitHub) ]
    │  수동 Build Now (내부망이므로 Webhook 불가)
    ▼
[ Jenkins (192.168.0.209:9090) ]
    │  mvnw package → JAR 빌드 (Java 17)
    ├── scp + ssh → Spring Boot #1 (192.168.0.121)
    ├── scp + ssh → Spring Boot #2 (192.168.0.233)
    └── scp + ssh → Spring Boot #3 (192.168.0.103) ← 추후 활성화
```

---

## Step 1. Jenkins 설치 (192.168.0.209)

### 1-1. Java 설치

최신 Jenkins(2.463+)는 **Java 21 이상**이 필요하다. (Jenkins 실행용)
단, 빌드(Maven 컴파일)는 Java 17을 사용한다.

```bash
# Jenkins 실행용 Java 21 설치
sudo dnf install -y java-21-openjdk

# 빌드용 Java 17 devel 설치 (javac 포함)
sudo dnf install -y java-17-openjdk-devel

# 설치 확인
ls /usr/lib/jvm/ | grep -E "17|21"
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

> **주의**: `alternatives --config java`로 기본 Java를 변경하면 Jenkins가 시작되지 않는다.
> override.conf의 JAVA_HOME이 Java 21을 가리키므로 그대로 유지해야 한다.

### 1-5. 초기 비밀번호 확인

```bash
sudo cat /var/lib/jenkins/secrets/initialAdminPassword
```

브라우저에서 `http://192.168.0.209:9090` 접속 → 위 비밀번호 입력 → **Install suggested plugins** 선택

> **내부망 접근 시**: SSH 터널링으로 접속
> ```bash
> ssh -L 9090:192.168.0.209:9090 user@점프서버
> ```
> 이후 `http://localhost:9090` 접속

---

## Step 2. Jenkins 플러그인 및 Tool 설정

### 2-1. SSH Agent 플러그인 설치

Deploy 단계에서 `sshagent` 사용을 위해 필수 설치.

Jenkins CLI로 설치:
```bash
java -jar /var/cache/jenkins/war/WEB-INF/jenkins-cli.jar \
  -s http://localhost:9090 -auth admin:비밀번호 \
  install-plugin ssh-agent -restart
```

또는 Jenkins UI: `Jenkins 관리` → `Plugins` → `Available plugins` → `SSH Agent` 검색 후 설치

### 2-2. Maven Tool 설정

Jenkins 관리 → **Tools** → Maven 섹션:

| 항목 | 설정 |
|---|---|
| Name | `Maven3` |
| Install automatically | 체크 (자동 설치) |

> **JDK Tool 등록 불필요**: Jenkins UI의 JDK 경로 검증 버그로 등록이 안 된다.
> 대신 Jenkinsfile에서 JAVA_HOME을 직접 지정하는 방식을 사용한다.

---

## Step 3. SSH 키 설정 (Jenkins → Spring Boot 서버)

Jenkins 서버에서 SSH 키를 생성하고 각 Spring Boot 서버에 등록한다.

### 3-1. Jenkins 서버에서 키 생성

```bash
sudo -u jenkins ssh-keygen -t rsa -b 4096 -f /var/lib/jenkins/.ssh/id_rsa -N ""
```

### 3-2. 각 Spring Boot 서버에 공개키 등록

```bash
# jenkins 계정으로 실행해야 함
sudo -u jenkins ssh-copy-id -i /var/lib/jenkins/.ssh/id_rsa.pub root@192.168.0.121
sudo -u jenkins ssh-copy-id -i /var/lib/jenkins/.ssh/id_rsa.pub root@192.168.0.233
# sudo -u jenkins ssh-copy-id -i /var/lib/jenkins/.ssh/id_rsa.pub root@192.168.0.103  # 추후 활성화
```

### 3-3. 연결 테스트

```bash
sudo -u jenkins ssh root@192.168.0.121 "echo OK"
sudo -u jenkins ssh root@192.168.0.233 "echo OK"
```

---

## Step 4. Jenkins Credentials 등록

Jenkins 관리 → **Credentials** → System → Global credentials → Add Credentials:

| 항목 | 값 |
|---|---|
| Kind | SSH Username with private key |
| ID | `deploy-key` |
| Username | `root` |
| Private Key | `/var/lib/jenkins/.ssh/id_rsa` 내용 붙여넣기 |

---

## Step 5. Pipeline 생성

### 5-1. 새 Item 생성

Jenkins 대시보드 → **New Item** → 이름: `messaging-engine` → **Pipeline** 선택

### 5-2. Jenkinsfile

프로젝트 루트의 `Jenkinsfile`:

```groovy
pipeline {
    agent any

    tools {
        maven 'Maven3'
    }

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-17.0.18.0.8-1.el8.x86_64'
        PATH = "/usr/lib/jvm/java-17-openjdk-17.0.18.0.8-1.el8.x86_64/bin:${env.PATH}"
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
                sh 'chmod +x ./mvnw && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-17.0.18.0.8-1.el8.x86_64 PATH=$JAVA_HOME/bin:$PATH ./mvnw clean package -DskipTests'
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
```

> **Java 버전 관련**:
> - Jenkins 데몬: Java 21 (systemd override에 고정)
> - Maven 빌드: Java 17 (Jenkinsfile에서 JAVA_HOME 인라인 지정)
> - 배포 서버: Java 17 (운영 정책상 고정)
> - pom.xml: `<java.version>17</java.version>`, Spring Boot 3.3.x

### 5-3. Pipeline 설정

Jenkins 파이프라인 설정에서:
- **Definition**: Pipeline script from SCM
- **SCM**: Git
- **Repository URL**: `https://github.com/621dev/messaging-engine`
- **Script Path**: `Jenkinsfile`

---

## Step 6. Spring Boot 서버 사전 준비

각 Spring Boot 서버(#1, #2)에서 아래 작업을 해둔다.

### 6-1. Java 17 설치 확인

```bash
java -version
# 없으면 설치
sudo dnf install -y java-17-openjdk
```

### 6-2. 배포 디렉토리 및 유저 생성

```bash
sudo useradd -r -s /sbin/nologin messaging
sudo mkdir -p /opt/messaging
sudo chown -R messaging:messaging /opt/messaging
```

### 6-3. 서버 전용 application.properties 작성

JAR 옆에 `application.properties`를 두면 Spring Boot가 자동으로 읽는다.
`WorkingDirectory=/opt/messaging`으로 지정하면 별도 실행 인자 없이 적용된다.

```bash
sudo vi /opt/messaging/application.properties
```

```properties
server.port=8080
spring.data.redis.cluster.nodes=192.168.0.136:6379
# 192.168.0.45:6379  ← Redis #2 활성화 시 추가
```

> **Redis 클러스터 주의**: `spring.data.redis.cluster.nodes` 사용 시 Redis 서버가
> 실제 클러스터 모드(`cluster_enabled:1`)로 구성되어 있어야 한다.
> 단독 모드 Redis라면 `spring.data.redis.host/port` 설정을 사용해야 한다.

### 6-4. systemd 서비스 등록

```bash
sudo vi /etc/systemd/system/messaging-engine.service
```

```ini
[Unit]
Description=Messaging Engine
After=network.target

[Service]
Type=simple
User=messaging
Group=messaging
WorkingDirectory=/opt/messaging
Environment="JAVA_OPTS=-Xms512m -Xmx1g -XX:+UseG1GC"
ExecStart=/usr/bin/java ${JAVA_OPTS} -jar /opt/messaging/engine.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable messaging-engine
```

### 6-5. 방화벽 포트 오픈

```bash
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
```

---

## Step 7. 배포 실행

Jenkins 대시보드 → `messaging-engine` → **Build Now** 클릭

> **자동 트리거 없음**: 내부망 환경으로 GitHub Webhook이 Jenkins에 도달하지 못한다.
> git push 후 수동으로 Build Now를 실행해야 한다.

빌드 로그에서 각 단계 확인:
```
[Checkout] ✓
[Build]    ✓  (Java 17로 컴파일)
[Deploy]   ✓  192.168.0.121
           ✓  192.168.0.233
```

배포 후 확인:
```bash
# 각 서버 상태 확인
ssh root@192.168.0.121 'systemctl status messaging-engine'
ssh root@192.168.0.233 'systemctl status messaging-engine'

# API 확인 (Redis 연결 완료 후)
curl -s http://192.168.0.121:8080/api/messages/status
curl -s http://192.168.0.233:8080/api/messages/status
```

---

## 전체 흐름 요약

```
git push (로컬 또는 젠킨스 서버)
  → Jenkins: Build Now (수동)
      → Checkout → Build (Java 17 mvnw package)
          ├── scp JAR → 192.168.0.121 → systemctl restart
          └── scp JAR → 192.168.0.233 → systemctl restart
```

---

## 검증 체크리스트

- [x] Jenkins 설치 및 9090 포트 접속 확인
- [x] SSH Agent 플러그인 설치
- [x] SSH 키로 Spring Boot 서버 무비밀번호 접속 확인
- [x] Jenkinsfile 프로젝트 루트에 추가 및 GitHub push
- [x] Pipeline 빌드 성공 확인 (#1, #2)
- [ ] Redis 클러스터 구성 확인
- [ ] 각 서버에서 `systemctl status messaging-engine` 정상 확인
- [ ] API 응답 확인 (`/api/messages/status`)
- [ ] Spring Boot #3 (192.168.0.103) 활성화
- [ ] Redis #2 (192.168.0.45) 활성화
