package com.example.pleoshello

import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// 카메라 화면 + 일정 간격으로 최신 화면을 넘겨줌 (busy 동안은 건너뜀)
// - 실제 카메라가 있으면 외부 연결 카메라(에뮬레이터의 차 실내 예시 영상 등)는 목록에서 뺌. 여러 대 남으면 '카메라 전환'
// - 카메라마다 영상이 돌아가 있을 수 있어(세로로 누운 웹캠 등) 0·90·270·180도로 돌려 보며 얼굴이 서는 방향을 찾아 기억함.
//   틀리면 '화면 회전'으로 바로잡을 수 있고, 그 방향도 기억함
@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun FaceCamera(modifier: Modifier, engine: FaceEngine, busy: () -> Boolean, onFrame: (Bitmap) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("camera", android.content.Context.MODE_PRIVATE) }
    val main = remember { ContextCompat.getMainExecutor(context) }
    var index by remember { mutableStateOf(prefs.getInt("index", 0)) }
    var count by remember { mutableStateOf(0) }
    var cameraId by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    val rotation = remember { AtomicInteger(-1) }   // 이 카메라에 쓸 회전 각도 (-1 = 아직 모름). 분석 스레드에서도 읽음
    val calibrating = remember { AtomicBoolean(false) }
    val lastShow = remember { AtomicLong(0L) }
    val lastSend = remember { AtomicLong(0L) }

    // 아직 방향을 모를 때: 원본 영상을 여러 각도로 돌려 보고 얼굴이 처음 잡히는 각도를 이 카메라의 방향으로 저장
    fun calibrate(raw: Bitmap, guess: Int) {
        val tries = listOf(guess, 0, 90, 270, 180).distinct()
        fun attempt(i: Int) {
            if (i >= tries.size) {
                calibrating.set(false)
                onFrame(rotate(raw, guess))  // 어느 방향에서도 얼굴이 없으면 화면은 '얼굴이 보이지 않습니다'를 안내
                return
            }
            val candidate = rotate(raw, tries[i])
            engine.hasFace(candidate) { found ->
                if (found) {
                    rotation.set(tries[i])
                    prefs.edit().putInt("rot_$cameraId", tries[i]).apply()
                    calibrating.set(false)
                    onFrame(candidate)
                } else attempt(i + 1)
            }
        }
        attempt(0)
    }

    DisposableEffect(index) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val all = provider.availableCameraInfos.sortedBy { Camera2CameraInfo.from(it).cameraId }
                val builtIn = all.filter {
                    Camera2CameraInfo.from(it).getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) !=
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL
                }
                val cameras = builtIn.ifEmpty { all }
                if (cameras.isEmpty()) error("카메라를 찾을 수 없습니다")
                count = cameras.size
                val info = cameras[index % cameras.size]
                cameraId = Camera2CameraInfo.from(info).cameraId
                rotation.set(prefs.getInt("rot_$cameraId", -1))
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { image ->
                    val now = System.currentTimeMillis()
                    if (now - lastShow.get() >= SHOW_MS) {
                        lastShow.set(now)
                        val raw = image.toBitmap()
                        val known = rotation.get()
                        val guess = image.imageInfo.rotationDegrees
                        val frame = rotate(raw, if (known >= 0) known else guess)
                        val send = now - lastSend.get() >= FRAME_MS && !busy() && !calibrating.get()
                        if (send) lastSend.set(now)
                        main.execute {
                            shown = frame.asImageBitmap()
                            if (send) {
                                if (known >= 0) onFrame(frame)
                                else if (calibrating.compareAndSet(false, true)) calibrate(raw, guess)
                            }
                        }
                    }
                    image.close()
                }
                provider.unbindAll()
                provider.bindToLifecycle(owner, info.cameraSelector, analysis)
            }.onFailure { e -> onError(e.message ?: "카메라 오류") }
        }, main)
        onDispose {
            runCatching { future.get().unbindAll() }
            executor.shutdown()
        }
    }

    Box(modifier) {
        shown?.let {
            // 거울처럼 좌우를 뒤집어 보여줌 (분석에는 원래 방향을 씀)
            Image(it, null, Modifier.fillMaxSize().graphicsLayer(scaleX = -1f), contentScale = ContentScale.Crop)
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(AppIcons.Rotate, "화면 회전", DarkColors) {
                val next = ((rotation.get().takeIf { it >= 0 } ?: 0) + 90) % 360
                rotation.set(next)
                prefs.edit().putInt("rot_$cameraId", next).apply()
            }
            if (count > 1) PillButton(AppIcons.Restore, "카메라 전환 ${index % count + 1}/$count", DarkColors) {
                index = (index + 1) % count
                prefs.edit().putInt("index", index).apply()
            }
        }
    }
}

