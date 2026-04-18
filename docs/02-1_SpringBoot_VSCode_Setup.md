# 02-1. VSCode로 Spring Boot 메시지 서비스 만들기

> **연계 문서:** `02_Messaging_Stack_Practice.md` → 부록 소스코드를 VSCode로 직접 개발하고 서버에 배포하는 과정

---

## 실습 개요

| 항목 | 내용 |
|------|------|
| 목표 | VSCode에서 Spring Boot 프로젝트를 생성하고, Redis 연동 메시지 API를 만들어 Linux 서버에 배포한다 |
| 전제 조건 | Windows/Mac에 VSCode, JDK 11, Maven 설치 |
| 예상 소요시간 | 약 40~60분 |

---

## Step 1. VSCode 확장 설치

VSCode에서 Spring Boot 개발에 필요한 확장을 설치한다.

1. VSCode 실행 → 좌측 **Extensions** 패널 (`Ctrl+Shift+X`)
2. 아래 확장을 검색하여 설치:

| 확장 이름 | 역할 |
|----------|------|
| **Extension Pack for Java** (Microsoft) | Java 개발 기본 도구 모음 |
| **Spring Boot Extension Pack** (VMware) | Spring Boot 프로젝트 생성 및 실행 지원 |

> 설치 후 VSCode 재시작 권장

---

## Step 2. Spring Initializr로 프로젝트 생성

### 2-1. 명령 팔레트로 프로젝트 생성

1. `Ctrl+Shift+P` → `Spring Initializr: Create a Maven Project` 선택
2. 아래 순서대로 선택:

```ini
# 실습에서는 java 17로 진행
Spring Boot 버전    : 2.7.18 (3.5.13)
언어                : Java
Group Id            : com.practice
Artifact Id         : messaging-engine
Packing name        : com.practice.messagingengine
Packaging           : Jar
Java 버전           : 11 (17)
```

3. 의존성 선택 화면에서 검색 후 추가:
   - `Spring Web`
   - `Spring Data Redis (Access+Driver)`

4. 저장 위치 선택 → 프로젝트 폴더 열기

### 2-2. 생성된 기본 구조 확인

```
messaging-engine/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/practice/messagingengine/
    │   │   └── MessagingEngineApplication.java
    │   └── resources/
    │       └── application.properties
    └── test/
```

---

## Step 3. 소스 코드 작성

### 3-1. 패키지 구조 정리

`src/main/java/com/practice/messagingengine/` 아래에 아래 폴더를 생성한다.  
VSCode 탐색기에서 폴더 우클릭 → **New Folder**:

```
messagingengine/
├── MessagingEngineApplication.java  (자동 생성됨)
├── config/
│   └── RedisConfig.java
├── service/
│   └── MessageService.java
└── controller/
    └── MessageController.java
```

---

### 3-2. RedisConfig.java

`config/RedisConfig.java` 파일 생성 후 작성:

```java
package com.practice.messagingengine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    @Value("${spring.redis.host:localhost}")
    private String host;

    @Value("${spring.redis.port:6379}")
    private int port;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(host, port);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
```

---

### 3-3. MessageService.java

`service/MessageService.java` 파일 생성 후 작성:

```java
package com.practice.messagingengine.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageService {

    private static final String QUEUE_KEY = "message_queue";
    private final StringRedisTemplate redisTemplate;

    public MessageService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void send(String message) {
        redisTemplate.opsForList().rightPush(QUEUE_KEY, message);
    }

    public String consume() {
        return redisTemplate.opsForList().leftPop(QUEUE_KEY);
    }

    public long queueSize() {
        Long size = redisTemplate.opsForList().size(QUEUE_KEY);
        return size != null ? size : 0;
    }

    public List<String> peek(int count) {
        return redisTemplate.opsForList().range(QUEUE_KEY, 0, count - 1);
    }
}
```

---

### 3-4. MessageController.java

`controller/MessageController.java` 파일 생성 후 작성:

```java
package com.practice.messagingengine.controller;

import com.practice.messagingengine.service.MessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> send(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "content is required"));
        }
        messageService.send(content);
        return ResponseEntity.ok(Map.of(
            "status", "queued",
            "content", content,
            "queueSize", messageService.queueSize()
        ));
    }

    @DeleteMapping("/consume")
    public ResponseEntity<Map<String, Object>> consume() {
        String message = messageService.consume();
        if (message == null) {
            return ResponseEntity.ok(Map.of("status", "empty", "message", "queue is empty"));
        }
        return ResponseEntity.ok(Map.of(
            "status", "consumed",
            "message", message,
            "remainingSize", messageService.queueSize()
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        List<String> recent = messageService.peek(5);
        return ResponseEntity.ok(Map.of(
            "queueSize", messageService.queueSize(),
            "recentMessages", recent,
            "serverInfo", System.getenv().getOrDefault("HOSTNAME", "unknown")
        ));
    }
}
```

---

### 3-5. application.properties

`src/main/resources/application.properties` 수정:

