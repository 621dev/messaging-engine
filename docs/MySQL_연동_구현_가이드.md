# MySQL 연동 구현 가이드

Redis 큐 + MySQL 이력 저장 구조를 구현하기 위한 단계별 가이드입니다.

---

## 전체 목표 구조

```
API 요청
    ↓
MessageService.send()
    ↙              ↘
Redis              MySQL
RPUSH message_queue  INSERT message_log (status=PENDING)

MessageService.consume()
    ↙              ↘
Redis              MySQL
LPOP message_queue   UPDATE message_log (status=SUCCESS)
```

---

## Step 1. pom.xml 의존성 추가

`spring-boot-starter-data-jpa`와 `mysql-connector-j` 두 가지를 추가합니다.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

## Step 2. application properties에 MySQL 설정 추가

### application-local.properties

기존 Redis 설정 아래에 추가합니다.

```properties
spring.datasource.url=jdbc:mysql://192.168.0.247:3306/messaging_db?serverTimezone=Asia/Seoul
spring.datasource.username=root
spring.datasource.password=비밀번호
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

### application-dev.properties

```properties
spring.datasource.url=jdbc:mysql://DB서버IP:3306/messaging_db?serverTimezone=Asia/Seoul
spring.datasource.username=app_user
spring.datasource.password=비밀번호
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
```

> `ddl-auto` 옵션:
> - `update` — 테이블이 없으면 자동 생성, 로컬 개발용
> - `validate` — 테이블이 이미 있어야 함, 운영/dev 환경용

---

## Step 3. MessageStatus enum 생성

위치: `src/main/java/com/practice/messagingengine/domain/MessageStatus.java`

```java
public enum MessageStatus {
    PENDING,   // Redis 큐에 적재됨
    SUCCESS,   // 소비(처리) 완료
    FAILED     // 처리 실패
}
```

---

## Step 4. MessageLog Entity 생성

위치: `src/main/java/com/practice/messagingengine/domain/MessageLog.java`

운영 가이드 DB 스키마를 기반으로 작성합니다.

```java
@Entity
@Table(name = "message_log")
public class MessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true)
    private String messageId;

    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    private MessageStatus status;

    private LocalDateTime sentAt;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // getter / setter
}
```

---

## Step 5. MessageLogRepository 생성

위치: `src/main/java/com/practice/messagingengine/repository/MessageLogRepository.java`

```java
public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {

    Optional<MessageLog> findByMessageId(String messageId);
}
```

---

## Step 6. MessageService 수정

기존 `MessageService`에 `MessageLogRepository`를 주입하고, 각 메서드에 MySQL 저장 로직을 추가합니다.

### send() 수정

```
기존: Redis RPUSH만 수행
변경: MySQL INSERT(PENDING) → Redis RPUSH
      (Redis에 넣는 값을 messageId로 변경)
```

### consume() 수정

```
기존: Redis LPOP만 수행
변경: Redis LPOP(messageId 꺼냄) → MySQL UPDATE(SUCCESS)
```

### 구현 포인트

- Redis에 content 전체를 넣는 것이 아니라 `messageId`(UUID)를 넣는 것이 핵심
- content는 MySQL에만 저장하고, Redis는 처리 순서 관리에만 사용
- consume() 후 MySQL 조회는 `findByMessageId(messageId)`로 수행

---

## Step 7. 실패 복구 로직 (선택)

Redis가 재시작되면 큐가 비워질 수 있습니다.
아래 메서드를 `MessageService`에 추가하면 MySQL에서 PENDING 상태 메시지를 다시 Redis에 적재할 수 있습니다.

```
MySQL에서 status=PENDING 전체 조회
    ↓
각 messageId를 Redis RPUSH
```

이 메서드는 애플리케이션 시작 시 `@PostConstruct`로 자동 실행하거나,
별도 `/api/messages/recover` 엔드포인트로 수동 호출하도록 구성할 수 있습니다.

---

## 최종 디렉토리 구조

```
src/main/java/com/practice/messagingengine/
├── config/
│   └── RedisConfig.java          (기존 유지)
├── controller/
│   └── MessageController.java    (기존 유지)
├── domain/
│   ├── MessageLog.java           (신규)
│   └── MessageStatus.java        (신규)
├── repository/
│   └── MessageLogRepository.java (신규)
├── service/
│   └── MessageService.java       (수정)
└── MessagingEngineApplication.java
```

---

## 확인 방법

구현 완료 후 아래 순서로 테스트합니다.

1. `POST /api/messages` 호출 → MySQL `message_log` 테이블에 `PENDING` 레코드 생성 확인
2. `DELETE /api/messages/consume` 호출 → 해당 레코드 `SUCCESS`로 업데이트 확인
3. 애플리케이션 재시작 후 recover 로직 동작 확인 (Step 7 구현 시)
