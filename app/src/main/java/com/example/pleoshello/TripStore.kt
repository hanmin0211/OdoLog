package com.example.pleoshello

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.util.Locale

// 운행 1건 = 운행기록부 한 줄
data class Trip(
    val date: String,        // 사용일자 (예: 2026-10-22)
    val start: String,       // 출발 시각
    val end: String,         // 도착 시각
    val startKm: Float,      // 주행 전 계기판 거리
    val endKm: Float,        // 주행 후 계기판 거리
    val distanceKm: Float,   // 주행거리
    val flag: String,        // 비고: "" / "확인 필요" / "종료 시각 추정" / 검토 후 "확인 완료"
    val purpose: String,     // 목적: "" = 미분류
    val dept: String = "",   // 운전자 부서
    val name: String = "",   // 운전자 성명 ("" = 운전자 기능 이전 기록)
) {
    fun toCsv(): String = listOf(
        date, start, end,
        String.format(Locale.US, "%.1f", startKm),
        String.format(Locale.US, "%.1f", endKm),
        String.format(Locale.US, "%.1f", distanceKm),
        flag, purpose, dept, name
    ).joinToString(",")

    companion object {
        fun fromCsv(line: String): Trip? {
            val p = line.split(",")
            if (p.size < 8) return null
            val startKm = p[3].toFloatOrNull() ?: return null
            val endKm = p[4].toFloatOrNull() ?: return null
            val distanceKm = p[5].toFloatOrNull() ?: return null
            // 예전 기록(8칸)도 읽을 수 있게 운전자 칸은 없으면 빈칸
            return Trip(p[0], p[1], p[2], startKm, endKm, distanceKm, p[6], p[7], p.getOrElse(8) { "" }, p.getOrElse(9) { "" })
        }
    }
}

// 비고 처리: "확인 필요"·"종료 시각 추정"은 사람이 검토하면 "확인 완료"로 바뀜 (주황 경고 대상에서 빠지고 장부 비고에는 남음)
fun Trip.needsCheck() = flag.isNotEmpty() && !flag.contains("확인 완료")

fun reviewedFlag(flag: String) = if (flag.contains("확인 필요")) flag.replace("확인 필요", "확인 완료") else "$flag / 확인 완료"

// 운행 기록을 앱 전용 저장 공간의 trips.csv 파일에 한 줄씩 저장 (수정 이력은 edits.tsv 파일 끝에 덧붙이기만 함)
class TripStore(context: Context) {
    private val file = File(context.filesDir, "trips.csv")
    private val editFile = File(context.filesDir, "edits.tsv")

    fun load(): List<Trip> =
        if (file.exists()) file.readLines().mapNotNull { Trip.fromCsv(it) } else emptyList()

    fun append(trip: Trip) {
        file.appendText(trip.toCsv() + "\n")
    }

    // 운행 1건 수정 (목적·운전자·검토만 바뀜. 거리와 시각은 차량 기록이라 그대로)
    // 바뀐 항목을 수정 이력에 먼저 남기고 저장 (이력 없이 바뀌는 기록이 생기지 않도록)
    fun update(index: Int, trip: Trip, p: Profile) {
        val trips = load().toMutableList()
        if (index !in trips.indices) return
        val changes = editsBetween(trips[index], trip, p, LocalDateTime.now(MainActivity.KST).withNano(0).format(EDIT_AT))
        if (changes.isNotEmpty()) editFile.appendText(changes.joinToString("") { it.toLine() + "\n" })
        trips[index] = trip
        save(trips)
    }

    // 저장 파일 그대로 (회사 서버로 올릴 때)
    fun rawText(): String = if (file.exists()) file.readText() else ""

    fun loadEdits(): List<EditEntry> =
        if (editFile.exists()) editFile.readLines().mapNotNull { EditEntry.fromLine(it) } else emptyList()

    // 마지막 운행 수정 (정차 후 목적·운전자 선택)
    fun updateLast(change: (Trip) -> Trip) {
        val trips = load().toMutableList()
        if (trips.isEmpty()) return
        trips[trips.lastIndex] = change(trips.last())
        save(trips)
    }

    private fun save(trips: List<Trip>) {
        file.writeText(trips.joinToString("") { it.toCsv() + "\n" })
    }
}