```properties
server.port=8080

# 로컬 테스트용 (Redis가 로컬에 있을 때)
spring.redis.host=localhost
spring.redis.port=6379

# 서버 배포 시 아래처럼 실행 인자로 덮어씀
# java -jar engine.jar --spring.redis.host=192.168.20.201
```

---

## Step 4. 로컬에서 실행 및 테스트

### 4-1. VSCode에서 실행

- 좌측 **Spring Boot Dashboard** 패널 → 프로젝트 옆 ▶ 버튼 클릭
- 또는 `MessagingEngineApplication.java` 열기 → `main` 메서드 위 **Run** 클릭
- 또는 VSCode 터미널에서:

```bash
./mvnw spring-boot:run
```

### 4-2. 로컬 Redis 없이 테스트하는 방법

Redis가 로컬에 없다면 Docker로 빠르게 띄운다:

```bash
docker run -d -p 6379:6379 --name redis-test redis:7
```

### 4-3. API 테스트 (VSCode 터미널 또는 REST Client 확장)

**방법 1 - 터미널에서 curl:**

```bash
# 메시지 발송
curl -s -X POST http://localhost:8080/api/messages \
  -H "Content-Type: application/json" \
  -d '{"content": "첫 번째 테스트 메시지"}' | jq

# 큐 상태 확인
curl -s http://localhost:8080/api/messages/status | jq

# 메시지 소비
curl -s -X DELETE http://localhost:8080/api/messages/consume | jq
```
```bash
curl -s -X POST http://localhost:8080/api/messages   -H "Content-Type: application/json"   -d '{"content": "첫 번째 테스트 메시지"}'
{"status":"queued","content":"첫 번째 테스트 메시지","queueSize":1}
curl -s http://localhost:8080/api/messages/status
{"queueSize":1,"recentMessages":["첫 번째 테스트 메시지"],"serverInfo":"DESKTOP-SSCVGA6"}
curl -s -X DELETE http://localhost:8080/api/messages/consume
{"status":"consumed","message":"첫 번째 테스트 메시지","remainingSize":0}
```
**방법 2 - REST Client 확장 사용:**

VSCode Extensions에서 **REST Client** (Huachao Mao) 설치 후, 프로젝트 루트에 `test.http` 파일 생성:

```http
### 메시지 발송
POST http://localhost:8080/api/messages
Content-Type: application/json

{"content": "첫 번째 테스트 메시지"}

### 큐 상태 확인
GET http://localhost:8080/api/messages/status

### 메시지 소비
DELETE http://localhost:8080/api/messages/consume
```

> 각 요청 위의 **Send Request** 버튼 클릭으로 실행, 우측에 응답 바로 확인

---

## Step 5. JAR 빌드 및 Linux 서버 배포

### 5-1. JAR 빌드

VSCode 터미널(`Ctrl+\``)에서:

```bash
./mvnw clean package -DskipTests
```

빌드 완료 후 확인:

```bash
ls -lh target/messaging-engine-0.0.1-SNAPSHOT.jar
```

### 5-2. 서버로 전송

```bash
# Spring Boot #1 서버로 전송
scp target/messaging-engine-0.0.1-SNAPSHOT.jar \
  user@192.168.20.101:/opt/messaging/engine.jar

# Spring Boot #2 서버로도 동일하게
scp target/messaging-engine-0.0.1-SNAPSHOT.jar \
  user@192.168.20.102:/opt/messaging/engine.jar
```

### 5-3. 서버에서 동작 확인 (배포 직후)

```bash
# Redis 클러스터 IP를 인자로 넘겨서 실행
java -jar /opt/messaging/engine.jar \
  --spring.redis.host=192.168.20.201 &

# 기동 확인
curl -s http://192.168.20.101:8080/api/messages/status | jq
```

> 정상 응답 확인 후 `Ctrl+C` 로 종료하고, **02_Messaging_Stack_Practice.md Step 2** 의 systemd 서비스 등록으로 이동한다.

---

## 전체 흐름 요약

```
[VSCode]                         [Linux 서버]
  │                                   │
  ├─ Spring Initializr로 프로젝트 생성   │
  ├─ 소스코드 작성 (Controller/Service)  │
  ├─ 로컬 테스트 (REST Client)          │
  ├─ mvnw package → JAR 생성           │
  └─ scp로 서버 전송 ──────────────────→ engine.jar 배포
                                       ├─ 직접 실행 테스트
                                       └─ systemd 서비스 등록
                                          (02_Messaging_Stack_Practice Step 2)
```

---

## 검증 체크리스트

- [ ] Spring Boot Extension Pack 설치 확인
- [ ] Spring Initializr로 프로젝트 생성 완료 (Web + Redis 의존성)
- [ ] 4개 파일 작성 완료 (RedisConfig, MessageService, MessageController, application.properties)
- [ ] VSCode에서 앱 실행 후 포트 8080 기동 확인
- [ ] REST Client 또는 curl로 POST/GET/DELETE 응답 확인
- [ ] `mvnw package` 로 JAR 생성 확인
- [ ] scp로 서버 전송 후 `java -jar` 직접 실행 확인
