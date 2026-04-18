# messaging-engine 코드 설명

## 프로젝트 개요

Redis를 이용한 간단한 메시지 큐 엔진 실습 프로젝트.
Spring Boot + Redis List 자료구조로 메시지를 발행/소비하는 구조를 학습한다.

- Java 17, Spring Boot 3.5.13, Maven
- 의존성: `spring-boot-starter-data-redis`, `spring-boot-starter-web`

---

## pom.xml

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.13</version>
</parent>
```

`spring-boot-starter-parent`를 부모로 설정하면 Spring Boot가 관리하는 모든 라이브러리의 **버전을 자동으로 맞춰준다.** 의존성에 `<version>` 태그를 직접 쓰지 않아도 된다.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Redis 연동에 필요한 라이브러리를 한 번에 가져온다.
내부적으로 **Lettuce**(기본 Redis 클라이언트)와 `spring-data-redis`가 포함된다.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

REST API를 만들기 위한 의존성. 내부적으로 **Tomcat + Spring MVC**가 포함된다.

---

## MessagingEngineApplication.java

```java
@SpringBootApplication
public class MessagingEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(MessagingEngineApplication.class, args);
    }
}
```

Spring Boot 애플리케이션의 진입점(entry point).

| 요소 | 역할 |
|---|---|
| `@SpringBootApplication` | `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan` 세 개를 합친 어노테이션. 이 클래스가 있는 패키지부터 하위 패키지를 자동으로 스캔한다. |
| `SpringApplication.run()` | Spring 컨테이너(ApplicationContext)를 초기화하고 내장 Tomcat을 실행한다. |

---

## RedisConfig.java

```java
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

Redis 연결을 설정하고, 애플리케이션 전체에서 사용할 Redis 객체를 Bean으로 등록하는 설정 클래스.

| 요소 | 역할 |
|---|---|
| `@Configuration` | 이 클래스가 Bean 정의 소스임을 Spring에게 알린다. |
| `@Value("${...}")` | `application.properties`의 값을 주입한다. `:` 뒤는 기본값으로, 설정이 없으면 `localhost:6379`를 사용한다. |
| `LettuceConnectionFactory` | Lettuce 드라이버로 Redis 서버에 TCP 연결을 맺는 팩토리. |
| `StringRedisTemplate` | Redis에 String 타입으로 데이터를 읽고 쓰는 헬퍼 클래스. `opsForList()`, `opsForValue()` 등의 메서드를 제공한다. |

> **왜 `@Bean`으로 직접 등록하나?**
> Spring Boot AutoConfiguration이 Redis Bean을 자동 생성해주지만, host/port를 커스텀하거나 설정을 명시적으로 제어할 때는 직접 등록한다.

---

## application.properties

```properties
spring.application.name=messaging-engine
```

현재는 애플리케이션 이름만 설정되어 있다.
Redis 연결을 위해 아래 설정을 추가해야 한다.

```properties
# Redis 서버 주소 (RedisConfig.java의 @Value와 대응)
spring.redis.host=localhost
spring.redis.port=6379

# 서버 포트 (기본값 8080)
server.port=8080
```

> `application.properties`의 키 이름이 `@Value("${키이름}")` 과 정확히 일치해야 값이 주입된다.

---

## 전체 흐름 요약

```
애플리케이션 시작
    └── MessagingEngineApplication.main()
            └── Spring 컨테이너 초기화
                    ├── RedisConfig → LettuceConnectionFactory 생성 (Redis 연결)
                    ├── RedisConfig → StringRedisTemplate 생성 (Redis 조작 도구)
                    └── (구현 예정) MessageService, MessageController Bean 등록
```

1. 앱이 시작되면 `RedisConfig`가 실행되어 Redis 연결이 준비된다.
2. `StringRedisTemplate`을 `MessageService`에 주입(DI)하여 Redis List에 메시지를 push/pop한다.
3. `MessageController`가 HTTP 요청을 받아 `MessageService`를 호출한다.
