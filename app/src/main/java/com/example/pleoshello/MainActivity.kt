package com.example.pleoshello

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 앱이 보여주는 화면: 최초 설정 / 홈 / 주행 중 / 정차 후 목적 선택 / 운행 상태 / 장부 내보내기 / 엑셀 미리보기 / 운행 목록 / 직원 명단 / 설정 / 시동 시 얼굴 확인 / 얼굴 등록
enum class AppScreen { SETUP, HOME, DRIVING, PURPOSE, STATUS, EXPORT, PREVIEW, TRIPS, ROSTER, SETTINGS, FACE_CHECK, FACE_ENROLL }

class MainActivity : ComponentActivity() {
    private lateinit var vehicle: Vehicle
    private lateinit var store: TripStore
    private lateinit var profileStore: ProfileStore
    private var profile by mutableStateOf(Profile())
    private lateinit var rosterStore: RosterStore
    private lateinit var activeStore: ActiveTripStore
    private var drivers by mutableStateOf(listOf<Driver>())
    private var allTrips by mutableStateOf(listOf<Trip>())
    private var allEdits by mutableStateOf(listOf<EditEntry>())
    private var unclassifiedCount by mutableStateOf(0)
    private var screen by mutableStateOf(AppScreen.HOME)
    private var statusText by mutableStateOf("대기 중")
    private var liveText by mutableStateOf("조회 중...")
    private var tripText by mutableStateOf("최근 운행 없음")
    private var savedText by mutableStateOf("")
    private var summaries by mutableStateOf(sampleSummaries)
    private var monthLog by mutableStateOf(monthlyLog(emptyList(), LocalDate.now(KST)))
    private var exportAnnual = false  // 내보내기 장부: false = 이번 달, true = 사업연도
    private var exportResult by mutableStateOf("")

    // 설정 화면: 백업 목록(표시 이름), 결과 안내, 설정에서 연 사업자 정보 수정이 끝나면 돌아갈 화면
    private lateinit var backupStore: BackupStore
    private lateinit var adminLock: AdminLock
    private var adminSet by mutableStateOf(false)
    private var backupFiles by mutableStateOf(listOf<File>())  // 설정 화면에 보이는 백업 목록 그대로 (복원은 이 목록의 파일로)
    private var settingsMessage by mutableStateOf("")
    private var setupReturn = AppScreen.HOME

    // 주행 중 화면과 목적 선택 화면에 보여줄 값
    private var driveDistanceText by mutableStateOf("0.0 km")
    private var driveStartText by mutableStateOf("")
    private var purposeSummary by mutableStateOf("")
    private var purposeDistance by mutableStateOf("")
    private var purposeFlagged by mutableStateOf(false)

