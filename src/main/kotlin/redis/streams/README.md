# Redis Streams 예제 (카프카 대안)

## Redis Streams란?

Redis 5.0(2018년)부터 도입된 자료구조로, Apache Kafka의 핵심 스트리밍 개념을 Redis 환경에 맞게 구현한 기능입니다.

기존 Redis Pub/Sub과 달리 **메시지가 영구 저장**되며, **Consumer Group**을 통한 분산 처리를 지원합니다.

## 카프카와 비슷한 점

| 개념 | Kafka | Redis Streams |
|------|-------|---------------|
| 메시지 저장소 | Topic | Stream (Key) |
| 메시지 발행 | `producer.send()` | `XADD` |
| 메시지 읽기 | `consumer.poll()` | `XREAD` / `XREADGROUP` |
| 분산 처리 | Consumer Group | Consumer Group |
| 처리 확인 | `commitSync()` | `XACK` |
| 오프셋 | Offset (숫자) | Entry ID (타임스탬프-시퀀스) |
| 크기 제한 | Retention Policy | `MAXLEN` |

## 카프카 vs Redis Streams

| 구분 | Apache Kafka | Redis Streams |
|------|-------------|---------------|
| 저장 방식 | 디스크 기반 (대용량 영구 보관) | 인메모리 기반 (초고속, 메모리 용량 한계) |
| 적합한 규모 | 초대규모 데이터 파이프라인 | 중소규모 실시간 처리 |
| 운영 난이도 | 높음 (ZooKeeper/KRaft 등) | 낮음 (기존 Redis 인프라 활용) |
| 처리량 | 초당 수백만 건 | 초당 수십만 건 |
| 메시지 보존 | 디스크 기반 장기 보존 | 메모리 기반 (MAXLEN으로 관리) |

## 예제 파일 구조

```
src/main/kotlin/redis/streams/
├── ProducerExample.kt        # 메시지 발행 (XADD)
├── SimpleConsumerExample.kt   # 단순 메시지 읽기 (XREAD)
└── ConsumerGroupExample.kt    # Consumer Group 분산 처리 (XREADGROUP + XACK)
```

## 실행 방법

### 1. Redis 서버 실행

```bash
# Docker로 간단하게 실행
docker run -d --name redis -p 6379:6379 redis:7

# 또는 로컬 Redis 실행
redis-server
```

### 2. 예제 실행 순서

```bash
# 1) Producer로 메시지 발행
./gradlew run -PmainClass=redis.streams.ProducerExampleKt

# 2) 단순 Consumer로 메시지 읽기
./gradlew run -PmainClass=redis.streams.SimpleConsumerExampleKt

# 3) Consumer Group으로 분산 처리
./gradlew run -PmainClass=redis.streams.ConsumerGroupExampleKt
```

### 3. Redis CLI로 직접 확인

```bash
# 스트림 내용 조회
redis-cli XRANGE order-events - +

# 스트림 길이 확인
redis-cli XLEN order-events

# Consumer Group 정보 확인
redis-cli XINFO GROUPS order-events

# 실시간 모니터링
redis-cli MONITOR
```

## 핵심 명령어 정리

| 명령어 | 설명 | 카프카 대응 |
|--------|------|------------|
| `XADD` | 스트림에 메시지 추가 | `producer.send()` |
| `XREAD` | 스트림에서 메시지 읽기 | 단순 `consumer.poll()` |
| `XREADGROUP` | Consumer Group으로 읽기 | `consumer.poll()` (그룹) |
| `XACK` | 메시지 처리 완료 확인 | `consumer.commitSync()` |
| `XPENDING` | 미처리 메시지 조회 | Lag 모니터링 |
| `XCLAIM` | 미처리 메시지 다른 컨슈머로 이관 | Rebalancing |
| `XRANGE` | 범위로 메시지 조회 | - |
| `XLEN` | 스트림 길이 조회 | - |
| `XINFO` | 스트림/그룹 정보 조회 | `kafka-consumer-groups.sh` |

## 언제 Redis Streams를 선택할까?

**Redis Streams가 적합한 경우:**
- 이미 Redis를 사용 중이고, 간단한 메시지 큐가 필요할 때
- 밀리초 단위의 극단적 저지연이 필요할 때
- 중소규모 이벤트 처리 (초당 수만~수십만 건)
- 빠르게 프로토타이핑하고 싶을 때

**Kafka가 적합한 경우:**
- 대용량 데이터 파이프라인 (초당 수백만 건 이상)
- 장기간 메시지 보존이 필요할 때
- 복잡한 스트림 처리 (Kafka Streams, ksqlDB)
- 멀티 데이터센터 복제가 필요할 때