private fun rotate(b: Bitmap, degrees: Int): Bitmap =
    if (degrees % 360 == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)

private const val FRAME_MS = 700L  // 얼굴 분석 간격
private const val SHOW_MS = 80L    // 화면 갱신 간격

// 얼굴 등록: 생체정보 동의 → 정면 얼굴 3장 자동 수집 → 저장 (onSave 가 null 이 아닌 문구를 돌려주면 저장 거부 사유)
@Composable
fun FaceEnrollScreen(colors: AppColors, driver: Driver, engine: FaceEngine, onSave: (List<FloatArray>) -> String?, onBack: () -> Unit) {
    var agreed by remember { mutableStateOf(false) }
    val shots = remember { mutableStateListOf<FloatArray>() }
    var busy by remember { mutableStateOf(false) }
    val alive = rememberAlive()
    var guide by remember { mutableStateOf("카메라를 정면으로 봐 주세요") }
    var error by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(colors.bg).padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PillButton(AppIcons.Back, "직원 명단", colors, onClick = onBack)
            Column {
                Text("${driver.name}님 얼굴 등록", color = colors.text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("시동을 걸 때 운전자가 누구인지 확인하는 데 씁니다", color = colors.sub, fontSize = 20.sp)
            }
        }
        if (!agreed) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(20.dp)).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("생체정보(얼굴) 수집·이용 동의", color = colors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                listOf(
                    "목적: 업무용 차량 운전자 본인 확인 (운행기록부의 운전자 정확성 확보)",
                    "항목: 얼굴 특징값 (사진은 저장하지 않으며, 원래 얼굴로 되돌릴 수 없는 숫자 128개)",
                    "보관: 이 차량 안에만 보관하고 외부로 보내거나 백업 파일에 넣지 않음",
                    "삭제: 직원 명단에서 지우거나 초기화하면 즉시 삭제",
                    "거부: 동의하지 않아도 운행 기록은 되며, 본인 확인 안 된 운행으로 관리자가 검토함",
                ).forEach { Text("• $it", color = colors.body, fontSize = 22.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillButton(AppIcons.Check, "${driver.name} 본인이 동의합니다", colors, tint = colors.accentText) { agreed = true }
                PillButton(AppIcons.Back, "동의하지 않음", colors, onClick = onBack)
            }
        } else {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                FaceCamera(
                    Modifier.weight(1.2f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Color.Black),
                    engine = engine,
                    busy = { busy || shots.size >= SHOTS },
                    onFrame = { frame ->
                        busy = true
                        engine.analyze(frame) analyzed@{ r ->
                        if (!alive.get()) return@analyzed  // 화면을 떠난 뒤 늦게 도착한 결과는 버림 (저장·화면 전환 방지)
                            when (r) {
                                is FaceShot.Ok -> {
                                    shots.add(r.embedding)
                                    guide = if (shots.size < SHOTS) "좋아요. 고개를 아주 조금씩 움직여 주세요" else "등록 중…"
                                    if (shots.size == SHOTS) error = onSave(shots.toList()) ?: ""
                                    if (error.isNotEmpty()) shots.clear()
                                }
                                FaceShot.NoFace -> guide = "얼굴이 보이지 않습니다. 카메라를 정면으로 봐 주세요"
                                FaceShot.TooSmall -> guide = "조금 더 가까이 와 주세요"
                            }
                            busy = false
                        }
                    },
                    onError = { error = it }
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)) {
                    Text("${shots.size} / $SHOTS", color = colors.text, fontSize = 72.sp, fontWeight = FontWeight.Bold)
                    Text(guide, color = colors.body, fontSize = 26.sp)
                    if (error.isNotEmpty()) Text(error, color = colors.warnText, fontSize = 24.sp)
                }
            }
        }
    }
}

