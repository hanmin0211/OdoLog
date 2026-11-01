package com.example.pleoshello

import android.content.Context

// 공유 차량의 운전자(직원) 한 명
data class Driver(val name: String, val dept: String) {
    val label: String get() = if (dept.isBlank()) name else "$name ($dept)"
}

// 직원 명단: 앱 전용 설정 저장소에 "이름<탭>부서" 줄로 보관하고, 마지막으로 운전한 사람도 기억 (다음 운행의 기본값)
class RosterStore(context: Context) {
    private val prefs = context.getSharedPreferences("roster", Context.MODE_PRIVATE)

    fun load(): List<Driver> =
        (prefs.getString("drivers", "") ?: "").lines().filter { it.isNotBlank() }.map {
            val p = it.split("\t")
            Driver(p[0], p.getOrElse(1) { "" })
        }

    fun save(drivers: List<Driver>) {
        prefs.edit().putString("drivers", drivers.joinToString("\n") { "${it.name}\t${it.dept}" }).apply()
    }

    var lastDriver: String
        get() = prefs.getString("last", "") ?: ""
        set(value) { prefs.edit().putString("last", value).apply() }
}

// 입력값 정리: 저장 형식을 깨뜨리는 글자(쉼표·탭·줄바꿈)를 공백으로 바꿈
fun cleanName(s: String) = s.replace(Regex("[,\t\r\n]"), " ").trim()

// 운행 기록의 운전자 (운전자 기능 이전의 기록처럼 비어 있으면 설정의 기본 운전자로 채움)
fun Trip.driverName(p: Profile) = name.ifBlank { p.name }
fun Trip.driverDept(p: Profile) = if (name.isBlank()) p.dept else dept