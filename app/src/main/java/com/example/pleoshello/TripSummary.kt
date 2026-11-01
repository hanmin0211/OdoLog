package com.example.pleoshello

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FMT = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
private val MONTH_DAY_FMT = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
private val YEAR_DAY_FMT = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)

// 저장된 운행으로 오늘 / 이번 달 / 사업연도 요약을 계산 (사업연도 시작 월은 사업자 정보에 저장)
fun summarize(trips: List<Trip>, today: LocalDate, fyStartMonth: Int = 1): List<PeriodSummary> {
    val dated = trips.mapNotNull { t ->
        runCatching { LocalDate.parse(t.date) }.getOrNull()?.let { it to t }
    }

    fun build(label: String, period: String, inPeriod: (LocalDate) -> Boolean): PeriodSummary {
        val list = dated.filter { inPeriod(it.first) }.map { it.second }
        fun km(purpose: String) = list.filter { it.purpose == purpose }.sumOf { it.distanceKm.toDouble() }
        val flagged = list.count { it.needsCheck() }
        return PeriodSummary(
            label = label,
            period = if (flagged > 0) "$period · 확인 필요 ${flagged}건" else period,
            commute = km("출퇴근"),
            work = km("일반 업무"),
            personal = km("개인"),
            unclassified = km(""),
            trips = list.size,
        )
    }

    return listOf(
        build("오늘", today.format(DAY_FMT)) { it == today },
        build("이번 달", "${today.withDayOfMonth(1).format(MONTH_DAY_FMT)} ~ ${today.format(MONTH_DAY_FMT)}") {
            it.year == today.year && it.month == today.month
        },
        fiscalYearStart(today, fyStartMonth).let { fy ->
            build("사업연도", "${fy.format(YEAR_DAY_FMT)} ~ ${today.format(if (fy.year == today.year) MONTH_DAY_FMT else YEAR_DAY_FMT)}") {
                !it.isBefore(fy) && !it.isAfter(today)
            }
        },
    )
}
