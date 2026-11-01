package com.example.pleoshello

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

// 운행 종료 판정: 저장 여부, '확인 필요', 저장되는 한 줄
class TripRulesTest {
    private val from = LocalDateTime.of(2026, 11, 2, 8, 0, 0)

    private fun judge(km: Float, minutes: Long, startKm: Float? = 100f, estimated: Boolean = false, driver: Driver? = null) =
        judgeTrip(from, from.plusMinutes(minutes), startKm, startKm?.plus(km), estimated, driver)

    @Test
    fun normalTripIsSaved() {
        val r = judge(5f, 10, driver = Driver("강지민", "영업팀")) as TripEnd.Saved
        assertFalse(r.suspicious)
        assertEquals(600L, r.seconds)
        assertEquals("2026-11-02,08:00,08:10,100.0,105.0,5.0,,,영업팀,강지민", r.trip.toCsv())
    }

    @Test
    fun skipsMissingShortAndNegative() {
        assertEquals(TripEnd.Skipped("거리 확인 불가"), judge(5f, 10, startKm = null))
        assertEquals(TripEnd.Skipped("0.1 km 미만"), judge(0.05f, 10))
        assertEquals(TripEnd.Skipped("거리 감소 오류"), judge(-1f, 10))
    }

    @Test
    fun tooFastOrZeroTimeNeedsCheck() {
        val fast = judge(30f, 10) as TripEnd.Saved  // 180 km/h
        assertTrue(fast.suspicious)
        assertEquals("확인 필요", fast.trip.flag)
        assertTrue((judge(1f, 0) as TripEnd.Saved).suspicious)
    }

    @Test
    fun estimatedEndIsFlagged() {
        assertEquals("종료 시각 추정", (judge(5f, 10, estimated = true) as TripEnd.Saved).trip.flag)
    }
}
