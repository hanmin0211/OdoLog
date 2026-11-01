package odolog.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import java.awt.Desktop
import java.io.File
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

// 오도로그 회사 서버: 차량마다 올라온 운행기록을 모아 두고, 직원 명단을 차량에 내려 줌. 얼굴 정보는 받지 않음(차량 밖으로 나가지 않음)
// 저장: data/cars/<자동차등록번호>.csv (차량의 trips.csv 그대로), data/roster.tsv ("이름<탭>부서" 줄)
// shortcut: 인증 없이 이 PC(127.0.0.1)에서만 받음 (에뮬레이터는 10.0.2.2 로 이 PC 에 닿음). 실제 배포 때는 HTTPS + 차량별 토큰
fun main() {
    val data = File(System.getenv("ODOLOG_DATA") ?: "data")
    val cars = File(data, "cars").apply { mkdirs() }
    val roster = File(data, "roster.tsv")
    val dashboard = object {}.javaClass.getResource("/dashboard.html")!!.readText()
    val url = "http://127.0.0.1:$PORT"
    val openBrowser = System.getenv("ODOLOG_OPEN") == "1"  // EXE 로 켤 때만 브라우저를 엶 (개발 중 실행은 조용히)
    // 포트가 이미 쓰이고 있으면: 오도로그 서버(EXE 를 두 번 누른 경우)면 대시보드만 열고, 다른 프로그램이면 오류로 끝냄
    if (runCatching { Socket("127.0.0.1", PORT).close() }.isSuccess) {
        val ours = runCatching {
            (URI("$url/api/ping").toURL().openConnection() as HttpURLConnection).run {
                connectTimeout = 2000
                readTimeout = 2000
                inputStream.use { it.readBytes().toString(Charsets.UTF_8) } == PING
            }
        }.getOrDefault(false)
        if (!ours) {
            println("$PORT 번 포트를 다른 프로그램이 쓰고 있어 오도로그 서버를 켤 수 없습니다. 그 프로그램을 끈 뒤 다시 실행하세요.")
            exitProcess(1)
        }
        println("오도로그 서버가 이미 켜져 있습니다: $url")
        if (openBrowser) browse(url)
        return
    }
    println("오도로그 서버: $url  (데이터 ${data.absolutePath})")
    println("끄려면 이 창을 닫으세요.")

    embeddedServer(Netty, port = PORT, host = "127.0.0.1") {
        routing {
            get("/") { call.respondText(dashboard, ContentType.Text.Html) }

            // 이 포트의 서버가 오도로그인지 확인용 (중복 실행 판별)
            get("/api/ping") { call.respondText(PING) }

            // 차량 목록: "번호<탭>마지막 동기화 시각" 줄
            get("/api/cars") {
                val lines = cars.listFiles { f -> f.extension == "csv" }.orEmpty().sortedBy { it.name }
                    .joinToString("\n") { "${it.nameWithoutExtension}\t${SYNCED.format(Instant.ofEpochMilli(it.lastModified()))}" }
                call.respondText(lines, ContentType.Text.Plain)
            }

            get("/api/cars/{car}/trips") {
                val f = carFile(cars, call.parameters["car"]) ?: return@get call.respondText("잘못된 차량 번호", status = HttpStatusCode.BadRequest)
                if (!f.exists()) return@get call.respondText("", status = HttpStatusCode.NotFound)
                call.respondText(f.readText(), ContentType.Text.CSV)
            }

            // 차량이 자기 운행기록 전체를 올림 (수정된 줄까지 반영되도록 통째로 바꿈)
            put("/api/cars/{car}/trips") {
                val f = carFile(cars, call.parameters["car"]) ?: return@put call.respondText("잘못된 차량 번호", status = HttpStatusCode.BadRequest)
                val body = call.receiveText()
                val error = checkTrips(body)
                if (error != null) return@put call.respondText(error, status = HttpStatusCode.BadRequest)
                writeAtomic(f, body)
                call.respondText("ok")
            }

            get("/api/roster") { call.respondText(if (roster.exists()) roster.readText() else "", ContentType.Text.Plain) }

            put("/api/roster") {
                val body = call.receiveText()
                val error = checkRoster(body)
                if (error != null) return@put call.respondText(error, status = HttpStatusCode.BadRequest)
                writeAtomic(roster, body.lines().map { it.trimEnd('\r', ' ') }.filter { it.isNotBlank() }.joinToString("\n"))
                call.respondText("ok")
            }
        }
    }.start(wait = false)
    if (openBrowser) browse(url)
    Thread.currentThread().join()  // 창을 닫을 때까지 서버 유지
}

private fun browse(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }.onFailure { println("브라우저를 열지 못했습니다. 직접 여세요: $url") }
}

const val PORT = 8090
const val MAX_BYTES = 2_000_000
const val PING = "odolog-server"

private val SYNCED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"))

// 앱과 같은 자동차등록번호 형식 ("12가3456", "서울12가3456"). 파일 이름으로 쓰므로 이 형식만 받음 (경로 조작 방지)
private val PLATE = Regex("^([가-힣]{2})?\\d{2,3}[가-힣]\\d{4}$")

fun carFile(dir: File, car: String?): File? = car?.takeIf { PLATE.matches(it) }?.let { File(dir, "$it.csv") }

private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

// 운행기록 검사: 앱의 trips.csv 형식 (사용일자,출발,도착,주행 전 km,주행 후 km,주행거리,비고,목적[,부서,성명])
fun checkTrips(body: String): String? {
    if (body.length > MAX_BYTES) return "너무 큽니다"
    body.lines().filter { it.isNotEmpty() }.forEachIndexed { i, line ->
        val p = line.split(",")
        if (p.size !in 8..10 || !DATE.matches(p[0]) || (3..5).any { p[it].toFloatOrNull() == null }) return "${i + 1}번째 줄 형식이 맞지 않습니다"
    }
    return null
}

fun checkRoster(body: String): String? {
    if (body.length > MAX_BYTES) return "너무 큽니다"
    // 빈 명단은 받지 않음: 차량은 '서버 명단 없음'으로 보고 자기 명단을 유지하므로, 비워 저장하면 화면과 차량이 어긋남
    if (body.isBlank()) return "직원을 한 명 이상 넣어 주세요"
    body.lines().filter { it.isNotBlank() }.forEachIndexed { i, line ->
        val p = line.split("\t")
        if (p.size > 2 || p[0].isBlank() || line.contains(",")) return "${i + 1}번째 줄: '이름<탭>부서' 형식이어야 하고 쉼표는 쓸 수 없습니다"
    }
    return null
}

// 쓰는 도중 꺼져도 이전 파일이 남도록 임시 파일에 쓴 뒤 바꿔 끼움
private fun writeAtomic(f: File, text: String) {
    val tmp = File(f.parentFile.apply { mkdirs() }, f.name + ".tmp")
    tmp.writeText(text)
    Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