    // 운전자 얼굴 확인: 시동 때 얼굴로 확인한 운전자(verifiedDriver)를 정차 후 고른 이름과 대조
    // 얼굴을 등록한 직원이 한 명이라도 있을 때만 동작 (faceNames 가 비면 예전처럼 이름만 고름)
    private var engine: FaceEngine? = null  // 처음 쓸 때 모델을 읽음 (얼굴 기능을 안 쓰면 읽지 않음)
    private val faceEngine: FaceEngine get() = engine ?: FaceEngine(this).also { engine = it }
    private lateinit var faceStore: FaceStore
    private var faceNames by mutableStateOf(setOf<String>())
    private var verifiedDriver by mutableStateOf<String?>(null)  // 지금 시동에서 확인한 운전자 (시동을 끄면 지움)
    private var tripVerified by mutableStateOf<String?>(null)    // 이번 운행이 시작될 때 확인돼 있던 운전자 (정차 후 이름 대조에 씀, 시동을 꺼도 유지)
    private var enrollTarget by mutableStateOf<Driver?>(null)
    private var ignition = -1
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(this, "카메라 권한이 없어 얼굴 확인을 할 수 없습니다", Toast.LENGTH_LONG).show()
            if (screen == AppScreen.FACE_CHECK) screen = AppScreen.HOME
        }
    }

    // 운행 판정에 쓰는 값
    private var lastOdo: Float? = null
    private var driving = false
    private var startAt: LocalDateTime? = null
    private var startOdo: Float? = null
    private var recovering = false  // 앱 재시작으로 되살린 운행 (첫 기어 값을 보고 이어갈지 마무리할지 정함)

    // 음성 안내
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsStatus = "음성: 준비 중"

    // 진단용: 앱 시작 시각, 마지막 거리 알림 시각, 기어 변화 기록
    private val appStartedAt: LocalTime = LocalTime.now(KST).withNano(0)
    private var lastOdoAt: LocalTime? = null
    private val gearEvents = mutableListOf<String>()
    private var debugText by mutableStateOf("")

    // 안드로이드 표준 차량 속성 (기어·속도). 표준 기어를 못 쓰면 플레오스 기어로 대체
    private var stdCar: StandardCar? = null
    private var gearSource = "-"
    private var stdGear = "-"
    private var stdSpeed = "-"
    private val stdStatus = mutableListOf<String>()
    private var stdText by mutableStateOf("준비 전")

    // 속도 권한은 사용자 허용이 필요한 권한이라 팝업으로 요청
    private val speedPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            stdCar?.listenSpeed()
        } else {
            stdStatus.add("속도 권한 거부")
            updateStdText()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = TripStore(this)
        activeStore = ActiveTripStore(this)
        profileStore = ProfileStore(this)
        profile = profileStore.load()
        rosterStore = RosterStore(this)
        drivers = rosterStore.load()
        backupStore = BackupStore(this)
        adminLock = AdminLock(this)
        adminSet = adminLock.isSet
        faceStore = FaceStore(this)
        faceNames = faceStore.names()
        seedRoster(profile)
        updateSavedText()
        updateDebugText()
        setUpVoice()
        // 지난번에 운행 중이던 기록이 남아 있으면 되살림 (운행 중 앱이 꺼졌다 켜진 경우)
        activeStore.load()?.let { (at, odo) -> restoreTrip(at, odo) }
        // 데이터 이용 동의 전에는 차량 데이터를 읽지 않음
        if (profile.consent) {
            startVehicle()
            syncFleet()
            if (!driving) openFaceCheck()  // 앱이 켜지는 때 = 시동을 건 때 (시동 신호를 못 받는 환경 대비)
        } else {
            statusText = "동의 필요"
            liveText = "-"
            screen = AppScreen.SETUP
        }
        setContent {
            // 차량(시스템)의 주간·야간 모드를 따라감. 테마 전환 버튼은 시험용
            val systemDark = isSystemInDarkTheme()
            var dark by remember(systemDark) { mutableStateOf(systemDark) }
            BoxWithConstraints {
                // 기준 해상도(시안 1200×840 dp)에 맞춰 화면 전체를 같은 비율로 확대
                val scale = minOf(maxWidth.value / 1200f, maxHeight.value / 840f)
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
                    val colors = if (dark) DarkColors else LightColors
                    when (screen) {
                        AppScreen.SETUP -> SetupScreen(
                            colors, profile,
                            onDone = { saveProfile(it) },
                            onLater = { screen = setupReturn }
                        )
                        AppScreen.DRIVING -> DrivingScreen(colors, driveDistanceText, driveStartText)
                        AppScreen.PURPOSE -> PurposeScreen(
                            colors, purposeSummary, purposeDistance, purposeFlagged, drivers, tripVerified, faceNames.isNotEmpty(),
                            onSave = { purpose, driver, note -> onPurposeSelected(purpose, driver, note) },
                            onHome = { screen = AppScreen.HOME }
                        )
                        AppScreen.FACE_CHECK -> FaceCheckScreen(
                            colors, faceEngine, faceStore,
                            onVerified = { name ->
                                // 확인 화면이 떠 있고 정차 중일 때만 반영 (출발해서 주행 화면으로 바뀐 뒤 덮어쓰지 않게)
                                if (screen != AppScreen.FACE_CHECK || driving) return@FaceCheckScreen
                                verifiedDriver = name
                                rosterStore.lastDriver = name
                                speak("${name}님, 확인되었습니다")
                                screen = AppScreen.HOME
                            },
                            onSkip = { screen = AppScreen.HOME }
                        )
                        AppScreen.FACE_ENROLL -> enrollTarget?.let { d ->
                            FaceEnrollScreen(colors, d, faceEngine, onSave = { shots -> saveFace(d, shots) }, onBack = { screen = AppScreen.ROSTER })
                        }
                        AppScreen.TRIPS -> TripListScreen(
                            colors, allTrips, profile, drivers, allEdits,
                            onSave = { i, t -> saveTripEdit(i, t) },
                            onBack = { screen = AppScreen.HOME }
                        )
                        AppScreen.ROSTER -> RosterScreen(
                            colors, drivers,
                            onAdd = { d -> saveRoster(drivers + d) },
                            onRemove = { d ->
                                saveRoster(drivers - d)
                                faceStore.remove(d.name)  // 직원을 지우면 얼굴 특징값도 바로 삭제
                                faceNames = faceStore.names()
                            },
                            onBack = { screen = AppScreen.SETTINGS },
                            faces = faceNames,
                            onFace = { d -> openFaceEnroll(d) }
                        )
                        AppScreen.SETTINGS -> SettingsScreen(
                            colors, profileText(),
                            if (drivers.isEmpty()) "없음" else "${drivers.size}명 · ${drivers.joinToString { it.label }}",
                            backupFiles.map { BackupStore.label(it) }, settingsMessage,
                            adminSet = adminSet,
                            onSetPin = { pin ->
                                adminLock.set(pin)
                                adminSet = true
                                settingsMessage = "관리자 비밀번호를 정했습니다"
                            },
                            onCheckPin = { pin -> adminLock.check(pin) },
                            onEditProfile = {
                                setupReturn = AppScreen.SETTINGS
                                screen = AppScreen.SETUP
                            },
                            onOpenRoster = { screen = AppScreen.ROSTER },
                            onBackup = { backupNow() },
                            onRestore = { i -> restoreBackup(i) },
                            onReset = { resetAll() },
                            onBack = { screen = AppScreen.HOME }
                        )
                        AppScreen.EXPORT -> ExportScreen(
                            colors, monthLog, exportResult,
                            fyStartMonth = profile.fyStartMonth,
                            onAnnual = {
                                exportAnnual = it
                                refreshLedger()
                            },
                            onStartMonth = { saveFyStartMonth(it) },
                            onSave = { exportResult = saveLedgerFiles(filesDir, monthLog, profile) },
                            onPreview = { screen = AppScreen.PREVIEW },
                            onBack = { screen = AppScreen.HOME }
                        )
                        AppScreen.PREVIEW -> LedgerPreviewScreen(
                            colors, monthLog, profile, exportResult,
                            onSave = { exportResult = saveLedgerFiles(filesDir, monthLog, profile) },
                            onBack = { screen = AppScreen.EXPORT }
                        )
                        AppScreen.STATUS -> StatusScreen(
                            colors,
                            listOfNotNull(
                                "현재 상태" to "$statusText · 누적 주행거리 $liveText",
                                "최근 운행" to tripText,
                                "저장된 운행" to savedText,
                                "기어·속도 (표준 API)" to stdText,
                                if (DEV_TOOLS) "진단 (개발용)" to debugText else null
                            ),
                            onBack = { screen = AppScreen.HOME }
                        )
                        AppScreen.HOME -> HomeScreen(
                            colors = colors,
                            statusText = statusText,
                            odometerText = liveText,
                            summaries = summaries,
                            onOpenStatus = { screen = AppScreen.STATUS },
                            onOpenExport = { openExport() },
                            onOpenTrips = { openTrips() },
                            unclassified = unclassifiedCount,
                            onToggleTheme = if (DEV_TOOLS) ({ dark = !dark }) else null,
                            onOpenSettings = { openSettings() },
                            driverText = if (faceNames.isEmpty()) null else verifiedDriver ?: "운전자 확인",
                            onCheckDriver = { openFaceCheck(force = true) }
                        )
                    }
                }
            }
        }
    }

    // 동의를 받은 뒤에만 차량 연결을 시작 (이미 시작했으면 다시 하지 않음)
    private fun startVehicle() {
        if (::vehicle.isInitialized) return
        vehicle = Vehicle(this)
        vehicle.initialize()
        statusText = "대기 중"
        liveText = "조회 중..."
        queryOdometer { }
        listenOdometer()
        startStandardCar()
    }

    // 기어·속도는 안드로이드 표준 차량 속성으로 읽음. 표준 기어를 못 쓰면 플레오스 기어 기능으로 대체
    private fun startStandardCar() {
        val c = runCatching {
            StandardCar(
                this,
                onGear = { g ->
                    runOnUiThread {
                        stdGear = gearName(g)
                        updateStdText()
                        if (g != 0) onGearChanged(gearName(g))  // 0 = 알 수 없음 → 판정에 쓰지 않음
                    }
                },
                onSpeed = { v -> runOnUiThread { stdSpeed = String.format(Locale.KOREA, "%.1f km/h", v * 3.6f); updateStdText() } },
                onStatus = { s -> runOnUiThread { stdStatus.add(s); updateStdText() } },
                onIgnition = { state -> runOnUiThread { onIgnitionChanged(state) } }
            )
        }.getOrElse {
            stdStatus.add("차량 서비스 연결 실패: ${it.message}")
            useLegacyGear()
            return
        }
        stdCar = c
        c.listenIgnition()
        if (c.listenGear()) {
            gearSource = "표준"
            updateStdText()
        } else {
            useLegacyGear()
        }
        if (checkSelfPermission(SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED) c.listenSpeed()
        else speedPermission.launch(SPEED_PERMISSION)
    }

    // 대체 경로: 플레오스 기어 기능 (지원 종료 예정이라 표준 기어를 못 쓸 때만 사용)
    private fun useLegacyGear() {
        gearSource = "플레오스 (대체)"
        updateStdText()
        listenGear()
    }

    private fun updateStdText() {
        stdText = "기어 출처 $gearSource · 기어 $stdGear · 속도 $stdSpeed · ${stdStatus.joinToString(" / ")}"
    }

    // 표준 기어 값 → 글자 (P=4, R=2, N=1, D=8, 0=알 수 없음)
    private fun gearName(g: Int): String = when (g) {
        4 -> "P"
        2 -> "R"
        1 -> "N"
        8 -> "D"
        0 -> "?"
        else -> "기타($g)"
    }

    // 앱이 운행 중에 꺼졌다가 다시 켜진 경우: 저장해 둔 출발 정보로 운행 상태를 되살림
    private fun restoreTrip(at: LocalDateTime, odo: Float?) {
        driving = true
        recovering = true
        startAt = at
        startOdo = odo
        val startTime = showTime(at.toLocalTime())
        statusText = "운행 중 (복구됨)"
        tripText = "운행 복구 · 출발 ${showDate(at.toLocalDate())} $startTime"
        driveDistanceText = formatKm(0f)
        driveStartText = "출발 $startTime · 출발 시 ${formatKm(odo)} · 앱 재시작 후 이어서 기록"
    }

    // 최초 설정 완료: 저장 → 차량 연결 시작 → 홈으로 (사업연도 시작 월은 설정 화면에 없으므로 기존 값 유지)
    private fun saveProfile(input: Profile) {
        val p = input.copy(fyStartMonth = profile.fyStartMonth)
        profileStore.save(p)
        profile = p
        seedRoster(p)
        startVehicle()
        screen = setupReturn
    }

    // 직원 명단이 비어 있으면 설정에 입력한 사람을 첫 운전자로 등록
    private fun seedRoster(p: Profile) {
        if (drivers.isEmpty() && p.name.isNotBlank()) {
            saveRoster(listOf(Driver(cleanName(p.name), cleanName(p.dept))))
            rosterStore.lastDriver = cleanName(p.name)
        }
    }

    private fun saveRoster(list: List<Driver>) {
        drivers = list
        rosterStore.save(list)
    }

    // 회사 서버와 동기화: 운행기록을 올리고, 서버에 명단이 있으면 차량 명단을 그 명단으로 바꿈 (명단 관리는 회사가 함)
    // 동의 전에는 보내지 않음
    private fun syncFleet() {
        if (!profile.consent) return
        FleetSync.sync(profile.carNo, store.rawText()) { list -> if (list != drivers) saveRoster(list) }
    }

    // 이번 운행의 기본 운전자: 직전 운전자 → 없으면 명단의 첫 사람
    private fun defaultDriver(): Driver? = drivers.find { it.name == rosterStore.lastDriver } ?: drivers.firstOrNull()

    // 운행 목록 열기: 저장된 운행과 수정 이력을 다시 읽음
    private fun openTrips() {
        allTrips = store.load()
        allEdits = store.loadEdits()
        screen = AppScreen.TRIPS
    }

    // 운행 목록에서 목적·운전자·검토 수정 → 저장(바뀐 항목은 수정 이력에 자동 기록) 후 목록·홈 카드 갱신
    private fun saveTripEdit(index: Int, trip: Trip) {
        store.update(index, trip, profile)
        allTrips = store.load()
        allEdits = store.loadEdits()
        updateSavedText()
        syncFleet()
    }

    private fun profileText(): String =
        if (profile.company.isEmpty()) "미입력"
        else "${profile.company} · ${profile.bizNo} · ${profile.carModel} ${profile.carNo} · ${profile.dept.ifEmpty { "부서 없음" }} ${profile.name}"

    // 안드로이드 기본 음성 기능으로 운행 시작·종료를 소리로 알림 (화면을 보지 않아도 기록 여부를 알 수 있게)
    private fun setUpVoice() {
        tts = TextToSpeech(this) { status ->
            runOnUiThread {
                val engine = tts
                if (status == TextToSpeech.SUCCESS && engine != null) {
                    val result = engine.setLanguage(Locale.KOREAN)
                    ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                    engine.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    ttsStatus = if (ttsReady) "음성: 사용 가능" else "음성: 한국어 미지원"
                } else {
                    ttsStatus = "음성: 사용 불가"
                }
                updateDebugText()
            }
        }
    }

    private fun speak(message: String) {
        if (ttsReady) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "trip")
    }

    private fun listenGear() {
        vehicle.getGear().registerGearSelection(object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) {
                if (gear == null) return
                onGearChanged("$gear".removePrefix("GEAR_").take(1))  // GEAR_PARK → P
            }

            override fun onFailed(e: Exception) {
                runOnUiThread { tripText = "기어 오류: ${e.message}" }
            }
        })
    }

    // 기어 변화로 운행 시작·종료를 판정: P가 아닌 기어 → 운행 시작, 다시 P → 운행 종료 (gear = "P", "D" …)
    private fun onGearChanged(gear: String) {
        runOnUiThread {
            gearEvents.add("$gear ${showTimeSec(LocalTime.now(KST))}")
            if (gearEvents.size > 6) gearEvents.removeAt(0)
            updateDebugText()
            val park = gear == "P"
            // 되살린 운행: 첫 기어 값이 P면 앱이 꺼져 있는 동안 이미 도착한 것 → 지금 거리로 마무리 (도착 시각은 추정)
            if (recovering) {
                recovering = false
                if (park) {
                    driving = false
                    val endAt = LocalDateTime.now(KST).withNano(0)
                    queryOdometer { v -> finishTrip(endAt, v ?: lastOdo, estimatedEnd = true) }
                } else {
                    screen = AppScreen.DRIVING
                }
                return@runOnUiThread
            }
            if (!driving && !park) {
                driving = true
                statusText = "운행 중"
                tripVerified = verifiedDriver  // 출발 시점의 얼굴 확인 결과를 이번 운행에 묶어 둠
                val at = LocalDateTime.now(KST).withNano(0)
                startAt = at
                startOdo = lastOdo
                activeStore.save(at, lastOdo)  // 출발 즉시 저장 (운행 중 앱이 꺼져도 복구 가능)
                val startTime = showTime(at.toLocalTime())
                tripText = "운행 중 · 출발 $startTime"
                driveDistanceText = formatKm(0f)
                driveStartText = "출발 $startTime · 출발 시 ${formatKm(startOdo)}"
                screen = AppScreen.DRIVING
                speak("운행 기록을 시작합니다")
                // 출발 순간의 거리를 직접 조회 (알림 값은 최대 약 4초 늦을 수 있음)
                queryOdometer { v ->
                    if (v != null) {
                        startOdo = v
                        activeStore.save(at, v)
                        driveStartText = "출발 $startTime · 출발 시 ${formatKm(v)}"
                    }
                }
            } else if (driving && park) {
                driving = false
                val endAt = LocalDateTime.now(KST).withNano(0)
                // 도착 순간의 거리를 직접 조회한 뒤 운행 종료 처리
                queryOdometer { v -> finishTrip(endAt, v ?: lastOdo) }
            }
        }
    }

    // 운행 종료: 판정 규칙을 적용해 저장 여부를 정함 (estimatedEnd = 앱이 꺼진 사이 도착해서 도착 시각을 모름)
    private fun finishTrip(endAt: LocalDateTime, endOdo: Float?, estimatedEnd: Boolean = false) {
        activeStore.clear()
        screen = AppScreen.HOME
        statusText = "대기 중"
        val from = startAt
        val time = "${showTime(from?.toLocalTime())} → ${showTime(endAt.toLocalTime())}" + if (estimatedEnd) " (도착 시각 추정)" else ""
        // 운전자: 얼굴 확인을 쓰면 비워 두고 정차 후 본인이 이름을 골라야 채움 (그냥 떠나면 미지정으로 남아 관리자가 검토)
        // 얼굴 확인을 안 쓰면 예전처럼 직전 운전자로 먼저 저장
        val driver = if (faceNames.isEmpty()) defaultDriver() else null
        when (val r = judgeTrip(from, endAt, startOdo, endOdo, estimatedEnd, driver)) {
            is TripEnd.Skipped -> tripText = "운행 종료 · $time · ${r.reason} · 저장 안 함"
            is TripEnd.Saved -> {
                store.append(r.trip)
                updateSavedText()
                syncFleet()
                val distance = r.trip.distanceKm
                tripText = "운행 종료 · $time · ${formatKm(distance)} · 저장됨" +
                    if (r.suspicious) " (확인 필요: 평균 속도 비정상)" else ""
                // 정차 후 요약 + 목적 선택 화면으로 이동
                purposeSummary = "$time · ${r.seconds / 60}분 ${r.seconds % 60}초"
                purposeDistance = formatKm(distance)
                purposeFlagged = r.suspicious
                screen = AppScreen.PURPOSE
                speak(String.format(Locale.KOREA, "%.1f킬로미터 기록했습니다. 운행 목적을 선택해 주세요", distance))
            }
        }
    }

    // 목적·운전자 선택: 마지막 운행에 기록 (목적 "" = 나중에 분류, idNote = 본인 확인 결과를 비고에 덧붙임)
    // 화면은 '기록 완료'를 보여준 뒤 스스로 홈으로 돌아감
    private fun onPurposeSelected(purpose: String, driver: Driver?, idNote: String) {
        store.updateLast { t ->
            t.copy(
                purpose = purpose.ifEmpty { t.purpose }, name = driver?.name ?: t.name, dept = driver?.dept ?: t.dept,
                flag = listOf(t.flag, idNote).filter { it.isNotEmpty() }.joinToString(" / ")
            )
        }
        if (driver != null) rosterStore.lastDriver = driver.name
        updateSavedText()
        syncFleet()
        tripText = "$tripText · 목적: ${purpose.ifEmpty { "미분류" }}"
    }

    // 시동 시 얼굴 확인 화면 열기: 얼굴을 등록한 직원이 있고, 정차 중이며, 아직 확인 전일 때 (force = 홈에서 다시 확인)
    private fun openFaceCheck(force: Boolean = false) {
        if (faceNames.isEmpty() || driving || (!force && verifiedDriver != null)) return
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(android.Manifest.permission.CAMERA)
        }
        screen = AppScreen.FACE_CHECK
    }

    // 시동 상태 변화: 켜지면 얼굴 확인, 꺼지면 확인한 운전자를 잊음 (다음 시동 때 다시 확인)
    private fun onIgnitionChanged(state: Int) {
        val on = state == IGNITION_ON || state == IGNITION_START
        val wasOn = ignition == IGNITION_ON || ignition == IGNITION_START
        ignition = state
        if (on && !wasOn) syncFleet()
        if (on && !wasOn && screen == AppScreen.HOME) openFaceCheck()
        if (!on && wasOn) verifiedDriver = null
    }

    private fun openFaceEnroll(d: Driver) {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(android.Manifest.permission.CAMERA)
        }
        enrollTarget = d
        screen = AppScreen.FACE_ENROLL
    }

    // 얼굴 등록 저장: 이미 다른 직원으로 등록된 얼굴이면 거부 (한 사람이 두 이름으로 등록해 기록을 바꾸는 것 방지)
    private fun saveFace(d: Driver, shots: List<FloatArray>): String? {
        // 지금 이 직원의 등록 화면이 떠 있을 때만 저장 (화면을 떠난 뒤의 저장·화면 전환 방지)
        if (screen != AppScreen.FACE_ENROLL || enrollTarget != d || driving) return null
        val clash = faceStore.sameAsOther(d.name, shots)
        if (clash != null) return "이미 ${clash}님으로 등록된 얼굴입니다. 본인 얼굴로 다시 등록해 주세요"
        faceStore.save(d.name, shots)
        faceNames = faceStore.names()
        Toast.makeText(this, "${d.name}님 얼굴을 등록했습니다", Toast.LENGTH_SHORT).show()
        screen = AppScreen.ROSTER
        return null
    }

    // 내보내기 화면 열기: 운행과 수정 이력을 다시 읽어 장부 준비
    private fun openExport() {
        refreshLedger()
        screen = AppScreen.EXPORT
    }

    // 장부 다시 만들기: 이번 달 또는 사업연도 (수정 이력 포함)
    private fun refreshLedger() {
        val today = LocalDate.now(KST)
        val trips = store.load()
        val edits = store.loadEdits()
        monthLog = if (exportAnnual) fiscalYearLog(trips, today, profile.fyStartMonth, edits) else monthlyLog(trips, today, edits)
        exportResult = ""
    }

    // 사업연도 시작 월 변경: 사업자 정보에 저장하고 장부·홈 요약을 다시 계산
    private fun saveFyStartMonth(month: Int) {
        profile = profile.copy(fyStartMonth = month)
        profileStore.save(profile)
        refreshLedger()
        updateSavedText()
    }

    // 설정 화면 열기: 백업 목록을 다시 읽음
    private fun openSettings() {
        settingsMessage = ""
        refreshBackups()
        screen = AppScreen.SETTINGS
    }

    private fun refreshBackups() {
        backupFiles = backupStore.list()
    }

    // 백업·복원·초기화는 운행 중에는 하지 않음 (기록 중인 운행이 꼬이지 않도록)
    private fun busyDriving(): Boolean {
        if (driving) settingsMessage = "운행 중에는 할 수 없습니다. 정차 후 다시 시도해 주세요"
        return driving
    }

    private fun backupNow() {
        if (busyDriving()) return
        settingsMessage = runCatching { backupStore.backup() }.fold(
            onSuccess = { "백업했습니다 · ${BackupStore.label(it.file)}" + if (it.publicCopy) " · 다운로드/OdoLog 폴더에도 저장" else "" },
            onFailure = { "백업 실패: ${it.message}" }
        )
        refreshBackups()
    }

    // 복원: 지금 데이터를 먼저 백업한 뒤 고른 백업으로 바꾸고, 앱을 다시 열어 모든 화면 값을 새로 읽음
    private fun restoreBackup(index: Int) {
        if (busyDriving()) return
        // 화면에 보이던 그 파일로 복원 (목록을 다시 읽으면 그사이 새 백업이 생겨 순번이 밀릴 수 있음)
        val target = backupFiles.getOrNull(index)?.takeIf { it.exists() } ?: run {
            settingsMessage = "그 백업을 찾을 수 없습니다. 목록을 다시 확인해 주세요"
            refreshBackups()
            return
        }
        val safety = runCatching { backupStore.backup() }.getOrElse {
            settingsMessage = "복원 전 자동 백업에 실패해 복원하지 않았습니다: ${it.message}"
            return
        }
        runCatching { backupStore.restore(target) }.onSuccess {
            Toast.makeText(this, "${BackupStore.label(target)} 백업으로 복원했습니다", Toast.LENGTH_LONG).show()
            recreate()
        }.onFailure { e ->
            // 일부만 바뀐 채 남지 않도록 복원 직전 상태로 되돌림
            val rolledBack = runCatching { backupStore.restore(safety.file) }.isSuccess
            settingsMessage = "복원 실패: ${e.message} · " + if (rolledBack) "복원 전 상태로 되돌렸습니다" else "되돌리기도 실패했습니다. 다시 시도해 주세요"
            refreshBackups()
            if (rolledBack) recreate()
        }
    }

    // 초기화: 자동 백업 → 모든 기록·설정 삭제 → 앱을 다시 열면 동의 전 상태라 처음 설정 화면부터 (차량 연결도 다시 동의 후 시작)
    private fun resetAll() {
        if (busyDriving()) return
        val saved = runCatching { backupStore.backup() }.getOrElse {
            settingsMessage = "자동 백업에 실패해 초기화하지 않았습니다: ${it.message}"
            return
        }
        // 일부만 지워졌으면 방금 백업으로 되돌린 뒤 앱을 다시 열어 화면 값을 저장소와 맞춤 (얼굴 정보는 백업에 없어 지워진 채 남을 수 있음)
        runCatching { backupStore.resetAll() }.onFailure { e ->
            val rolledBack = runCatching { backupStore.restore(saved.file) }.isSuccess
            Toast.makeText(
                this,
                "초기화 실패: ${e.message} · " + if (rolledBack) "초기화 전 상태로 되돌렸습니다" else "되돌리기도 실패했습니다. 설정에서 ${BackupStore.label(saved.file)} 백업으로 복원해 주세요",
                Toast.LENGTH_LONG
            ).show()
            recreate()
            return
        }
        Toast.makeText(this, "초기화했습니다 · ${BackupStore.label(saved.file)} 백업에서 되돌릴 수 있습니다", Toast.LENGTH_LONG).show()
        recreate()
    }

    // 저장된 운행을 다시 읽어 저장 현황과 홈 카드 요약을 갱신
    private fun updateSavedText() {
        val trips = store.load()
        summaries = summarize(trips, LocalDate.now(KST), profile.fyStartMonth)
        unclassifiedCount = trips.count { it.purpose.isEmpty() }
        val last = trips.lastOrNull()
        savedText = "저장된 운행 ${trips.size}건" + if (last != null) {
            val d = runCatching { LocalDate.parse(last.date) }.getOrNull()
            val t = runCatching { LocalTime.parse(last.start) }.getOrNull()
            " · 마지막 ${showDate(d)} ${showTime(t)} ${formatKm(last.distanceKm)} · ${last.purpose.ifEmpty { "미분류" }}"
        } else ""
    }

    private fun updateDebugText() {
        debugText = "앱 시작 ${showTimeSec(appStartedAt)} · 거리 알림 ${showTimeSec(lastOdoAt)} · $ttsStatus · 기어 ${gearEvents.joinToString(" → ")}"
    }

    // 현재 누적 주행거리를 직접 조회해서 결과를 화면 스레드로 전달
    private fun queryOdometer(onResult: (Float?) -> Unit) {
        vehicle.getOdometer().getCurrentDistanceDriven(
            onComplete = { v: Float? ->
                runOnUiThread {
                    if (v != null) {
                        lastOdo = v
                        liveText = formatKm(v)
                    }
                    onResult(v)
                }
            },
            onFailed = { e: Exception ->
                runOnUiThread {
                    liveText = "오류: ${e.message}"
                    onResult(null)
                }
            }
        )
    }

    private fun listenOdometer() {
        vehicle.getOdometer().registerDistanceDriven(object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(value: Float?) {
                runOnUiThread {
                    lastOdo = value
                    liveText = formatKm(value)
                    lastOdoAt = LocalTime.now(KST).withNano(0)
                    // 주행 중이면 이번 운행 거리 갱신
                    val s = startOdo
                    if (driving && value != null && s != null) driveDistanceText = formatKm((value - s).coerceAtLeast(0f))
                    updateDebugText()
                }
            }

            override fun onFailed(e: Exception) {
                runOnUiThread { liveText = "오류: ${e.message}" }
            }
        })
    }

    // 누적 주행거리를 "627,981.0 km" 형식으로 표시
    private fun formatKm(value: Float?): String =
        if (value == null) "값 없음" else String.format(Locale.KOREA, "%,.1f km", value)

    // 화면 표시용 시각·날짜: "오후 2:05", "오후 2:05:31", "10월 22일 (목)" (저장은 24시간제 그대로)
    private fun showTime(t: LocalTime?): String = t?.format(TIME_FMT) ?: "-"
    private fun showTimeSec(t: LocalTime?): String = t?.format(TIME_SEC_FMT) ?: "-"
    private fun showDate(d: LocalDate?): String = d?.format(DATE_FMT) ?: "-"

    override fun onDestroy() {
        tts?.shutdown()
        engine?.close()
        stdCar?.release()
        if (::vehicle.isInitialized) vehicle.release()
        super.onDestroy()
    }

    companion object {
        const val DEV_TOOLS = false       // 개발용 진단 줄·테마 전환 버튼 표시 (시연·제출용은 false). 기록 정리는 설정 → 초기화
        const val IGNITION_ON = 4     // 표준 시동 상태: 켜짐
        const val IGNITION_START = 5  // 표준 시동 상태: 시동 중
        const val SPEED_PERMISSION = "android.car.permission.CAR_SPEED"  // 표준 속도 읽기 권한 (사용자 허용 필요)
        val KST: ZoneId = ZoneId.of("Asia/Seoul")  // 운행기록은 항상 한국 시간으로 기록
        val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
        val TIME_SEC_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("a h:mm:ss", Locale.KOREAN)
        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    }
}
