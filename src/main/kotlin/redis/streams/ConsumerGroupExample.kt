package redis.streams

import redis.clients.jedis.JedisPooled
import redis.clients.jedis.StreamEntryID
import redis.clients.jedis.params.XReadGroupParams

/**
 * Redis Streams Consumer Group 예제
 *
 * 카프카의 Consumer Group과 동일한 개념.
 * - 같은 그룹 내 컨슈머들이 메시지를 분산 처리 (각 메시지는 그룹 내 하나의 컨슈머만 받음)
 * - 각 컨슈머의 읽기 위치(offset)를 그룹 단위로 관리
 * - 처리 완료 후 XACK로 확인 (카프카의 commit offset과 유사)
 *
 * 카프카와의 대응:
 *   Consumer Group  →  Consumer Group (동일 개념)
 *   XREADGROUP      →  poll()
 *   XACK            →  commitSync() / commitAsync()
 *   Pending 목록     →  아직 커밋되지 않은 오프셋의 메시지들
 */
fun main() {
    val jedis = JedisPooled("localhost", 6379)
    val streamKey = "order-events"
    val groupName = "order-processing-group"  // 카프카의 group.id
    val consumerName = "worker-1"              // 카프카의 client.id

    println("=== Redis Streams Consumer Group 예제 시작 ===\n")

    // 1) Consumer Group 생성
    //    "0" = 스트림의 맨 처음부터 읽기
    //    "$" = 그룹 생성 이후의 새 메시지만 읽기
    try {
        jedis.xgroupCreate(streamKey, groupName, StreamEntryID("0"), false)
        println("[그룹 생성] '$groupName' 그룹을 '$streamKey' 스트림에 생성했습니다.")
    } catch (e: Exception) {
        if (e.message?.contains("BUSYGROUP") == true) {
            println("[그룹 존재] '$groupName' 그룹이 이미 존재합니다. 계속 진행합니다.")
        } else {
            throw e
        }
    }

    // 2) Consumer Group으로 메시지 읽기 (XREADGROUP)
    //    ">" = 아직 이 그룹에 전달되지 않은 새 메시지만 가져옴
    println("\n--- 새 메시지 읽기 (XREADGROUP) ---")
    val entries = jedis.xreadGroup(
        groupName,
        consumerName,
        XReadGroupParams.xReadGroupParams().count(10).block(2000),
        mapOf(streamKey to StreamEntryID.UNRECEIVED_ENTRY) // ">" 에 해당
    )

    if (entries != null) {
        for (streamEntry in entries) {
            for (entry in streamEntry.value) {
                println("  [수신] ID=${entry.id} | ${entry.fields}")

                // 3) 메시지 처리 후 ACK (카프카의 offset commit과 동일)
                //    ACK를 보내지 않으면 pending 목록에 남아있어서
                //    다른 컨슈머가 XCLAIM으로 가져갈 수 있음 (장애 복구)
                val ackCount = jedis.xack(streamKey, groupName, entry.id)
                println("       → ACK 완료 (acknowledged: $ackCount)")
            }
        }
    } else {
        println("  새 메시지가 없습니다. 먼저 ProducerExample을 실행하세요.")
    }

    // 4) Pending 메시지 확인 (아직 ACK되지 않은 메시지)
    //    카프카에서 커밋되지 않은 오프셋의 메시지들과 유사
    println("\n--- Pending 메시지 확인 (XPENDING) ---")
    val pending = jedis.xpending(streamKey, groupName)
    println("  총 pending 메시지 수: ${pending.total}")
    if (pending.total > 0) {
        println("  최소 ID: ${pending.minId}")
        println("  최대 ID: ${pending.maxId}")
        println("  컨슈머별 pending:")
        pending.consumerMessageCount.forEach { (consumer, count) ->
            println("    $consumer: ${count}개")
        }
    }

    // 5) Consumer Group 정보 조회
    println("\n--- Consumer Group 정보 (XINFO GROUPS) ---")
    val groups = jedis.xinfoGroups(streamKey)
    for (group in groups) {
        println("  그룹명: ${group.name}")
        println("  컨슈머 수: ${group.consumers}")
        println("  Pending 수: ${group.pending}")
        println("  마지막 전달 ID: ${group.lastDeliveredId}")
    }

    jedis.close()
    println("\n=== Consumer Group 예제 종료 ===")
}
