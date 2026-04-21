# 06. API 사용 가이드

> **API Base URL**: `http://{nginx_ip}` (80포트, nginx 경유)  
> **Spring 직접**: `http://{server_ip}:8080`  
> **모든 요청/응답**: `Content-Type: application/json`

---

## 목차

1. [환경 변수 설정](#1-환경-변수-설정)
2. [메시지 발송](#2-메시지-발송)
3. [큐 상태 확인](#3-큐-상태-확인)
4. [메시지 처리 (consume)](#4-메시지-처리-consume)
5. [워커 제어](#5-워커-제어)

---

## 1. 환경 변수 설정

```bash
export NGINX_URL="http://192.168.0.xxx"
```

---

## 2. 메시지 발송

### `POST /api/messages`

Redis 큐에 메시지를 적재합니다.

**요청**

```bash
curl -X POST $NGINX_URL/api/messages \
  -H "Content-Type: application/json" \
  -d '{
    "senderId": "sender01",
    "receiver": "01012345678",
    "messageType": "SMS",
    "content": "안녕하세요"
  }'
```

**messageType 종류**

| 값 | 설명 |
|----|------|
| `SMS` | 단문 문자 |
| `LMS` | 장문 문자 |
| `KAKAO` | 카카오 알림톡 |
| `EMAIL` | 이메일 |

**응답**

```json
{
  "status": "queued",
  "messageId": "3ca5572d-072c-4d9c-aeb3-bfedc3602c69",
  "queueSize": 1
}
```

---

## 3. 큐 상태 확인

### `GET /api/messages/status`

현재 Redis 큐 크기와 최근 messageId 5건을 반환합니다.

```bash
curl -s $NGINX_URL/api/messages/status | jq .
```

**응답**

```json
{
  "queueSize": 468240,
  "recentMessageIds": [
    "3ca5572d-072c-4d9c-aeb3-bfedc3602c69",
    "f8b3c739-6765-4fa5-8b8b-c0bf0bcffa23"
  ],
  "serverInfo": "messaging-server-1"
}
```

---

## 4. 메시지 처리 (consume)

Redis 큐에서 메시지를 꺼내 발송하고 MySQL에 저장합니다.  
발송 완료 후 Redis에서 삭제됩니다.

### `DELETE /api/messages/consume?limit={n}`

지정한 수만큼 배치 처리합니다. 기본값은 100건입니다.

```bash
# 기본 100건
curl -X DELETE $NGINX_URL/api/messages/consume

# 직접 지정
curl -X DELETE "$NGINX_URL/api/messages/consume?limit=1000"
```

**응답**

```json
{
  "status": "consumed",
  "count": 1000,
  "remainingSize": 467240
}
```

큐가 비어 있을 경우:

```json
{
  "status": "empty",
  "message": "queue is empty"
}
```

---

### `DELETE /api/messages/consume/{messageId}`

특정 messageId 하나만 처리합니다.

```bash
curl -X DELETE $NGINX_URL/api/messages/consume/3ca5572d-072c-4d9c-aeb3-bfedc3602c69
```

**응답**

```json
{
  "status": "SUCCESS",
  "messageId": "3ca5572d-072c-4d9c-aeb3-bfedc3602c69",
  "messageType": "SMS",
  "remainingSize": 468239
}
```

큐에 없는 messageId일 경우:

```json
{
  "status": "not_in_queue",
  "messageId": "3ca5572d-072c-4d9c-aeb3-bfedc3602c69"
}
```

---

## 5. 워커 제어

워커는 1초마다 자동으로 100건씩 consume을 반복합니다.  
서버 기동 시 기본 **off** 상태입니다.

### `POST /api/messages/worker/on`

워커를 활성화합니다.

```bash
curl -X POST $NGINX_URL/api/messages/worker/on
```

**응답**

```json
{ "worker": "on" }
```

---

### `POST /api/messages/worker/off`

워커를 비활성화합니다.

```bash
curl -X POST $NGINX_URL/api/messages/worker/off
```

**응답**

```json
{ "worker": "off" }
```

---

### `GET /api/messages/worker/status`

워커 상태와 현재 큐 크기를 반환합니다.

```bash
curl -s $NGINX_URL/api/messages/worker/status | jq .
```

**응답**

```json
{
  "worker": "on",
  "queueSize": 467240
}
```

---

## 참고: 큐 소진 예상 시간

워커(1초당 100건 기준) 가동 시:

| 잔여 건수 | 예상 소요 시간 |
|-----------|--------------|
| 10,000건 | 약 1.7분 |
| 100,000건 | 약 17분 |
| 468,000건 | 약 78분 |
