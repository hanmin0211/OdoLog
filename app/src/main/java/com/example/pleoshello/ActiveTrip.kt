package com.example.pleoshello

import android.content.Context
import java.time.LocalDateTime

// 운행 중 상태 저장: 출발 시각·출발 거리를 바로 기록해 두었다가, 운행 중 앱이 꺼졌다 켜지면 이어서 처리 (기록 누락 방지)
class ActiveTripStore(context: Context) {
    private val prefs = context.getSharedPreferences("active_trip", Context.MODE_PRIVATE)

    // commit = 즉시 저장 (전원이 갑자기 꺼져도 남도록 apply 대신 사용)
    fun save(startAt: LocalDateTime, startOdo: Float?) {
        prefs.edit().putString("startAt", startAt.toString()).putFloat("startOdo", startOdo ?: -1f).commit()
    }

    fun load(): Pair<LocalDateTime, Float?>? {
        val at = prefs.getString("startAt", null) ?: return null
        val time = runCatching { LocalDateTime.parse(at) }.getOrNull() ?: return null
        val odo = prefs.getFloat("startOdo", -1f)
        return time to odo.takeIf { it >= 0f }
    }

    fun clear() {
        prefs.edit().clear().commit()
    }
}