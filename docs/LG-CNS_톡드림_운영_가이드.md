# LG-CNS 톡드림 운영 가이드

---

## 목차

1. [경력 상세 면접 가이드](#1-경력-상세-면접-가이드)
2. [업무 내용 상세 기술 가이드](#2-업무-내용-상세-기술-가이드)
3. [일상 업무 루틴](#3-일상-업무-루틴)

---

# 1. 경력 상세 면접 가이드

## 1-1. 톡드림 기업 메시징 플랫폼 전담 운영

### 서비스 개요: LG그룹 계열사 및 외부 기업 대상 B2B 메시징 솔루션

**예상 질문**: "톡드림이 정확히 어떤 서비스인가요?"

**답변**: "톡드림은 LG-CNS에서 개발한 기업용 메시징 솔루션입니다. LG화학, LG전자 등 LG그룹 계열사들이 고객에게 SMS, 카카오톡 비즈니스메시지, 이메일 등을 발송할 때 사용하는 플랫폼이에요. 예를 들어 LG유플러스에서 요금 안내 문자를 보내거나, LG화학에서 고객사에 납기일정을 알릴 때 톡드림을 통해 메시지가 발송됩니다. 외부 기업들도 이 서비스를 구매해서 사용하고 있습니다."

**예상 질문**: "B2B 메시징이 일반 메시징과 어떻게 다른가요?"

**답변**: "개인이 카톡 보내는 것과 달리 기업에서는 한 번에 수천, 수만 건의 메시지를 발송해야 합니다. 또한 발송 결과 추적, 실패 재처리, 스케줄링 발송 등의 기능이 필요하고, 무엇보다 메시지가 확실히 전송되어야 하는 신뢰성이 중요합니다. 그래서 일반 메시징보다 훨씬 복잡한 시스템이 필요합니다."

---

### 운영 규모: 일 평균 100만건 메시지 처리, 피크 시간 초당 500메시지 처리

**예상 질문**: "일 100만건이라는 게 실제로 어느 정도 규모인가요?"

**답변**: "하루 24시간으로 나누면 평균 초당 12건 정도지만, 실제로는 업무시간에 집중됩니다. 특히 오전 9-11시, 오후 2-5시에 트래픽이 몰리는데, 이때는 초당 300-500건까지 처리해야 합니다. 월말이나 프로모션 기간에는 평소의 2-3배까지 증가하기도 해서, 시스템이 이런 버스트 트래픽을 안정적으로 처리할 수 있도록 모니터링하는 것이 중요했습니다."

**예상 질문**: "피크 시간을 어떻게 대응했나요?"

**답변**: "실시간 모니터링으로 큐에 쌓이는 메시지 수를 지켜보고, 처리 지연이 발생하면 메시징 서버 인스턴스를 추가로 기동했습니다. 또한 고객사에게 대량 발송 시에는 사전에 알려달라고 요청해서 미리 준비할 수 있도록 했습니다."

---

### 담당 인프라: 리눅스 서버 30대, 메시징 서버 클러스터 운영

**예상 질문**: "30대 서버를 혼자 관리했나요?"

**답변**: "팀으로 운영했지만, 그 중에서 톡드림 플랫폼 전담으로 제가 주로 관리했습니다. 메시징 서버 10대, 웹서버 5대, DB서버 3대, 모니터링 서버 등으로 구성되어 있었고, 각 서버의 역할과 상태를 매일 점검하는 것이 주 업무였습니다."

**예상 질문**: "서버들이 어떻게 구성되어 있었나요?"

**답변**: "메시징 처리를 담당하는 메시징 서버가 클러스터로 구성되어 있어서, 여러 대가 동시에 큐에서 메시지를 가져와서 처리하는 방식이었습니다. 앞단에는 API 서버가 있어서 고객사의 발송 요청을 받고, 뒤에는 실제 통신사나 카카오 API로 메시지를 전송하는 구조였습니다."

---

## 1-2. 톡드림 메시징 플랫폼 안정성 관리

### 대규모 메시징 트래픽 처리: 일 평균 100만건 메시지 안정적 처리 및 전송률 99% 이상 유지

**예상 질문**: "전송률 99%는 어떻게 측정했나요?"

**답변**: "톡드림 시스템에서 발송 시도한 메시지 중에서 실제로 수신자에게 전달된 비율을 매일 집계했습니다. 통신사나 카카오에서 결과 코드를 받아서 성공/실패를 판단하는데, 실패 원인은 주로 잘못된 번호, 수신거부, 통신사 일시 장애 등이었습니다. 시스템 자체 오류로 인한 실패를 최소화하는 것이 목표였습니다."

**예상 질문**: "전송률이 떨어졌을 때는 어떻게 대응했나요?"

**답변**: "먼저 실패 원인을 분석해서 시스템 오류인지 외부 요인인지 구분했습니다. 시스템 문제라면 로그를 확인해서 빠르게 해결하고, 통신사 장애 등 외부 문제면 재전송 스케줄을 조정해서 나중에 다시 시도하도록 했습니다."

---

### 플랫폼 가용성 확보: 24시간 연중무휴 메시징 서비스 제공을 위한 실시간 모니터링

**예상 질문**: "24시간 모니터링은 어떻게 했나요?"

**답변**: "G-EMS 모니터링 시스템에서 서버 상태, 메시지 처리량, 큐 적체 상황 등을 실시간으로 확인했습니다. 임계치를 넘으면 자동으로 SMS나 이메일 알람이 와서, 새벽이나 주말에도 문제가 생기면 즉시 알 수 있었습니다. 긴급한 경우에는 원격으로 접속해서 1차 조치를 했습니다."

**예상 질문**: "야간이나 주말에도 대응했나요?"

**답변**: "네, 기업 메시징은 새벽에도 자동 발송되는 경우가 많아서 24시간 대응이 필요했습니다. 온콜 체계로 돌아가면서 담당했고, 장애 알람이 오면 30분 내에 확인하고 초기 대응하는 것이 원칙이었습니다."

---

### 장애 대응 및 복구: 메시징 큐 장애, 네트워크 이슈 등 발생 시 30분 내 1차 대응 수행

**예상 질문**: "주로 어떤 장애가 발생했나요?"

**답변**: "가장 빈번했던 건 메시징 큐에 메시지가 쌓여서 처리가 지연되는 상황이었습니다. 대량 발송이나 외부 API 응답 지연이 원인인 경우가 많았어요. 그 다음으로는 특정 메시징 서버의 프로세스가 다운되거나, 네트워크 연결 문제, 가끔 DB 커넥션 풀 부족 등이 있었습니다."

**예상 질문**: "30분 내 대응이라는 게 구체적으로 뭔가요?"

**답변**: "알람을 받으면 먼저 시스템에 접속해서 현재 상황을 파악하고, 고객에게 영향을 주는 장애인지 판단합니다. 간단한 재시작으로 해결되는 문제면 즉시 조치하고, 복잡한 문제면 임시 방안을 먼저 적용해서 서비스 중단을 최소화한 후에 근본 원인을 찾아 해결했습니다."

---

### 재발 방지 대책: 장애 원인 분석 보고서 작성 및 운영 절차 개선으로 시스템 안정성 향상

**예상 질문**: "장애 분석 보고서는 어떤 내용으로 작성했나요?"

**답변**: "장애 발생 시간, 영향 범위, 원인 분석, 조치 과정, 재발 방지 대책으로 구성했습니다. 예를 들어 메시징 서버 다운 장애가 있었다면, 로그 분석을 통해 메모리 부족이 원인이었는지, JVM 옵션 조정이 필요한지 등을 파악하고, 향후 같은 문제를 방지하기 위한 모니터링 강화나 설정 변경 사항을 포함했습니다."

**예상 질문**: "실제로 개선한 사례가 있나요?"

**답변**: "메시징 큐 적체가 자주 발생해서, 큐 크기 모니터링을 강화하고 자동으로 처리 프로세스를 추가 기동하는 스크립트를 만들었습니다. 또한 대량 발송 시 미리 알림을 받는 절차를 만들어서 사전에 준비할 수 있도록 개선했습니다."

---

## 1-3. 운영 자동화 및 모니터링 시스템 관리

### Bash 스크립트 기반 운영 자동화

**예상 질문**: "어떤 스크립트를 만들었나요?"

**답변**: "대표적으로 메시지 처리량 점검 스크립트를 만들었습니다. 매시간 큐에 쌓인 메시지 수, 처리된 메시지 수, 실패한 메시지 수를 집계해서 일정 임계치를 넘으면 알람을 보내는 스크립트였어요. 또한 로그 파일에서 ERROR 라인을 추출해서 일일 리포트를 만드는 스크립트도 있었습니다."

```bash
#!/bin/bash
# 메시지 큐 상태 점검
QUEUE_SIZE=$(메시지 큐 조회 명령어)
if [ $QUEUE_SIZE -gt 10000 ]; then
    echo "큐 적체 경고: $QUEUE_SIZE" | mail -s "알람" admin@example.com
fi
```

**예상 질문**: "스크립트 작성할 때 어려웠던 점은?"

**답변**: "처음에는 간단한 스크립트부터 시작했는데, 예외 상황 처리가 어려웠습니다. 예를 들어 서버 접속이 안 되거나, 로그 파일이 없을 때 스크립트가 에러를 내는 경우가 있어서, 에러 핸들링과 로그 기록을 추가하면서 점점 안정화시켰습니다."

---

### 모니터링 시스템 운영: LG-CNS 표준 도구(G-EMS, Scouter) 활용한 실시간 성능 지표 추적

**예상 질문**: "G-EMS와 Scouter가 각각 어떤 역할인가요?"

**답변**: "G-EMS는 LG-CNS의 통합 모니터링 시스템으로 서버 리소스, 네트워크, 애플리케이션 상태를 종합적으로 관리하는 도구입니다. Scouter는 Java 애플리케이션 성능 모니터링(APM) 도구로, 메시징 서버의 응답시간, 메모리 사용량, GC 상태 등을 실시간으로 추적할 수 있었습니다."

**예상 질문**: "모니터링에서 주로 어떤 지표를 봤나요?"

**답변**: "메시지 처리량(TPS), 큐 적체량, 서버 CPU/메모리 사용률, 응답시간을 주로 봤습니다. 특히 메시지 처리 지연시간이 늘어나거나, 큐에 메시지가 계속 쌓이는 패턴을 파악해서 문제를 조기에 발견하는 것이 중요했습니다."

**예상 질문**: "이런 지표들을 어떻게 활용했나요?"

**답변**: "메시지 처리량은 시간대별 패턴을 파악해서 피크 시간을 예측하는 데 사용했고, 지연시간이 늘어나면 서버 증설이나 설정 조정이 필요한 신호로 봤습니다. 성공률은 매일 리포트로 만들어서 서비스 품질 지표로 관리했고, 서버 리소스는 용량 계획 수립에 활용했습니다."

**예상 질문**: "알림은 어떤 방식으로 받았나요?"

**답변**: "G-EMS에서 설정한 임계치를 넘으면 SMS와 이메일로 알림이 왔습니다. 심각도에 따라 Warning, Critical로 구분했고, Critical은 즉시 대응, Warning은 업무시간 내 확인하는 식으로 운영했습니다. 또한 자주 발생하는 알람은 임계치를 조정해서 불필요한 알림을 줄였습니다."

---

## 1-4. 리눅스 인프라 구축 및 운영

### 메시징 서버 환경 구축: RHEL, CentOS 기반 서버 설치 및 구성

**예상 질문**: "서버 설치 과정은 어떻게 됐나요?"

**답변**: "표준 이미지를 기반으로 OS를 설치한 후, 톡드림 애플리케이션에 필요한 Java, 메시징 큐 소프트웨어 등을 설치했습니다. LG-CNS 표준 설정에 맞춰 보안 설정, 사용자 계정, 방화벽 등을 구성하고, 메시징 서버 클러스터에 조인하는 작업을 했습니다."

**예상 질문**: "LVM을 왜 사용했나요?"

**답변**: "메시징 로그나 임시 파일이 급격히 증가할 수 있어서, 나중에 디스크 공간을 유연하게 확장할 수 있도록 LVM을 사용했습니다. 실제로 대량 발송 기간에 로그가 많이 쌓여서 논리 볼륨을 확장한 경험이 있습니다."

**예상 질문**: "네트워크 설정에서 특별한 점이 있었나요?"

**답변**: "메시징 서버들이 클러스터로 통신해야 해서 내부 통신용 네트워크와 외부 API 통신용 네트워크를 분리해서 설정했습니다. 또한 고가용성을 위해 네트워크 본딩도 구성했습니다."

**예상 질문**: "DB에는 주로 어떤 데이터가 저장됐나요?"

**답변**: "메시지 발송 이력, 고객사 정보, 발송 통계, 실패 메시지 재처리 큐 등이 저장됐습니다. 특히 메시지 이력은 데이터가 빠르게 증가해서 파티셔닝으로 관리했고, 오래된 데이터는 아카이빙하는 작업도 했습니다."

**예상 질문**: "백업은 어떤 방식으로 했나요?"

**답변**: "매일 새벽에 mysqldump를 이용한 논리 백업과 바이너리 로그 백업을 자동으로 수행했습니다. 월 1회는 다른 서버에서 백업 파일로 복구 테스트를 해서 백업이 정상적으로 되고 있는지 확인했습니다."

**예상 질문**: "망분리는 왜 필요했나요?"

**답변**: "운영 환경과 개발 환경을 분리해서 개발 작업이 운영 서비스에 영향을 주지 않도록 했습니다. 또한 보안상 운영망은 접근 권한을 엄격하게 관리하고, 개발망은 좀 더 자유롭게 접근할 수 있도록 구분했습니다."

---

## 💡 면접 팁

### 강조할 포인트

1. **메시징 특화 경험** - "일반 웹서비스와 다른 메시징 서비스의 특성을 이해"
2. **대용량 처리 경험** - "일 100만건이라는 상당한 규모의 트래픽 처리"
3. **24시간 운영 경험** - "기업 서비스의 높은 가용성 요구사항 이해"
4. **자동화 능력** - "반복 업무를 스크립트로 자동화하는 능력"

### 겸손한 태도

- "1년 7개월이라 아직 배우는 단계였습니다"
- "선배님들께 많이 배웠습니다"
- "처음에는 어려웠지만 점점 익숙해졌습니다"

### 성장 의지

- "메시징 서비스의 특수성을 배울 수 있어서 좋았습니다"
- "더 큰 규모의 시스템을 경험해보고 싶습니다"
- "새로운 기술 스택에도 도전해보고 싶습니다"

---

# 2. 업무 내용 상세 기술 가이드

## 2-1. 톡드림 기업 메시징 플랫폼 전담 운영

### 플랫폼 아키텍처

```
[고객사 시스템] → [API Gateway] → [메시징 큐] → [메시징 엔진] → [통신사/카카오 API]
                       ↓               ↓              ↓
                  [웹 관리자]      [DB 저장]       [결과 수집]
```

### 지원 메시징 채널

- **SMS/LMS**: 일반 문자메시지, 장문메시지
- **카카오톡 비즈니스**: 알림톡, 친구톡
- **RCS**: 차세대 메시지 (이미지, 동영상 첨부)
- **이메일**: 대량 이메일 발송
- **Push 알림**: 모바일 앱 푸시

### 실제 업무 내용

- **플랫폼 상태 점검**: 매일 오전 메시징 서버 클러스터 전체 상태 확인
- **발송량 모니터링**: 시간별/일별 메시지 처리량 추이 분석
- **고객사 대응**: 발송 실패 문의, 대량 발송 사전 협의
- **용량 관리**: 메시지 큐 적체 상황 모니터링 및 처리 능력 조정

---

### 운영 규모 및 트래픽 패턴

```
시간대별 메시지 처리량 (평일 기준)
06:00-09:00: 50,000건  (출근 시간 알림)
09:00-12:00: 300,000건 (업무 시간 집중)
12:00-14:00: 100,000건 (점심 시간 감소)
14:00-18:00: 400,000건 (오후 업무 피크)
18:00-22:00: 150,000건 (퇴근 후 알림)
```

### 처리 능력 관리

- **스케일 아웃**: 피크 시간 메시징 서버 인스턴스 추가 기동
- **큐 관리**: Redis/RabbitMQ 기반 메시지 큐 상태 모니터링
- **병목 지점 식별**: API 응답시간, DB 쿼리 성능, 외부 API 지연시간 추적
- **부하 분산**: 라운드로빈, 가중치 기반 메시지 배분

---

### 서버 구성 (30대)

```
메시징 서버 클러스터 (10대)
├── 메시징 엔진: Java Spring Boot 애플리케이션
├── 메시지 큐: Redis Cluster (3대)
├── 로드밸런서: Nginx (2대)
└── 관리자 웹: React + Node.js (2대)

데이터베이스 (3대)
├── 메인 DB: MySQL Master-Slave (2대)
└── 통계 DB: MariaDB (1대)

운영 서버 (15대)
├── 모니터링: G-EMS, Scouter 서버
├── 로그 수집: ELK Stack
├── 파일 서버: 이미지, 첨부파일 저장
└── 백업 서버: 일일 백업 전용
```

### 클러스터 운영 업무

- **서버 상태 점검**: 매일 30대 서버 CPU, 메모리, 디스크 사용률 확인
- **애플리케이션 상태**: Java 프로세스, 메모리 힙 사용량, GC 빈도 모니터링
- **클러스터 동기화**: 서버 간 설정 파일 동기화, 버전 일치성 확인
- **장애 서버 격리**: 문제 서버 로드밸런서에서 제외, 복구 후 재투입

---

## 2-2. 톡드림 메시징 플랫폼 안정성 관리

### 전송률 관리 체계

```sql
-- 일일 전송률 집계 쿼리 예시
SELECT
    DATE(created_at) as send_date,
    COUNT(*) as total_messages,
    SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as success_count,
    (SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) / COUNT(*)) * 100 as success_rate
FROM message_log
WHERE created_at >= CURDATE() - INTERVAL 1 DAY
GROUP BY DATE(created_at);
```

### 실패 원인 분석

- **시스템 오류**: 서버 다운, DB 연결 실패, 메모리 부족 (1%)
- **외부 API 오류**: 통신사/카카오 일시 장애 (0.5%)
- **발송 규칙 위반**: 스팸 차단, 수신거부 (0.3%)
- **데이터 오류**: 잘못된 번호, 형식 오류 (0.2%)

### 실시간 모니터링 스크립트

```bash
#!/bin/bash
CURRENT_TPS=$(cat /var/log/messaging/current_tps.log | tail -1)
QUEUE_SIZE=$(redis-cli llen message_queue)
ERROR_RATE=$(grep ERROR /var/log/messaging/error.log | wc -l)

if [ $CURRENT_TPS -lt 100 ]; then
    echo "WARNING: Low TPS detected: $CURRENT_TPS"
fi
```

---

### 가용성 목표

- **서비스 가용성**: 99.9% (월 다운타임 43분 이하)
- **API 응답시간**: 95% 요청 1초 이내 응답
- **메시지 처리 지연**: 큐 대기시간 5분 이내

### G-EMS 모니터링 대시보드

```
┌─────────────┬─────────────┬─────────────┐
│ 서버 상태   │ 메시지 처리 │ 큐 상태     │
├─────────────┼─────────────┼─────────────┤
│ CPU: 65%    │ TPS: 245    │ Queue: 1,234│
│ Memory: 78% │ Success: 99%│ Delay: 2min │
│ Disk: 45%   │ Error: 12   │ Worker: 8   │
└─────────────┴─────────────┴─────────────┘

알람 설정:
- CPU > 80%: Warning
- Memory > 85%: Critical
- Queue > 10,000: Critical
- Success Rate < 95%: Critical
```

---

### 장애 유형별 대응

#### 1. 메시징 큐 적체

```bash
# 큐 상태 확인
redis-cli llen message_queue
redis-cli monitor

# 응급 조치
# 1) 워커 프로세스 추가 기동
systemctl start messaging-worker@{2,3,4}

# 2) 대량 발송 일시 중단
redis-cli del bulk_sending_queue

# 3) 우선순위 큐 생성
redis-cli lpush priority_queue "긴급메시지"
```

#### 2. 메시징 서버 다운

```bash
# 서버 상태 확인
systemctl status messaging-service
journalctl -u messaging-service -f

# 응급 조치
# 1) 로드밸런서에서 해당 서버 제외
curl -X POST "http://lb-admin/disable-server/msg-server-03"

# 2) 서비스 재시작 시도
systemctl restart messaging-service

# 3) 복구 후 로드밸런서에 재투입
curl -X POST "http://lb-admin/enable-server/msg-server-03"
```

#### 3. 데이터베이스 연결 실패

```bash
# DB 커넥션 풀 상태 확인
mysql -e "SHOW PROCESSLIST;"
mysql -e "SHOW VARIABLES LIKE 'max_connections';"

# 응급 조치
# 1) 커넥션 풀 초기화
systemctl restart messaging-service

# 2) Read-only 모드로 전환
mysql -e "SET GLOBAL read_only = ON;"

# 3) 슬레이브 DB로 트래픽 우회
```

---

### 장애 분석 보고서 템플릿

```
장애 보고서 #MSG-2024-001

■ 장애 개요
- 발생일시: 2024-07-11 14:23 ~ 14:45 (22분)
- 영향 범위: 메시징 서버 클러스터 전체
- 장애 증상: 메시지 발송 지연, API 응답시간 증가

■ 원인 분석
- 직접 원인: 메시징 서버 #3 메모리 부족으로 인한 GC 지연
- 근본 원인: JVM 힙 메모리 설정 부족 (2GB → 4GB 필요)
- 트리거: 대량 발송 요청으로 인한 메모리 사용량 급증

■ 대응 과정
14:23 - G-EMS 알람 수신 (Memory > 90%)
14:25 - 서버 접속 및 상황 파악
14:28 - 해당 서버 로드밸런서에서 제외
14:30 - JVM 재시작 및 임시 복구
14:45 - 정상 서비스 확인 후 로드밸런서 재투입

■ 재발 방지 대책
1. JVM 힙 메모리 2GB → 4GB 증설
2. GC 로그 모니터링 강화
3. 메모리 사용률 알람 임계치 조정 (90% → 80%)
4. 대량 발송 시 사전 알림 프로세스 수립
```

---

## 2-3. 운영 자동화 및 모니터링 시스템 관리

### 메시지 처리 현황 점검 스크립트

```bash
#!/bin/bash
# messaging_health_check.sh

LOG_FILE="/var/log/messaging/health_check.log"
DATE=$(date '+%Y-%m-%d %H:%M:%S')

QUEUE_SIZE=$(redis-cli llen message_queue)
PROCESSING_RATE=$(tail -100 /var/log/messaging/processing.log | grep "SUCCESS" | wc -l)
ERROR_COUNT=$(tail -100 /var/log/messaging/error.log | wc -l)

CPU_USAGE=$(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | awk -F'%' '{print $1}')
MEMORY_USAGE=$(free | grep Mem | awk '{printf("%.1f", $3/$2 * 100.0)}')
DISK_USAGE=$(df -h / | awk 'NR==2 {print $5}' | sed 's/%//')

echo "[$DATE] Queue:$QUEUE_SIZE, Processing:$PROCESSING_RATE, Error:$ERROR_COUNT, CPU:$CPU_USAGE%, Memory:$MEMORY_USAGE%, Disk:$DISK_USAGE%" >> $LOG_FILE

if [ $QUEUE_SIZE -gt 10000 ]; then
    echo "ALERT: Queue size exceed threshold: $QUEUE_SIZE" | mail -s "Messaging Alert" admin@lgcns.com
fi

if [ ${CPU_USAGE%.*} -gt 80 ]; then
    echo "ALERT: High CPU usage: $CPU_USAGE%" | mail -s "Server Alert" admin@lgcns.com
fi
```

### 로그 분석 자동화 스크립트

```bash
#!/bin/bash
# daily_log_analysis.sh

YESTERDAY=$(date -d "yesterday" '+%Y-%m-%d')
REPORT_FILE="/var/reports/daily_report_$YESTERDAY.txt"

echo "=== 톡드림 일일 운영 보고서 - $YESTERDAY ===" > $REPORT_FILE

TOTAL_MESSAGES=$(grep "$YESTERDAY" /var/log/messaging/processing.log | wc -l)
SUCCESS_MESSAGES=$(grep "$YESTERDAY.*SUCCESS" /var/log/messaging/processing.log | wc -l)
FAILED_MESSAGES=$(grep "$YESTERDAY.*FAILED" /var/log/messaging/processing.log | wc -l)
SUCCESS_RATE=$(echo "scale=2; $SUCCESS_MESSAGES * 100 / $TOTAL_MESSAGES" | bc)

echo "■ 메시지 처리 현황" >> $REPORT_FILE
echo "- 총 처리: $TOTAL_MESSAGES 건" >> $REPORT_FILE
echo "- 성공: $SUCCESS_MESSAGES 건" >> $REPORT_FILE
echo "- 실패: $FAILED_MESSAGES 건" >> $REPORT_FILE
echo "- 성공률: $SUCCESS_RATE%" >> $REPORT_FILE

echo "■ 주요 에러 현황" >> $REPORT_FILE
grep "$YESTERDAY.*ERROR" /var/log/messaging/error.log | cut -d' ' -f4- | sort | uniq -c | sort -nr | head -10 >> $REPORT_FILE

mail -s "톡드림 일일 운영 보고서 - $YESTERDAY" team@lgcns.com < $REPORT_FILE
```

---

### G-EMS 모니터링 구성 요소

```
G-EMS 에이전트 설치 및 설정:

1. 시스템 리소스 모니터링
   - CPU, Memory, Disk I/O, Network I/O
   - 프로세스별 리소스 사용량
   - 파일시스템 사용률

2. 애플리케이션 모니터링
   - Java 프로세스 상태
   - 포트 연결성 체크
   - 로그 파일 크기 증가율

3. 네트워크 모니터링
   - 서버 간 연결성
   - 외부 API 응답시간
   - 방화벽 정책 체크
```

### Scouter APM 설정

```java
// Scouter 에이전트 설정 (scouter.conf)
net_collector_ip=192.168.1.100
net_collector_udp_port=6100
net_collector_tcp_port=6100

obj_name=messaging-server-01
java_name=messaging-service

profile_spring_controller_enabled=true
profile_sql_enabled=true
profile_method_enabled=true
hook_method_patterns=com.lgcns.messaging.*
```

### 핵심 KPI - 실시간 TPS 계산 쿼리

```sql
SELECT
    DATE_FORMAT(created_at, '%H:%i') as time_slot,
    COUNT(*) / 60 as tps
FROM message_log
WHERE created_at >= NOW() - INTERVAL 1 HOUR
GROUP BY DATE_FORMAT(created_at, '%H:%i')
ORDER BY time_slot DESC
LIMIT 10;
```

---

## 2-4. 리눅스 인프라 구축 및 운영

### OS 기본 설정

```bash
# 시스템 업데이트
yum update -y

# 필수 패키지 설치
yum install -y wget curl vim net-tools htop

# 시간 동기화 설정
timedatectl set-timezone Asia/Seoul
systemctl enable chronyd
systemctl start chronyd

# 방화벽 기본 설정
firewall-cmd --permanent --add-port=8080/tcp
firewall-cmd --permanent --add-port=6379/tcp
firewall-cmd --reload
```

### Java 환경 구성

```bash
# OpenJDK 설치
yum install -y java-11-openjdk java-11-openjdk-devel

# JAVA_HOME 설정
echo 'export JAVA_HOME=/usr/lib/jvm/java-11-openjdk' >> /etc/profile
echo 'export PATH=$PATH:$JAVA_HOME/bin' >> /etc/profile
source /etc/profile

# JVM 옵션 설정
Environment="JAVA_OPTS=-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### 메시징 애플리케이션 서비스 등록

```bash
cat > /etc/systemd/system/messaging.service << EOF
[Unit]
Description=Messaging Service
After=network.target

[Service]
Type=forking
User=messaging
Group=messaging
ExecStart=/opt/messaging/bin/start.sh
ExecStop=/opt/messaging/bin/stop.sh
Restart=always

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable messaging
```

---

### LVM 구성

```bash
# 물리 볼륨 생성
pvcreate /dev/sdb /dev/sdc

# 볼륨 그룹 생성
vgcreate messaging_vg /dev/sdb /dev/sdc

# 논리 볼륨 생성
lvcreate -L 50G -n logs_lv messaging_vg
lvcreate -L 100G -n data_lv messaging_vg
lvcreate -L 20G -n backup_lv messaging_vg

# 파일시스템 생성 및 마운트
mkfs.xfs /dev/messaging_vg/logs_lv
mkfs.xfs /dev/messaging_vg/data_lv
mkfs.xfs /dev/messaging_vg/backup_lv

# /etc/fstab 설정
echo '/dev/messaging_vg/logs_lv /var/log/messaging xfs defaults 0 0' >> /etc/fstab
echo '/dev/messaging_vg/data_lv /opt/messaging/data xfs defaults 0 0' >> /etc/fstab
mount -a
```

### 네트워크 본딩 설정

```bash
modprobe bonding

cat > /etc/sysconfig/network-scripts/ifcfg-bond0 << EOF
DEVICE=bond0
TYPE=Bond
BONDING_MASTER=yes
BOOTPROTO=static
IPADDR=192.168.1.100
NETMASK=255.255.255.0
GATEWAY=192.168.1.1
BONDING_OPTS="mode=active-backup miimon=100"
EOF

systemctl restart network
```

---

### 메시징 DB 스키마

```sql
-- 메시지 발송 이력 테이블 (파티셔닝)
CREATE TABLE message_log (
    id BIGINT AUTO_INCREMENT,
    message_id VARCHAR(50) NOT NULL,
    sender_id VARCHAR(50),
    receiver VARCHAR(100),
    message_type ENUM('SMS', 'LMS', 'KAKAO', 'EMAIL'),
    content TEXT,
    status ENUM('PENDING', 'SUCCESS', 'FAILED'),
    sent_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, created_at),
    INDEX idx_sender_date (sender_id, created_at),
    INDEX idx_status_date (status, created_at)
) PARTITION BY RANGE (TO_DAYS(created_at)) (
    PARTITION p202407 VALUES LESS THAN (TO_DAYS('2024-08-01')),
    PARTITION p202408 VALUES LESS THAN (TO_DAYS('2024-09-01')),
    PARTITION p202409 VALUES LESS THAN (TO_DAYS('2024-10-01'))
);
```

### 일일 백업 스크립트

```bash
#!/bin/bash
# daily_backup.sh

BACKUP_DIR="/backup/mysql"
DATE=$(date +%Y%m%d)
MYSQL_USER="backup_user"
MYSQL_PASS="backup_password"

mkdir -p $BACKUP_DIR/$DATE

mysqldump -u$MYSQL_USER -p$MYSQL_PASS \
    --single-transaction \
    --routines \
    --triggers \
    --all-databases > $BACKUP_DIR/$DATE/full_backup_$DATE.sql

mysql -u$MYSQL_USER -p$MYSQL_PASS -e "FLUSH LOGS;"
cp /var/lib/mysql/mysql-bin.* $BACKUP_DIR/$DATE/

cd $BACKUP_DIR/$DATE
tar czf full_backup_$DATE.tar.gz *.sql mysql-bin.*
rm *.sql mysql-bin.*

find $BACKUP_DIR -type d -mtime +7 -exec rm -rf {} \;

if [ $? -eq 0 ]; then
    echo "Backup completed: $DATE" | mail -s "Backup Success" admin@lgcns.com
else
    echo "Backup failed: $DATE" | mail -s "Backup Failed" admin@lgcns.com
fi
```

---

### 네트워크 아키텍처

```
Internet
    ↓
[방화벽] - DMZ 네트워크 (192.168.10.0/24)
    ↓         ↳ 웹서버, API Gateway
[L3 스위치] - 서비스 네트워크 (192.168.20.0/24)
    ↓         ↳ 메시징 서버 클러스터
[L2 스위치] - 데이터 네트워크 (192.168.30.0/24)
              ↳ 데이터베이스, 백업 서버
```

### VLAN 망분리 설정

```bash
modprobe 8021q

# 운영망 VLAN 100
cat > /etc/sysconfig/network-scripts/ifcfg-eth0.100 << EOF
DEVICE=eth0.100
BOOTPROTO=static
VLAN=yes
IPADDR=10.100.1.10
NETMASK=255.255.255.0
GATEWAY=10.100.1.1
EOF

# 개발망 VLAN 200
cat > /etc/sysconfig/network-scripts/ifcfg-eth0.200 << EOF
DEVICE=eth0.200
BOOTPROTO=static
VLAN=yes
IPADDR=10.200.1.10
NETMASK=255.255.255.0
EOF

# 운영망에서 개발망으로의 접근 차단
firewall-cmd --zone=production --add-rich-rule='rule family="ipv4" destination address="10.200.0.0/16" drop' --permanent
firewall-cmd --reload
```

---

# 3. 일상 업무 루틴

## 3-1. 오전 업무 (09:00-12:00)

| 시간 | 업무 내용 |
|------|-----------|
| 09:00-09:30 | **시스템 상태 점검** - G-EMS/Scouter 대시보드 확인, 전날 메시지 발송량 및 성공률 확인 (목표: 99% 이상), 서버 30대 리소스 점검, 메시징 큐 적체 확인 |
| 09:30-10:00 | **전날 이슈 리뷰** - 장애 알람 이력 검토, 에러 로그 분석, 처리 지연 시간대 분석 |
| 10:00-11:00 | **고객사 이슈 대응** - 메시지 발송 실패 문의 처리, 대량 발송 예정 건 사전 협의, 발송 결과 리포트 제공 |
| 11:00-12:00 | **시스템 운영 업무** - 메시징 서버 프로세스 확인, DB 커넥션 풀 점검, 디스크 용량 모니터링 |

## 3-2. 오후 업무 (13:00-18:00)

| 시간 | 업무 내용 |
|------|-----------|
| 13:00-14:00 | **피크 시간 대응 준비** - 오후 피크(14:00-17:00) 대비 시스템 점검, 메시징 서버 클러스터 상태 확인, 자동 스케일링 설정 점검 |
| 14:00-17:00 | **실시간 모니터링** - 메시지 처리량 실시간 추적(TPS), 큐 적체 상황 지속 확인, 장애 알람 즉시 대응(30분 내 1차 조치) |
| 17:00-18:00 | **자동화 스크립트 관리** - 운영 자동화 스크립트 실행 결과 확인, 스크립트 로그 분석 및 개선사항 파악 |

## 3-3. 주간 업무 (Weekly Tasks)

| 요일 | 업무 내용 |
|------|-----------|
| 월요일 | 주간 운영 계획 수립 - 대량 발송 일정 확인, 서버 점검 계획 수립, 스크립트 업데이트 계획 |
| 수요일 | 중간 점검 - 주간 메시지 처리 현황 리뷰, 서버 성능 트렌드 분석, DB 백업 상태 점검 |
| 금요일 | 주간 리포트 작성 - 메시지 발송 통계 리포트, 장애 발생 현황 및 대응 결과, 다음 주 이슈 정리 |

---

## 3-4. 면접 예시 답변

**Q: "평상시에는 어떤 업무를 하셨나요?"**

**A**: "매일 오전에는 톡드림 플랫폼의 전체적인 상태를 점검하는 것부터 시작했습니다. G-EMS 대시보드에서 30대 서버 상태를 확인하고, 전날 메시지 발송량과 성공률이 99% 이상 유지됐는지 체크했어요.

오후에는 실시간 모니터링이 주요 업무였습니다. 특히 14시-17시 피크 시간에는 메시지 처리량이 초당 500건까지 올라가기 때문에 큐 적체나 서버 부하를 지속적으로 모니터링했습니다.

그리고 메시징 서비스 특성상 고객사에서 '왜 메시지가 안 갔나요?'라는 문의가 자주 들어와서, 발송 이력을 조회하고 실패 원인을 분석해서 답변드리는 업무도 많이 했습니다.

주간 단위로는 운영 자동화 스크립트를 지속적으로 개선하고, 메시지 처리 통계 리포트를 작성하는 일을 했습니다."
