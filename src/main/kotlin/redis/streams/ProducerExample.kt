package redis.streams

import redis.clients.jedis.JedisPooled
import redis.clients.jedis.StreamEntryID
import redis.clients.jedis.params.XAddParams

/**
 * Redis Streams Producer 예제
 *
 * 카프카의 Producer처럼, 스트림(토픽)에 메시지를 추가(append)하는 역할.
 * XADD 명령어를 사용하며, 데이터는 시간순으로 append-only log에 쌓인다.
 */
fun main() {
    val jedis = JedisPooled("localhost", 6379)
    val streamKey = "order-events" // 카프카의 토픽(Topic)에 해당

    println("=== Redis Streams Producer 시작 ===\n")

    // 1) 기본 메시지 발행 - Redis가 자동으로 ID를 생성 (타임스탬프-시퀀스)
    val id1: StreamEntryID = jedis.xadd(
        streamKey,
        StreamEntryID.NEW_ENTRY,
        mapOf(
            "action" to "ORDER_CREATED",
            "orderId" to "1001",
            "userId" to "user-42",
            "amount" to "29900"
        )
    )
    println("[발행] ID=$id1 | ORDER_CREATED orderId=1001")

    // 2) 연속 메시지 발행
    val id2 = jedis.xadd(
        streamKey,
        StreamEntryID.NEW_ENTRY,
        mapOf(
            "action" to "PAYMENT_COMPLETED",
            "orderId" to "1001",
            "paymentMethod" to "CARD"
        )
    )
    println("[발행] ID=$id2 | PAYMENT_COMPLETED orderId=1001")

    val id3 = jedis.xadd(
        streamKey,
        StreamEntryID.NEW_ENTRY,
        mapOf(
            "action" to "ORDER_SHIPPED",
            "orderId" to "1001",
            "trackingNo" to "KR123456789"
        )
    )
    println("[발행] ID=$id3 | ORDER_SHIPPED orderId=1001")

    // 3) MAXLEN으로 스트림 크기 제한 (카프카의 retention 정책과 유사)
    //    메모리 기반이므로 무한정 쌓으면 메모리가 부족해진다.
    //    MAXLEN ~ 1000 -> 약 1000개 정도로 유지 (~ 는 성능 최적화를 위한 근사치)
    val id4 = jedis.xadd(
        streamKey,
        XAddParams.xAddParams().maxLen(1000).approximateTrimming(),
        mapOf(
            "action" to "ORDER_DELIVERED",
            "orderId" to "1001"
        )
    )
    println("[발행] ID=$id4 | ORDER_DELIVERED orderId=1001")

    // 4) 스트림에 쌓인 메시지 수 확인
    val length = jedis.xlen(streamKey)
    println("\n현재 스트림 '$streamKey' 에 쌓인 메시지 수: $length")

    // 5) 스트림 전체 내용 조회 (XRANGE: 처음부터 끝까지)
    println("\n--- 스트림 전체 메시지 조회 (XRANGE) ---")
    val entries = jedis.xrange(streamKey, null as StreamEntryID?, null as StreamEntryID?)
    for (entry in entries) {
        println("  ID=${entry.id} | ${entry.fields}")
    }

    jedis.close()
    println("\n=== Producer 종료 ===")
}
