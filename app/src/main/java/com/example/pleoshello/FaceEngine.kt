package com.example.pleoshello

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

// 얼굴 한 장의 결과: 특징값(128개 숫자) 또는 실패 사유
sealed class FaceShot {
    // eyesOpen: 두 눈 중 덜 뜬 쪽의 '눈 뜸' 확률 0~1 (알 수 없으면 null). 사진이 아닌 사람인지 눈 깜빡임으로 볼 때 씀
    class Ok(val embedding: FloatArray, val eyesOpen: Float?) : FaceShot()
    data object NoFace : FaceShot()
    data object TooSmall : FaceShot()
}

// 운전자 얼굴 확인 엔진: ML Kit 로 얼굴 위치를 찾고, MobileFaceNet(Qualcomm AI Hub 배포본, Apache-2.0)으로 특징값을 만듦
// 모두 차량 안에서만 계산하며 사진은 저장하지 않음
// 모델 입력: img1·img2 각 [1, 3, 112, 112] RGB 0~1 (NCHW), 출력: [2, 128] → 같은 얼굴을 두 칸에 넣고 첫 줄만 씀
class FaceEngine(context: Context) {
    private val interpreter = Interpreter(context.assets.open(MODEL).use { input ->
        val bytes = input.readBytes()
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
    })
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).setMinFaceSize(0.15f)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).build()
    )

    // 사진에서 가장 큰 얼굴 하나의 특징값 (조수석 등 다른 얼굴이 함께 찍혀도 운전자 쪽 큰 얼굴을 씀)
    fun analyze(bitmap: Bitmap, onResult: (FaceShot) -> Unit) {
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                onResult(
                    when {
                        face == null -> FaceShot.NoFace
                        face.boundingBox.width() < MIN_FACE_PX -> FaceShot.TooSmall
                        else -> FaceShot.Ok(
                            embed(crop(bitmap, face.boundingBox)),
                            listOfNotNull(face.leftEyeOpenProbability, face.rightEyeOpenProbability).minOrNull()
                        )
                    }
                )
            }
            .addOnFailureListener { onResult(FaceShot.NoFace) }
    }

    // 얼굴이 있는지만 확인 (카메라 영상 방향 찾기에 씀)
    fun hasFace(bitmap: Bitmap, onResult: (Boolean) -> Unit) {
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { faces -> onResult(faces.any { it.boundingBox.width() >= MIN_FACE_PX }) }
            .addOnFailureListener { onResult(false) }
    }

    // 얼굴 상자를 조금 넓힌 정사각형으로 잘라 112×112 로 맞춤
    private fun crop(src: Bitmap, box: Rect): Bitmap {
        val size = (max(box.width(), box.height()) * 1.2f).toInt()
        val cx = box.centerX()
        val cy = box.centerY()
        val left = (cx - size / 2).coerceIn(0, max(0, src.width - 1))
        val top = (cy - size / 2).coerceIn(0, max(0, src.height - 1))
        val w = size.coerceAtMost(src.width - left)
        val h = size.coerceAtMost(src.height - top)
        return Bitmap.createScaledBitmap(Bitmap.createBitmap(src, left, top, w, h), SIDE, SIDE, true)
    }

    private fun embed(face: Bitmap): FloatArray {
        val px = IntArray(SIDE * SIDE)
        face.getPixels(px, 0, SIDE, 0, 0, SIDE, SIDE)
        val input = ByteBuffer.allocateDirect(4 * 3 * SIDE * SIDE).order(ByteOrder.nativeOrder())
        // NCHW: R 평면 전체 → G 평면 → B 평면, 값은 0~1
        for (shift in intArrayOf(16, 8, 0)) px.forEach { input.putFloat(((it shr shift) and 0xFF) / 255f) }
        input.rewind()
        val output = Array(2) { FloatArray(DIM) }
        interpreter.runForMultipleInputsOutputs(arrayOf<Any>(input, input.duplicate().apply { rewind() }), mapOf(0 to output))
        return normalize(output[0])
    }

    fun close() {
        detector.close()
        interpreter.close()
    }

    companion object {
        const val MODEL = "mobile_facenet.tflite"
        const val SIDE = 112
        const val DIM = 128
        const val MIN_FACE_PX = 80
    }
}

fun normalize(v: FloatArray): FloatArray {
    val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat().takeIf { it > 0f } ?: return v
    return FloatArray(v.size) { v[it] / n }
}

// 두 특징값의 비슷한 정도 (1 = 같은 방향, 0 = 무관)
fun cosine(a: FloatArray, b: FloatArray): Float {
    var s = 0f
    for (i in a.indices) s += a[i] * b[i]
    return s
}

// 사진·화면으로 속이는 것 막기: 눈을 감았다가(closed) 다시 뜬(open) 순서를 봐야 통과
// shortcut: 감은 눈 사진과 뜬 눈 사진을 바꿔 대면 통과됨, 출시 때는 적외선(DMS) 카메라나 깊이 기반 위조 판별로 바꿈
class BlinkCheck {
    private var closed = false

    // 한 장의 '눈 뜸' 확률을 넣으면, 감은 뒤 뜬 순간 true
    fun feed(eyesOpen: Float): Boolean {
        if (eyesOpen < CLOSED) closed = true
        return closed && eyesOpen > OPEN
    }

    fun reset() {
        closed = false
    }

    companion object {
        const val CLOSED = 0.3f
        const val OPEN = 0.7f
    }
}

// 직원별 얼굴 특징값 보관: 이름마다 몇 장의 특징값을 저장 (사진은 저장하지 않음). 생체정보라 앱 백업·클라우드 백업에서 뺌
class FaceStore(context: Context) {
    private val prefs = context.getSharedPreferences("faces", Context.MODE_PRIVATE)

    fun names(): Set<String> = prefs.all.keys

    fun get(name: String): List<FloatArray> =
        (prefs.getString(name, "") ?: "").split(";").filter { it.isNotBlank() }
            .map { e -> e.split(",").map { it.toFloat() }.toFloatArray() }

    fun save(name: String, embeddings: List<FloatArray>) {
        prefs.edit().putString(name, embeddings.joinToString(";") { e -> e.joinToString(",") }).commit()
    }

    fun remove(name: String) {
        prefs.edit().remove(name).commit()
    }

    // 가장 비슷한 직원: 기준 점수를 넘고, 2등과 충분히 차이가 날 때만 인정 (닮은 사람끼리 헷갈리지 않게)
    fun identify(e: FloatArray): Match? {
        val scores = names().map { n -> n to (get(n).maxOfOrNull { cosine(it, e) } ?: -1f) }.sortedByDescending { it.second }
        val best = scores.firstOrNull() ?: return null
        val second = scores.getOrNull(1)?.second ?: -1f
        return if (best.second >= MATCH && best.second - second >= MARGIN) Match(best.first, best.second) else null
    }

    data class Match(val name: String, val score: Float)

    // 중복 등록 검사: 등록하려는 사람을 뺀 다른 직원 얼굴과 하나씩 직접 비교해, 기준을 넘는 사람이 있으면 그 이름
    // (식별용 identify 의 1·2등 차이 조건은 쓰지 않음: 닮은 사람이 여럿이어도 중복을 놓치지 않게)
    fun sameAsOther(name: String, shots: List<FloatArray>): String? =
        (names() - name).firstOrNull { other -> get(other).any { known -> shots.any { cosine(it, known) >= MATCH } } }

    companion object {
        const val MATCH = 0.55f   // 같은 사람으로 볼 최소 점수
        const val MARGIN = 0.08f  // 1등과 2등 점수 차이 최소값
    }
}
