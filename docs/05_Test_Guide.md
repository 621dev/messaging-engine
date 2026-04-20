# 05. 통합 테스트 & 스트레스 테스트 가이드

> **대상 스택**: nginx → Spring Boot (messaging-engine) → Redis Cluster → MySQL  
> **API Base URL**: `http://{nginx_ip}` (80포트, nginx 경유)  
> **Spring 직접**: `http://{server_ip}:8080`

---

## 목차

1. [사전 준비](#1-사전-준비)
2. [계층별 단위 확인](#2-계층별-단위-확인)
3. [API 기능 테스트 (curl)](#3-api-기능-테스트-curl)
4. [시나리오 테스트 (쉘 스크립트)](#4-시나리오-테스트-쉘-스크립트)
5. [스트레스 테스트 도구별 방법](#5-스트레스-테스트-도구별-방법)
6. [스트레스 테스트 중 실시간 모니터링](#6-스트레스-테스트-중-실시간-모니터링)
7. [장애 주입 테스트 (Chaos)](#7-장애-주입-테스트-chaos)
8. [결과 판단 기준](#8-결과-판단-기준)

---

## 1. 사전 준비

### 환경 변수 설정 (테스트 시작 전 1회)

```bash
# 실제 서버 IP로 교체
export NGINX_URL="http://192.168.0.xxx"
export SPRING_URL="http://192.168.0.xxx:8080"
export REDIS_HOST="192.168.0.136"
export MYSQL_HOST="192.168.0.247"
export MYSQL_USER="messaging_user"
export MYSQL_PASS="1212"
export MYSQL_DB="messaging_db"
```

### 도구 설치 확인

```bash
# curl, jq (JSON 파싱), mysql-client, redis-cli 필수
curl --version
jq --version
mysql --version
redis-cli --version

# 없으면 설치 (CentOS/RHEL 기준)
yum install -y curl jq mysql redis
```

---

## 2. 계층별 단위 확인

테스트 전에 각 계층이 독립적으로 정상인지 확인합니다.

### 2-1. nginx 확인

```bash
# nginx 프로세스 확인 (서버에서)
systemctl status nginx

# nginx → Spring 프록시 응답 확인
curl -v $NGINX_URL/api/messages/status

# 응답 헤더에서 nginx 서버 확인
curl -I $NGINX_URL/api/messages/status | grep -i server
```

### 2-2. Spring Boot 확인

```bash
# Spring 직접 접근 (nginx 우회)
curl -s $SPRING_URL/actuator/health | jq .

# 정상 응답
# {
#   "status": "UP"
# }

# 상세 정보 (application.properties에 따라 다름)
curl -s $SPRING_URL/actuator/health | jq '.components'
```

### 2-3. Redis Cluster 확인

```bash
# 클러스터 상태 조회
redis-cli -c -h $REDIS_HOST -p 6379 cluster info | grep cluster_state
# cluster_state:ok 이어야 정상

# 클러스터 노드 목록
redis-cli -c -h $REDIS_HOST -p 6379 cluster nodes

# 현재 큐 크기
redis-cli -c -h $REDIS_HOST -p 6379 LLEN message_queue

# 큐 앞 5개 항목 미리보기
redis-cli -c -h $REDIS_HOST -p 6379 LRANGE message_queue 0 4
```

### 2-4. MySQL 확인

```bash
# 연결 확인
mysql -h $MYSQL_HOST -u $MYSQL_USER -p$MYSQL_PASS $MYSQL_DB \
  -e "SELECT NOW() AS server_time, VERSION() AS version;"

# 테이블 구조 확인
mysql -h $MYSQL_HOST -u $MYSQL_USER -p$MYSQL_PASS $MYSQL_DB \
  -e "DESCRIBE message_log;"

# 최근 데이터 확인
mysql -h $MYSQL_HOST -u $MYSQL_USER -p$MYSQL_PASS $MYSQL_DB \
  -e "SELECT message_id, status, sender_id, created_at FROM message_log ORDER BY created_at DESC LIMIT 5;"
```

---

## 3. API 기능 테스트 (curl)

### 3-1. POST /api/messages — 메시지 전송

**정상 케이스 (각 MessageType별)**

```bash
# SMS
curl -s -X POST $NGINX_URL/api/messages \
  -H "Content-Type: application/json" \
  -d '{"senderId":"user1","receiver":"01012345678","content":"SMS 테스트","messageType":"SMS"}' | jq .

# LMS
curl -s -X POST $NGINX_URL/api/messages \
  -H "Content-Type: application/json" \
  -d '{"senderId":"user1","receiver":"01012345678","content":"LMS 장문 테스트 메시지입니다.","messageType":"LMS"}' | jq .

# KAKAO
curl -s -X POST $NGINX_URL/api/messages \
  -H "Content-Type: application/json" \
  -d '{"senderId":"biz_channel","receiver":"kakao_id_123","content":"카카오 알림톡 테스트","messageType":"KAKAO"}' | jq .

# EMAIL
curl -s -X POST $NGINX_URL/api/messages \
  -H "Content-Type: application/json" \
  -d '{"senderId":"system","receiver":"test@example.com","content":"이메일 테스트","messageType":"EMAIL"}' | jq .
```

**예상 응답**
```json
{
  "status": "queued",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "queueSize": 1
}
```

**이상 케이스 (에러 핸들링 확인)**

```bash
# content 누락 → 400 Bad Request 기대
curl -s -X POST $NGINX_URL/api/messages \
  -H "Content-Type: application/json" \
  -d '{"senderId":"user1","receiver":"01012345678","messageType":"SMS"}' | jq .

# content 빈 문자열 → 400 Bad Request 기대
curl -s -X POST $NGINX_URL/api/messages \
  -H "Content-Type: application/json" \
  -d '{"senderId":"user1","content":"","messageType":"SMS"}' | jq .

# Body 없음 → 400 기대
curl -s -X POST $NGINX_URL/api/messages \
  -H "Content-Type: application/json" | jq .

# 잘못된 MessageType → 에러 기대
curl -s -X POST $NGINX_URL/api/messages \
  -H "Content-Type: application/json" \
  -d '{"senderId":"user1","content":"test","messageType":"INVALID"}' | jq .
```

### 3-2. GET /api/messages/status — 큐 상태 조회

```bash
curl -s $NGINX_URL/api/messages/status | jq .
```

**예상 응답**
```json
{
  "queueSize": 1,
  "recentMessageIds": ["550e8400-e29b-41d4-a716-446655440000"],
  "serverInfo": "node1"
}
```

### 3-3. DELETE /api/messages/consume — 메시지 소비

```bash
# 큐에 메시지가 있을 때
curl -s -X DELETE $NGINX_URL/api/messages/consume | jq .

# 예상 응답
# {
#   "status": "consumed",
#   "messageId": "550e8400-...",
#   "content": "SMS 테스트",
#   "remainingSize": 0
# }

# 큐가 비어 있을 때
curl -s -X DELETE $NGINX_URL/api/messages/consume | jq .
# {
#   "status": "empty",
#   "message": "queue is empty"
# }
```

### 3-4. send → status → consume 흐름 검증

```bash
echo "=== 1. 메시지 전송 ==="
RESPONSE=$(curl -s -X POST $NGINX_URL/api/messages \
  -H "Content-Type: application/json" \
  -d '{"senderId":"tester","receiver":"01099999999","content":"흐름 테스트","messageType":"SMS"}')
echo $RESPONSE | jq .
MSG_ID=$(echo $RESPONSE | jq -r '.messageId')

echo ""
echo "=== 2. 큐 상태 확인 ==="
curl -s $NGINX_URL/api/messages/status | jq .

echo ""
echo "=== 3. Redis 직접 확인 ==="
redis-cli -c -h $REDIS_HOST LLEN message_queue

echo ""
echo "=== 4. MySQL PENDING 확인 ==="
mysql -h $MYSQL_HOST -u $MYSQL_USER -p$MYSQL_PASS $MYSQL_DB \
  -e "SELECT message_id, status, created_at FROM message_log WHERE message_id='$MSG_ID';"

echo ""
echo "=== 5. 메시지 소비 ==="
curl -s -X DELETE $NGINX_URL/api/messages/consume | jq .

echo ""
echo "=== 6. MySQL SUCCESS 확인 ==="
mysql -h $MYSQL_HOST -u $MYSQL_USER -p$MYSQL_PASS $MYSQL_DB \
  -e "SELECT message_id, status, sent_at FROM message_log WHERE message_id='$MSG_ID';"
```

---

## 4. 시나리오 테스트 (쉘 스크립트)

### 4-1. 다중 메시지 순서 보장 테스트

Redis List는 FIFO 구조이므로 전송 순서와 소비 순서가 일치해야 합니다.

```bash
cat > /tmp/test_fifo.sh << 'EOF'
#!/bin/bash
NGINX_URL="${NGINX_URL:-http://192.168.0.xxx}"
REDIS_HOST="${REDIS_HOST:-192.168.0.136}"

echo "=== FIFO 순서 보장 테스트 ==="
SENT_IDS=()

# 5개 전송
for i in {1..5}; do
  RES=$(curl -s -X POST $NGINX_URL/api/messages \
    -H "Content-Type: application/json" \
    -d "{\"senderId\":\"tester\",\"receiver\":\"recv\",\"content\":\"msg-$i\",\"messageType\":\"SMS\"}")
  ID=$(echo $RES | jq -r '.messageId')
  SENT_IDS+=($ID)
  echo "전송 $i: $ID"
done

echo ""
echo "=== 소비 순서 확인 ==="
for i in {1..5}; do
  RES=$(curl -s -X DELETE $NGINX_URL/api/messages/consume)
  CONSUMED_ID=$(echo $RES | jq -r '.messageId')
  EXPECTED_ID=${SENT_IDS[$((i-1))]}
  if [ "$CONSUMED_ID" = "$EXPECTED_ID" ]; then
    echo "소비 $i: $CONSUMED_ID ✓ (순서 일치)"
  else
    echo "소비 $i: $CONSUMED_ID ✗ (기대: $EXPECTED_ID)"
  fi
done
EOF
chmod +x /tmp/test_fifo.sh
bash /tmp/test_fifo.sh
```

### 4-2. 동시 전송 후 MySQL 정합성 확인

```bash
cat > /tmp/test_concurrent.sh << 'EOF'
#!/bin/bash
NGINX_URL="${NGINX_URL:-http://192.168.0.xxx}"
MYSQL_HOST="${MYSQL_HOST:-192.168.0.247}"
COUNT=50

echo "=== 동시 전송 $COUNT 건 ==="
for i in $(seq 1 $COUNT); do
  curl -s -X POST $NGINX_URL/api/messages \
    -H "Content-Type: application/json" \
    -d "{\"senderId\":\"concurrent_$i\",\"receiver\":\"recv\",\"content\":\"concurrent msg $i\",\"messageType\":\"SMS\"}" \
    > /dev/null &
done
wait
echo "전송 완료"

echo ""
echo "=== Redis 큐 크기 ==="
redis-cli -c -h $REDIS_HOST LLEN message_queue

echo ""
echo "=== MySQL PENDING 건수 ==="
mysql -h $MYSQL_HOST -u messaging_user -p1212 messaging_db \
  -e "SELECT status, COUNT(*) as cnt FROM message_log GROUP BY status;"
EOF
chmod +x /tmp/test_concurrent.sh
bash /tmp/test_concurrent.sh
```

### 4-3. 대량 consume 후 잔량 0 확인

```bash
cat > /tmp/test_drain.sh << 'EOF'
#!/bin/bash
NGINX_URL="${NGINX_URL:-http://192.168.0.xxx}"

echo "=== 큐 전체 소비 테스트 ==="
TOTAL=0
while true; do
  RES=$(curl -s -X DELETE $NGINX_URL/api/messages/consume)
  STATUS=$(echo $RES | jq -r '.status')
  if [ "$STATUS" = "empty" ]; then
    echo "큐 소진 완료. 총 소비: $TOTAL 건"
    break
  fi
  TOTAL=$((TOTAL+1))
  echo "소비 #$TOTAL: $(echo $RES | jq -r '.messageId')"
done

echo ""
echo "최종 큐 크기:"
curl -s $NGINX_URL/api/messages/status | jq '.queueSize'
EOF
chmod +x /tmp/test_drain.sh
bash /tmp/test_drain.sh
```

### 4-4. nginx 로드밸런싱 분산 확인

```bash
cat > /tmp/test_lb.sh << 'EOF'
#!/bin/bash
NGINX_URL="${NGINX_URL:-http://192.168.0.xxx}"
COUNT=20
declare -A NODE_COUNT

echo "=== nginx 로드밸런싱 $COUNT 회 확인 ==="
for i in $(seq 1 $COUNT); do
  NODE=$(curl -s $NGINX_URL/api/messages/status | jq -r '.serverInfo')
  NODE_COUNT[$NODE]=$((${NODE_COUNT[$NODE]:-0}+1))
done

echo ""
echo "노드별 요청 수:"
for NODE in "${!NODE_COUNT[@]}"; do
  echo "  $NODE: ${NODE_COUNT[$NODE]} 건"
done
EOF
chmod +x /tmp/test_lb.sh
bash /tmp/test_lb.sh
```

---

## 5. 스트레스 테스트 도구별 방법

### 5-1. ab (Apache Bench) — 빠른 기본 테스트

```bash
# 설치
yum install -y httpd-tools

# payload 파일 준비
echo '{"senderId":"stress","receiver":"01099999999","content":"stress test message","messageType":"SMS"}' \
  > /tmp/ab_payload.json

# 기본: 1,000건 / 동시 50
ab -n 1000 -c 50 \
   -p /tmp/ab_payload.json \
   -T "application/json" \
   $NGINX_URL/api/messages

# 고부하: 5,000건 / 동시 200
ab -n 5000 -c 200 \
   -p /tmp/ab_payload.json \
   -T "application/json" \
   $NGINX_URL/api/messages

# 결과에서 체크할 핵심 항목
# Requests per second:    ← TPS
# Time per request:       ← 평균 응답시간(ms)
# Failed requests:        ← 0이어야 정상
# Transfer rate:          ← 처리량(KB/s)
```

### 5-2. wrk — 정밀 TPS 측정 (권장)

```bash
# 설치 (소스 빌드)
git clone https://github.com/wg/wrk.git /tmp/wrk
cd /tmp/wrk && make
cp wrk /usr/local/bin/

# POST용 Lua 스크립트 작성
cat > /tmp/wrk_post.lua << 'EOF'
wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.body   = '{"senderId":"stress","receiver":"01099999999","content":"wrk stress test","messageType":"SMS"}'
EOF

# 기본: 30초, 4스레드, 동시 50
wrk -t4 -c50 -d30s -s /tmp/wrk_post.lua $NGINX_URL/api/messages

# 고부하: 60초, 8스레드, 동시 200
wrk -t8 -c200 -d60s -s /tmp/wrk_post.lua $NGINX_URL/api/messages

# 레이턴시 분포 추가 (--latency)
wrk -t4 -c100 -d30s --latency -s /tmp/wrk_post.lua $NGINX_URL/api/messages

# wrk 출력 해석
# Running 30s test @ http://...
#   4 threads and 50 connections
#   Thread Stats   Avg      Stdev     Max   +/- Stdev
#     Latency    xx.xxms   xx.xx    xxx.xx    xx%
#     Req/Sec   xxxx.xx   xxx.xx   xxxx     xx%
#   Latency Distribution
#     50%    xx.xxms   ← P50 (중앙값)
#     75%    xx.xxms
#     90%    xx.xxms
#     99%    xx.xxms   ← P99 (이 값이 SLA 기준)
#   xxxxxx requests in 30s ← 총 요청 수
#   Requests/sec: xxxx.xx  ← TPS
#   Transfer/sec: xx.xxMB
```

### 5-3. hey — 설치 간편, 분포 그래프 제공

```bash
# 설치 (Linux amd64)
curl -Lo /usr/local/bin/hey \
  https://hey-release.s3.us-east-2.amazonaws.com/hey_linux_amd64
chmod +x /usr/local/bin/hey

# POST 스트레스 테스트 — 5,000건 / 동시 100
hey -n 5000 -c 100 -m POST \
    -H "Content-Type: application/json" \
    -d '{"senderId":"stress","receiver":"01099999999","content":"hey stress test","messageType":"SMS"}' \
    $NGINX_URL/api/messages

# 시간 기반 (30초간 동시 200)
hey -z 30s -c 200 -m POST \
    -H "Content-Type: application/json" \
    -d '{"senderId":"stress","receiver":"01099999999","content":"hey stress test","messageType":"SMS"}' \
    $NGINX_URL/api/messages

# GET status 엔드포인트 스트레스
hey -n 10000 -c 200 $NGINX_URL/api/messages/status

# hey 출력 예시 (분포 자동 출력)
# Summary:
#   Total:        xx.xxxx secs
#   Slowest:      x.xxxx secs
#   Fastest:      x.xxxx secs
#   Average:      x.xxxx secs
#   Requests/sec: xxxx.xx
# Response time histogram:
#   0.001 [x]    |
#   0.010 [xxx]  |■■■■■■
#   ...
# Status code distribution:
#   [200] xxxx responses
```

### 5-4. JMeter — GUI 기반 복합 시나리오

```bash
# JMeter 설치 (Java 11+ 필요)
wget https://downloads.apache.org/jmeter/binaries/apache-jmeter-5.6.3.tgz
tar -xvf apache-jmeter-5.6.3.tgz
mv apache-jmeter-5.6.3 /opt/jmeter

# CLI 모드로 실행 (아래 jmx 파일 사용)
/opt/jmeter/bin/jmeter -n \
  -t /tmp/messaging_test.jmx \
  -l /tmp/result.jtl \
  -e -o /tmp/jmeter_report
```

**JMeter 테스트 계획 파일 생성**

```bash
cat > /tmp/messaging_test.jmx << 'JMXEOF'
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Messaging Engine Test">
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments">
        <collectionProp name="Arguments.arguments"/>
      </elementProp>
    </TestPlan>
    <hashTree>
      <!-- 스레드 그룹: 100 유저, 30초 램프업, 60초 실행 -->
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Send Messages">
        <intProp name="ThreadGroup.num_threads">100</intProp>
        <intProp name="ThreadGroup.ramp_time">30</intProp>
        <boolProp name="ThreadGroup.scheduler">true</boolProp>
        <stringProp name="ThreadGroup.duration">60</stringProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
          <boolProp name="LoopController.continue_forever">true</boolProp>
        </elementProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="POST /api/messages">
          <stringProp name="HTTPSampler.domain">192.168.0.xxx</stringProp>
          <intProp name="HTTPSampler.port">80</intProp>
          <stringProp name="HTTPSampler.path">/api/messages</stringProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
            <collectionProp name="Arguments.arguments">
              <elementProp name="" elementType="HTTPArgument">
                <stringProp name="Argument.value">{"senderId":"jmeter","receiver":"01099999999","content":"jmeter test","messageType":"SMS"}</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
          <elementProp name="HTTPSampler.header" elementType="HeaderManager">
            <collectionProp name="HeaderManager.headers">
              <elementProp name="" elementType="Header">
                <stringProp name="Header.name">Content-Type</stringProp>
                <stringProp name="Header.value">application/json</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
        </HTTPSamplerProxy>
        <hashTree/>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
JMXEOF
```

---

## 6. 스트레스 테스트 중 실시간 모니터링

스트레스 테스트 실행과 동시에 **별도 터미널**에서 각 계층 지표를 관찰합니다.

### 터미널 1 — Redis 큐 크기 감시

```bash
watch -n 1 'echo "=== Redis Queue ===" && \
  redis-cli -c -h $REDIS_HOST LLEN message_queue'
```

### 터미널 2 — MySQL 상태별 건수 감시

```bash
watch -n 2 'mysql -h $MYSQL_HOST -u messaging_user -p1212 messaging_db \
  -e "SELECT status, COUNT(*) cnt FROM message_log GROUP BY status;"'
```

### 터미널 3 — Spring Actuator Prometheus 지표

```bash
watch -n 3 'curl -s $NGINX_URL/actuator/prometheus | \
  grep -E "http_server_requests_seconds_(count|sum)" | \
  grep -E "POST|DELETE|GET"'
```

### 터미널 4 — 서버 리소스 (top)

```bash
# Spring 서버에서 실행
top -bn1 | grep -E "java|nginx|mysql|redis"

# 또는 실시간
watch -n 2 'ps aux --sort=-%cpu | head -10'
```

### 터미널 5 — MySQL 슬로우 쿼리 실시간 확인

```bash
mysql -h $MYSQL_HOST -u messaging_user -p1212 messaging_db \
  -e "SHOW PROCESSLIST;" 

# 슬로우 쿼리 로그 tail (서버에서)
tail -f /var/log/mysql/slow.log
```

### 한 번에 보는 통합 모니터링 스크립트

```bash
cat > /tmp/monitor.sh << 'EOF'
#!/bin/bash
REDIS_HOST="${REDIS_HOST:-192.168.0.136}"
MYSQL_HOST="${MYSQL_HOST:-192.168.0.247}"
NGINX_URL="${NGINX_URL:-http://192.168.0.xxx}"

while true; do
  clear
  echo "=========================================="
  echo " Messaging Engine 실시간 모니터링"
  echo " $(date '+%Y-%m-%d %H:%M:%S')"
  echo "=========================================="

  echo ""
  echo "[Redis] 큐 크기:"
  redis-cli -c -h $REDIS_HOST LLEN message_queue 2>/dev/null || echo "연결 실패"

  echo ""
  echo "[MySQL] 상태별 건수:"
  mysql -h $MYSQL_HOST -u messaging_user -p1212 messaging_db 2>/dev/null \
    -e "SELECT status, COUNT(*) cnt FROM message_log GROUP BY status;" \
    || echo "연결 실패"

  echo ""
  echo "[Spring] 큐 상태 API:"
  curl -s --max-time 2 $NGINX_URL/api/messages/status 2>/dev/null | jq . \
    || echo "응답 없음"

  sleep 3
done
EOF
chmod +x /tmp/monitor.sh
bash /tmp/monitor.sh
```

---

## 7. 장애 주입 테스트 (Chaos)

서비스의 장애 복구 능력을 검증합니다. **반드시 개발/검증 환경에서만 실행하세요.**

### 7-1. Redis 노드 1개 장애 시뮬레이션

```bash
# Redis 클러스터 노드 중 1개 중단 (해당 서버에서)
systemctl stop redis

# 다른 터미널에서 API 계속 호출 — 서비스 유지 확인
for i in {1..10}; do
  curl -s -X POST $NGINX_URL/api/messages \
    -H "Content-Type: application/json" \
    -d '{"senderId":"chaos","receiver":"recv","content":"chaos test","messageType":"SMS"}' | jq .status
  sleep 1
done

# 노드 복구
systemctl start redis

# 클러스터 상태 재확인
redis-cli -c -h $REDIS_HOST cluster info | grep cluster_state
```

### 7-2. Spring 노드 1개 장애 (nginx 로드밸런싱 확인)

```bash
# 특정 Spring 노드 중단 (해당 서버에서)
systemctl stop messaging-engine

# nginx를 통해 계속 요청 — 다른 노드로 라우팅 확인
for i in {1..20}; do
  RES=$(curl -s $NGINX_URL/api/messages/status)
  echo "응답 서버: $(echo $RES | jq -r .serverInfo), 큐: $(echo $RES | jq -r .queueSize)"
  sleep 1
done

# Spring 노드 복구
systemctl start messaging-engine
```

### 7-3. MySQL 연결 지연 시뮬레이션

```bash
# tc (traffic control)로 MySQL 포트에 100ms 지연 주입 (Spring 서버에서)
tc qdisc add dev eth0 root netem delay 100ms

# API 응답시간 확인
curl -o /dev/null -s -w "응답시간: %{time_total}초\n" \
  -X POST $NGINX_URL/api/messages \
  -H "Content-Type: application/json" \
  -d '{"senderId":"chaos","receiver":"recv","content":"latency test","messageType":"SMS"}'

# 지연 제거
tc qdisc del dev eth0 root
```

### 7-4. 대용량 payload 테스트

```bash
# content 필드에 긴 문자열 전송 (LMS 장문 시나리오)
LONG_CONTENT=$(python3 -c "print('X' * 4000)")
curl -s -X POST $NGINX_URL/api/messages \
  -H "Content-Type: application/json" \
  -d "{\"senderId\":\"user1\",\"receiver\":\"recv\",\"content\":\"$LONG_CONTENT\",\"messageType\":\"LMS\"}" | jq .

# content 없음 (빈 문자열) — 400 응답 확인
curl -s -X POST $NGINX_URL/api/messages \
  -H "Content-Type: application/json" \
  -d '{"senderId":"user1","receiver":"recv","content":"","messageType":"SMS"}' | jq .
```

---

## 8. 결과 판단 기준

### API 응답 기준

| 항목 | 정상 기준 | 확인 방법 |
|------|----------|----------|
| `POST /api/messages` | HTTP 200, 응답시간 < 200ms | curl -w "%{time_total}" |
| `GET /api/messages/status` | HTTP 200, 응답시간 < 50ms | curl -w "%{time_total}" |
| `DELETE /api/messages/consume` | HTTP 200, 응답시간 < 200ms | curl -w "%{time_total}" |
| content 누락 요청 | HTTP 400 | curl 응답 코드 확인 |
| 큐 빈 상태 consume | HTTP 200 + `"status":"empty"` | jq .status |

### 스트레스 테스트 기준

| 지표 | 목표치 | 경고 기준 |
|------|-------|----------|
| TPS (POST) | > 500 req/s | < 100 req/s |
| P99 응답시간 | < 500ms | > 1,000ms |
| 에러율 | 0% | > 1% |
| Redis queueSize 증가 | 전송 수와 일치 | 불일치 시 유실 의심 |
| MySQL PENDING 건수 | 전송 건수와 일치 | 불일치 시 트랜잭션 오류 |

### MySQL 데이터 정합성 체크 쿼리

```sql
-- 전송 대비 DB 저장 비율 확인
SELECT
  COUNT(*) AS total,
  SUM(CASE WHEN status = 'PENDING'  THEN 1 ELSE 0 END) AS pending,
  SUM(CASE WHEN status = 'SUCCESS'  THEN 1 ELSE 0 END) AS success,
  SUM(CASE WHEN status = 'FAILED'   THEN 1 ELSE 0 END) AS failed,
  MAX(created_at) AS last_created,
  MIN(created_at) AS first_created
FROM message_log;

-- 최근 1분 처리량 (TPS 역산)
SELECT
  COUNT(*) / 60 AS approx_tps
FROM message_log
WHERE created_at >= NOW() - INTERVAL 1 MINUTE;

-- Redis와 MySQL 정합성 비교 (큐에 있는 ID가 DB에도 존재해야 함)
-- Redis 키 목록을 별도로 추출 후 비교 필요
SELECT message_id, status, created_at
FROM message_log
WHERE status = 'PENDING'
ORDER BY created_at ASC
LIMIT 20;
```

### 스트레스 테스트 후 정리

```bash
# Redis 큐 완전 소비
while [ "$(redis-cli -c -h $REDIS_HOST LLEN message_queue)" != "0" ]; do
  curl -s -X DELETE $NGINX_URL/api/messages/consume > /dev/null
done
echo "큐 정리 완료"

# 최종 MySQL 상태 확인
mysql -h $MYSQL_HOST -u messaging_user -p1212 messaging_db \
  -e "SELECT status, COUNT(*) FROM message_log GROUP BY status;"
```

---

## 참고 — 응답 코드 빠른 참조

| HTTP 코드 | 의미 | 발생 케이스 |
|-----------|------|------------|
| 200 | 성공 | 정상 처리 |
| 400 | 잘못된 요청 | content 누락/빈 값 |
| 404 | 엔드포인트 없음 | URL 오타, nginx 설정 오류 |
| 502 | Bad Gateway | nginx → Spring 연결 실패 |
| 503 | Service Unavailable | Spring 다운, 헬스체크 실패 |
| 504 | Gateway Timeout | Spring 응답 지연 (MySQL/Redis 병목) |
