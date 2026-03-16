package redis.streams

import redis.clients.jedis.JedisPooled
import redis.clients.jedis.StreamEntryID
import redis.clients.jedis.params.XReadParams

/**
 * Redis Streams 단순 Consumer 예제
 *
 * XREAD 명령어로 스트림에서 메시지를 읽는 가장 기본적인 방법.
 * 카프카에서 auto.offset.reset=earliest 로 전체 메시지를 읽거나,
 * latest로 새 메시지만 대기하는 것과 유사하다.
 */
fun main() {
    val jedis = JedisPooled("localhost", 6379)
    val streamKey = "order-events"

    println("=== Redis Streams 단순 Consumer 시작 ===\n")

    // 1) 처음부터 전체 읽기 (XREAD COUNT 10 STREAMS order-events 0-0)
    //    "0-0" = 맨 처음부터 읽기 (카프카의 earliest offset과 동일)
    println("--- 1) 처음부터 전체 읽기 ---")
    val allEntries = jedis.xread(
        XReadParams.xReadParams().count(10),
        mapOf(streamKey to StreamEntryID("0-0"))
    )

    if (allEntries != null) {
        for (streamEntry in allEntries) {
            println("스트림: ${streamEntry.key}")
            for (entry in streamEntry.value) {
                println("  ID=${entry.id} | ${entry.fields}")
            }
        }
    } else {
        println("메시지가 없습니다. 먼저 ProducerExample을 실행하세요.")
        jedis.close()
        return
    }

    // 2) 블로킹 읽기 - 새 메시지가 올 때까지 대기 (카프카의 poll() 루프와 유사)
    //    "$" = 지금 이후의 새 메시지만 대기 (카프카의 latest offset)
    //    BLOCK 5000 = 최대 5초 대기
    println("\n--- 2) 새 메시지 대기 중 (5초 타임아웃)... ---")
    println("(다른 터미널에서 ProducerExample을 실행하면 여기서 메시지를 수신합니다)")

    val newEntries = jedis.xread(
        XReadParams.xReadParams().count(5).block(5000),
        mapOf(streamKey to StreamEntryID.UNRECEIVED_ENTRY) // "$" 에 해당
    )

    if (newEntries != null) {
        for (streamEntry in newEntries) {
            for (entry in streamEntry.value) {
                println("  [새 메시지] ID=${entry.id} | ${entry.fields}")
            }
        }
    } else {
        println("  타임아웃: 5초 동안 새 메시지가 없었습니다.")
    }

    jedis.close()
    println("\n=== 단순 Consumer 종료 ===")
}
