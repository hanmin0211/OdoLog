package com.example.pleoshello

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// 관리자 비밀번호: 복원·초기화처럼 운행 기록을 지우거나 되돌리는 기능을 잠금 (운전자가 기록을 임의로 없애는 것 방지)
// 숫자 4~8자리. 원문은 저장하지 않고 소금값을 섞은 해시만 보관하며, 백업·복원·초기화 대상에서 빠짐 (되돌려서 비밀번호를 우회할 수 없게)
class AdminLock(context: Context) {
    private val prefs = context.getSharedPreferences("admin", Context.MODE_PRIVATE)
    // 틀린 횟수·잠금 시각도 저장 (앱을 다시 열어 시도 제한을 푸는 것 방지)
    private var failures: Int
        get() = prefs.getInt("failures", 0)
        set(v) { prefs.edit().putInt("failures", v).commit() }
    private var lockedUntil: Long
        get() = prefs.getLong("lockedUntil", 0L)
        set(v) { prefs.edit().putLong("lockedUntil", v).commit() }

    val isSet: Boolean get() = prefs.contains("hash")

    fun set(pin: String) {
        val salt = java.util.UUID.randomUUID().toString()
        prefs.edit().putString("salt", salt).putString("hash", hash(salt, pin)).commit()
    }

    // 맞으면 null, 틀리면 안내 문구 (5번 틀리면 30초 동안 시도 막음)
    fun check(pin: String): String? {
        val now = System.currentTimeMillis()
        if (now < lockedUntil) return "잠시 후 다시 시도해 주세요 (${(lockedUntil - now) / 1000 + 1}초)"
        val salt = prefs.getString("salt", "") ?: ""
        if (hash(salt, pin) == prefs.getString("hash", null)) {
            failures = 0
            return null
        }
        failures += 1
        if (failures >= 5) {
            failures = 0
            lockedUntil = now + 30_000
            return "5번 틀려 30초 동안 잠갔습니다"
        }
        return "비밀번호가 맞지 않습니다 (${failures}/5)"
    }

    private fun hash(salt: String, pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        fun validPin(pin: String) = pin.length in 4..8 && pin.all { it.isDigit() }
    }
}

// 백업 결과: 앱 안에 만든 파일 + 다운로드 폴더(Download/OdoLog)에도 복사했는지
data class BackupResult(val file: File, val publicCopy: Boolean)

// 운행 기록·수정 이력·사업자 정보·직원 명단을 zip 파일 하나로 백업하고 되돌림
// 백업 파일은 앱 안 backup 폴더에 두고(초기화해도 지우지 않음), 차량 밖으로 옮길 수 있게 다운로드 폴더에도 복사
class BackupStore(private val context: Context) {
    private val dir = File(context.filesDir, "backup")
    private val dataFiles = listOf("trips.csv", "edits.tsv")   // TripStore 가 쓰는 파일
    private val prefNames = listOf("profile", "roster")        // ProfileStore·RosterStore 가 쓰는 설정 저장소

    // 최근 백업부터
    fun list(): List<File> = dir.listFiles { f -> f.name.endsWith(".zip") }?.sortedByDescending { it.name } ?: emptyList()

