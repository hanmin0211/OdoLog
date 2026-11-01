package com.example.pleoshello

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

// 회사 서버(플릿 관리)와 동기화: 이 차량의 운행기록을 올리고 직원 명단을 받아 옴
// 서버가 없거나 꺼져 있으면 조용히 넘어감 (차량은 지금처럼 혼자 기록하고, 다음 동기화 때 전체를 다시 올림)
// 얼굴 특징값은 보내지 않음: 서버에는 운행기록 속 '확인된 운전자 이름'만 감
object FleetSync {
    // shortcut: 에뮬레이터에서 본 이 PC 주소로 고정 (서버는 PC 의 127.0.0.1:8090). 실제 차량에 넣을 때 설정 화면에서 받도록
    private const val SERVER = "http://10.0.2.2:8090"
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    // trips: 지금의 trips.csv 내용 (호출한 쪽에서 읽어 넘김 → 파일을 고치는 중에 읽지 않음)
    // onRoster: 서버에 명단이 있으면 메인 스레드에서 불림
    fun sync(carNo: String, trips: String, onRoster: (List<Driver>) -> Unit) {
        val car = carNo.replace(" ", "")
        if (car.isEmpty()) return
        io.execute {
            runCatching { request("PUT", "/api/cars/${URLEncoder.encode(car, "UTF-8")}/trips", trips) }
            val roster = runCatching { request("GET", "/api/roster", null) }.getOrNull()
                ?.lines()?.filter { it.isNotBlank() }
                ?.map { val p = it.split("\t"); Driver(cleanName(p[0]), cleanName(p.getOrElse(1) { "" })) }
                ?.filter { it.name.isNotEmpty() }?.distinctBy { it.name }
            if (!roster.isNullOrEmpty()) main.post { onRoster(roster) }
        }
    }

    private fun request(method: String, path: String, body: String?): String {
        val c = URL(SERVER + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 2000
            c.readTimeout = 5000
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "text/csv; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            if (c.responseCode !in 200..299) error("서버 응답 ${c.responseCode}")
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }
}