// 시동 시 얼굴 확인: 등록된 직원 얼굴과 비교해 같은 사람으로 두 번 연속 확인되고, 눈을 감았다 뜨면(사진이 아님) 운전자로 정함
// 확인이 안 돼도 운행은 막지 않음 (건너뛰면 '본인 확인 안 됨'으로 기록되어 관리자가 검토)
@Composable
fun FaceCheckScreen(colors: AppColors, engine: FaceEngine, store: FaceStore, onVerified: (String) -> Unit, onSkip: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    val alive = rememberAlive()
    var guide by remember { mutableStateOf("카메라를 봐 주세요") }
    var candidate by remember { mutableStateOf<String?>(null) }
    var live by remember { mutableStateOf<String?>(null) }  // 얼굴로 확인된 사람 (눈 깜빡임 확인 전)
    val blink = remember { BlinkCheck() }
    var done by remember { mutableStateOf<String?>(null) }
    var misses by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(done) {
        done?.let {
            delay(1500)
            onVerified(it)
        }
    }
    Column(
        Modifier.fillMaxSize().background(colors.bg).padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("운전자 확인", color = colors.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text("시동을 걸면 등록된 직원 얼굴로 운전자를 확인합니다", color = colors.sub, fontSize = 20.sp)
            }
            PillButton(AppIcons.Clock, "건너뛰기", colors, onClick = onSkip)
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            FaceCamera(
                Modifier.weight(1.2f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Color.Black),
                engine = engine,
                busy = { busy || done != null },
                onFrame = { frame ->
                    busy = true
                    engine.analyze(frame) analyzed@{ r ->
                        if (!alive.get()) return@analyzed  // 화면을 떠난 뒤 늦게 도착한 결과는 버림 (저장·화면 전환 방지)
                        when (r) {
                            is FaceShot.Ok -> if (live != null) {
                                // 얼굴은 확인됨 → 눈을 감았다 뜨는지 봄. 뜬 눈 장면에서 다시 같은 사람인지도 확인
                                val eyes = r.eyesOpen
                                val m = if (eyes != null && eyes >= BlinkCheck.CLOSED) store.identify(r.embedding) else null
                                when {
                                    eyes == null -> guide = "눈 깜빡임을 확인할 수 없습니다. 건너뛰기를 눌러 주세요"
                                    eyes < BlinkCheck.CLOSED -> { blink.feed(eyes); guide = "좋아요. 이제 눈을 떠 주세요" }
                                    m != null && m.name != live -> { live = null; candidate = null; blink.reset(); guide = "확인 중…" }
                                    m?.name == live && blink.feed(eyes) -> done = live
                                }
                            } else {
                                val m = store.identify(r.embedding)
                                if (m == null) {
                                    candidate = null
                                    misses++
                                    guide = if (misses >= 6) "등록된 직원 얼굴이 아닙니다. 다시 시도하거나 건너뛰세요" else "확인 중…"
                                } else if (m.name == candidate) {
                                    live = m.name
                                    blink.reset()
                                    guide = "${m.name}님, 눈을 1초쯤 감았다 떠 주세요"
                                } else {
                                    candidate = m.name
                                    guide = "확인 중…"
                                }
                            }
                            FaceShot.NoFace -> guide = "얼굴이 보이지 않습니다"
                            FaceShot.TooSmall -> guide = "조금 더 가까이 와 주세요"
                        }
                        busy = false
                    }
                },
                onError = { error = it }
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)) {
                if (done != null) {
                    Icon(AppIcons.Check, null, tint = colors.ok, modifier = Modifier.size(96.dp))
                    Text("${done}님, 확인되었습니다", color = colors.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text("안전 운전하세요", color = colors.sub, fontSize = 24.sp)
                } else {
                    Icon(AppIcons.Person, null, tint = colors.accentText, modifier = Modifier.size(96.dp))
                    Text(guide, color = colors.body, fontSize = 28.sp)
                    if (error.isNotEmpty()) Text("$error · 건너뛰면 운행은 그대로 기록됩니다", color = colors.warnText, fontSize = 22.sp)
                }
            }
        }
    }
}

private const val SHOTS = 3

// 화면이 떠 있는 동안만 true (카메라 분석 결과는 화면이 닫힌 뒤에 도착할 수 있음)
@Composable
private fun rememberAlive(): AtomicBoolean {
    val alive = remember { AtomicBoolean(true) }
    DisposableEffect(Unit) { onDispose { alive.set(false) } }
    return alive
}