    fun backup(): BackupResult {
        dir.mkdirs()
        // 같은 초에 두 번 백업해도 덮어쓰지 않게 번호를 붙임 (복원 직전 자동 백업이 복원할 파일을 덮는 일 방지)
        val stamp = "odolog_backup_${LocalDateTime.now(MainActivity.KST).format(FILE_STAMP)}"
        val file = generateSequence(1) { it + 1 }.map { n -> File(dir, if (n == 1) "$stamp.zip" else "${stamp}_$n.zip") }.first { !it.exists() }
        // 임시 이름으로 다 쓴 뒤 이름을 바꿈 (중간에 실패한 파일이 정상 백업 목록에 뜨지 않게)
        val tmp = File(dir, file.name + ".part")
        runCatching { writeZip(tmp) }.onFailure { tmp.delete(); throw it }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            error("백업 파일을 저장하지 못했습니다")
        }
        // 오래된 백업은 최근 20개만 남김
        list().drop(KEEP).forEach { it.delete() }
        return BackupResult(file, copyToDownloads(file))
    }

    private fun writeZip(file: File) {
        ZipOutputStream(file.outputStream()).use { zip ->
            dataFiles.map { File(context.filesDir, it) }.filter { it.exists() }.forEach { f ->
                zip.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            prefNames.forEach { name ->
                zip.putNextEntry(ZipEntry("$name.properties"))
                prefsToProperties(name).store(zip, null)
                zip.closeEntry()
            }
        }
    }

    // 백업 파일 내용으로 지금 데이터를 통째로 바꿈 (백업에 없는 기록 파일은 지움)
    // 먼저 백업 파일을 끝까지 읽어 확인한 뒤에만 바꿈: 손상된 파일이면 지금 데이터는 그대로 둠
    fun restore(file: File) {
        val data = mutableMapOf<String, ByteArray?>()
        val props = mutableMapOf<String, Properties>()
        ZipFile(file).use { zip ->
            prefNames.forEach { name ->
                val e = zip.getEntry("$name.properties") ?: error("백업 파일에 $name 정보가 없습니다")
                props[name] = Properties().apply { zip.getInputStream(e).use { load(it) } }
            }
            dataFiles.forEach { name -> data[name] = zip.getEntry(name)?.let { e -> zip.getInputStream(e).use { it.readBytes() } } }
        }
        // 새 내용을 모두 임시 파일로 먼저 써 둔 뒤 하나씩 바꿈. 하나라도 실패하면 예외 → 호출한 쪽이 복원 직전 백업으로 되돌림
        val staged = data.mapValues { (name, bytes) -> bytes?.let { File(context.filesDir, "$name.tmp").apply { writeBytes(it) } } }
        staged.forEach { (name, tmp) ->
            val target = File(context.filesDir, name)
            val ok = if (tmp == null) !target.exists() || target.delete() else tmp.renameTo(target)
            if (!ok) error("$name 파일을 바꾸지 못했습니다")
        }
        props.forEach { (name, p) -> if (!propertiesToPrefs(name, p)) error("$name 정보를 저장하지 못했습니다") }
    }

    // 초기화: 운행 기록·수정 이력·사업자 정보·직원 명단·운행 중 상태를 모두 지움 (백업 폴더와 내보낸 장부는 남김)
    // 지우지 못한 것이 있으면 이름을 모아 예외 (남은 데이터를 '초기화됨'으로 오해하지 않게)
    fun resetAll() {
        val failed = dataFiles.filter { File(context.filesDir, it).let { f -> f.exists() && !f.delete() } } +
            (prefNames + "active_trip" + "faces").filter { !context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        if (failed.isNotEmpty()) error("${failed.joinToString()} 삭제 실패")
    }

    // 설정 저장소 → Properties (값 앞에 형식 표시: s 문자, b 참거짓, i 정수, l 긴 정수, f 실수)
    private fun prefsToProperties(name: String): Properties {
        val props = Properties()
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            val typed = when (v) {
                is Boolean -> "b:$v"
                is Int -> "i:$v"
                is Long -> "l:$v"
                is Float -> "f:$v"
                is String -> "s:$v"
                else -> null
            }
            if (typed != null) props.setProperty(k, typed)
        }
        return props
    }

    private fun propertiesToPrefs(name: String, props: Properties): Boolean {
        val e = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        props.stringPropertyNames().forEach { k ->
            val raw = props.getProperty(k)
            val v = raw.drop(2)
            when (raw.take(2)) {
                "b:" -> e.putBoolean(k, v.toBoolean())
                "i:" -> v.toIntOrNull()?.let { e.putInt(k, it) }
                "l:" -> v.toLongOrNull()?.let { e.putLong(k, it) }
                "f:" -> v.toFloatOrNull()?.let { e.putFloat(k, it) }
                "s:" -> e.putString(k, v)
            }
        }
        return e.commit()
    }

    // 다운로드/OdoLog 폴더에 복사 (안드로이드 10 이상, 실패해도 앱 안 백업은 유지)
    private fun copyToDownloads(file: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/OdoLog")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return false
            true
        }.getOrDefault(false)
    }

    companion object {
        const val KEEP = 20
        private val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        private val LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E) a h:mm", Locale.KOREAN)

        // "odolog_backup_20261031_153012.zip" → "10월 31일 (토) 오후 3:30"
        fun label(f: File): String = runCatching {
            LocalDateTime.parse(f.nameWithoutExtension.removePrefix("odolog_backup_").take(15), FILE_STAMP).format(LABEL)
        }.getOrDefault(f.name)
    }
}
