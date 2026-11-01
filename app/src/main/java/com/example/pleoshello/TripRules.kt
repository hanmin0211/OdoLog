package com.example.pleoshello

import java.time.Duration
import java.time.LocalDateTime

const val MIN_TRIP_KM = 0.1f   // 이보다 짧은 운행은 저장하지 않음
const val MAX_AVG_KMH = 150.0  // 평균 속도가 이보다 크면 "확인 필요" (한국 고속도로 최고 제한속도 110~120 km/h 기준)

// 운행 종료 판정 결과: 저장할 운행 한 줄, 또는 저장하지 않는 이유
sealed class TripEnd {
    data class Saved(val trip: Trip, val suspicious: Boolean, val seconds: Long) : TripEnd()
    data class Skipped(val reason: String) : TripEnd()
}

// 운행 종료 판정 (화면·저장과 떨어진 규칙만): 거리 확인 → 0.1 km 미만 제외 → 평균 속도 비정상이면 '확인 필요'
// estimatedEnd = 앱이 꺼진 사이 도착해서 도착 시각을 모름, driver = 먼저 채워 둘 운전자 (없으면 정차 후 고름)
fun judgeTrip(from: LocalDateTime?, endAt: LocalDateTime, startKm: Float?, endKm: Float?, estimatedEnd: Boolean, driver: Driver?): TripEnd {
    if (from == null || startKm == null || endKm == null) return TripEnd.Skipped("거리 확인 불가")
    val distance = endKm - startKm
    if (distance < 0f) return TripEnd.Skipped("거리 감소 오류")
    if (distance < MIN_TRIP_KM) return TripEnd.Skipped("0.1 km 미만")
    val secs = Duration.between(from, endAt).seconds
    val hours = secs / 3600.0
    val suspicious = hours <= 0.0 || distance / hours > MAX_AVG_KMH
    // 파일에는 24시간제 그대로 저장 (계산·정렬·엑셀 호환용)
    val trip = Trip(
        date = from.toLocalDate().toString(),
        start = from.toLocalTime().toString(),
        end = endAt.toLocalTime().toString(),
        startKm = startKm,
        endKm = endKm,
        distanceKm = distance,
        flag = listOfNotNull(if (suspicious) "확인 필요" else null, if (estimatedEnd) "종료 시각 추정" else null).joinToString(" / "),
        purpose = "",
        dept = driver?.dept ?: "",
        name = driver?.name ?: ""
    )
    return TripEnd.Saved(trip, suspicious, secs)
}
