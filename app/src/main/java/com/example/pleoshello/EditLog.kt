package com.example.pleoshello

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// 수정 이력 1건: 언제, 어느 운행의, 어떤 항목이, 무엇에서 무엇으로 바뀌었는지 (감사 추적용 기록)
data class EditEntry(
    val at: String,         // 수정 일시 (예: 2026-10-24 23:50:12)
    val tripDate: String,   // 운행 사용일자
    val tripStart: String,  // 운행 출발 시각 (사용일자와 함께 어느 운행인지 구분)
    val field: String,      // 목적 / 운전자 / 비고
    val before: String,     // 수정 전
    val after: String,      // 수정 후
) {
    fun matches(t: Trip) = tripDate == t.date && tripStart == t.start

    // 파일 한 줄: 탭으로 구분 (이름·목적에 쉼표가 있어도 안전)
    fun toLine() = listOf(at, tripDate, tripStart, field, before, after).joinToString("\t") { it.replace(Regex("[\t\r\n]"), " ") }

    companion object {
        fun fromLine(line: String): EditEntry? {
            val p = line.split("\t")
            return if (p.size < 6) null else EditEntry(p[0], p[1], p[2], p[3], p[4], p[5])
        }
    }
}

val EDIT_AT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val EDIT_SHORT = DateTimeFormatter.ofPattern("M/d a h:mm", Locale.KOREAN)

// 수정 일시를 "10/24 오후 11:50"처럼 짧게 표시
fun EditEntry.atShort(): String = runCatching { LocalDateTime.parse(at, EDIT_AT).format(EDIT_SHORT) }.getOrDefault(at)

// 운행 수정 전후를 비교해 바뀐 항목만 이력으로 만듦 (빈 값은 사람이 읽을 수 있는 말로 바꿈)
fun editsBetween(old: Trip, new: Trip, p: Profile, at: String): List<EditEntry> {
    fun driver(t: Trip) = t.driverName(p).ifBlank { "미지정" } + t.driverDept(p).let { if (it.isBlank()) "" else " ($it)" }
    fun entry(field: String, before: String, after: String) = EditEntry(at, old.date, old.start, field, before, after)
    return listOfNotNull(
        if (old.purpose != new.purpose) entry("목적", old.purpose.ifEmpty { "미분류" }, new.purpose.ifEmpty { "미분류" }) else null,
        if (driver(old) != driver(new)) entry("운전자", driver(old), driver(new)) else null,
        if (old.flag != new.flag) entry("비고", old.flag.ifEmpty { "없음" }, new.flag.ifEmpty { "없음" }) else null,
    )
}

// 장부에 들어간 운행의 수정 이력만 골라 오래된 순으로
fun editsFor(trips: List<Trip>, edits: List<EditEntry>): List<EditEntry> {
    val keys = trips.map { it.date to it.start }.toSet()
    return edits.filter { (it.tripDate to it.tripStart) in keys }.sortedBy { it.at }
}
