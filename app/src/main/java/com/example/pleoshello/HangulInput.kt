package com.example.pleoshello

import android.view.KeyEvent as AndroidKey
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// PC 키보드(에뮬레이터·외장 키보드)로 한글 입력: 안드로이드 에뮬레이터는 PC 쪽 한글 입력기를 넘겨받지 못해 영문만 들어가므로,
// 입력 칸이 키를 직접 받아 두벌식 자판대로 한글을 조합함. 한/영 전환: 한/영 키(오른쪽 Alt) 또는 Shift+Space
// 화면 키보드로 입력할 때는 그대로 동작 (조합 대상은 PC 키보드의 알파벳 키뿐)

private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
private const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
private const val JONG = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"  // 0번 = 받침 없음

// 두벌식 자판: 알파벳 → 자모 (Shift 는 쌍자음·ㅒ·ㅖ)
private const val QWERTY = "qwertyuiopasdfghjklzxcvbnm"
private const val DUBEOL = "ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡ"
private val SHIFTED = mapOf('q' to 'ㅃ', 'w' to 'ㅉ', 'e' to 'ㄸ', 'r' to 'ㄲ', 't' to 'ㅆ', 'o' to 'ㅒ', 'p' to 'ㅖ')

private val VOWEL_PAIRS = mapOf("ㅗㅏ" to 'ㅘ', "ㅗㅐ" to 'ㅙ', "ㅗㅣ" to 'ㅚ', "ㅜㅓ" to 'ㅝ', "ㅜㅔ" to 'ㅞ', "ㅜㅣ" to 'ㅟ', "ㅡㅣ" to 'ㅢ')
private val JONG_PAIRS = mapOf(
    "ㄱㅅ" to 'ㄳ', "ㄴㅈ" to 'ㄵ', "ㄴㅎ" to 'ㄶ', "ㄹㄱ" to 'ㄺ', "ㄹㅁ" to 'ㄻ', "ㄹㅂ" to 'ㄼ',
    "ㄹㅅ" to 'ㄽ', "ㄹㅌ" to 'ㄾ', "ㄹㅍ" to 'ㄿ', "ㄹㅎ" to 'ㅀ', "ㅂㅅ" to 'ㅄ',
)

private fun isVowel(c: Char) = c in 'ㅏ'..'ㅣ'

// 알파벳 키 → 두벌식 자모 (letter = 'a'..'z')
fun jamoForKey(letter: Char, shift: Boolean): Char {
    val base = DUBEOL[QWERTY.indexOf(letter)]
    return if (shift) SHIFTED[letter] ?: base else base
}

// 입력한 자모 순서 → 완성된 글자 (매번 처음부터 다시 조합: 다음에 모음이 오면 받침이 다음 글자 첫소리로 넘어감)
fun composeHangul(js: List<Char>): String {
    val out = StringBuilder()
    val n = js.size
    fun vowelAt(k: Int) = k < n && isVowel(js[k])
    var i = 0
    while (i < n) {
        val c = js[i]
        if (isVowel(c)) {  // 자음 없이 온 모음
            val pair = if (i + 1 < n) VOWEL_PAIRS["$c${js[i + 1]}"] else null
            out.append(pair ?: c)
            i += if (pair != null) 2 else 1
            continue
        }
        val cho = CHO.indexOf(c)
        if (cho < 0 || !vowelAt(i + 1)) {  // 모음이 뒤따르지 않는 자음은 그대로
            out.append(c)
            i++
            continue
        }
        var j = i + 1
        var v = js[j++]
        if (j < n) VOWEL_PAIRS["$v${js[j]}"]?.let { v = it; j++ }
        var jong = 0
        if (j < n && !isVowel(js[j]) && !vowelAt(j + 1) && JONG.indexOf(js[j]) > 0) {
            val pair = if (j + 1 < n && !vowelAt(j + 2)) JONG_PAIRS["${js[j]}${js[j + 1]}"] else null
            if (pair != null) {
                jong = JONG.indexOf(pair)
                j += 2
            } else {
                jong = JONG.indexOf(js[j])
                j++
            }
        }
        out.append((0xAC00 + (cho * 21 + JUNG.indexOf(v)) * 28 + jong).toChar())
        i = j
    }
    return out.toString()
}

// BasicTextField 대신 쓰는 입력 칸: PC 키보드 한글 조합 + 한/영 표시. hangul = false 면 조합하지 않음 (숫자 칸)
@Composable
fun HangulTextField(
    value: String, onValueChange: (String) -> Unit, textStyle: TextStyle, cursorBrush: Brush, colors: AppColors,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, hangul: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    decorationBox: @Composable (inner: @Composable () -> Unit) -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var korean by remember { mutableStateOf(true) }
    var focused by remember { mutableStateOf(false) }
    // 조합 중인 글자: prefix = 커서 앞 글자, suffix = 커서(선택 영역) 뒤 글자, jamo = 지금 조합 중인 자모들
    var prefix by remember { mutableStateOf(value) }
    var suffix by remember { mutableStateOf("") }
    val jamo = remember { mutableStateListOf<Char>() }
    if (value != field.text) field = TextFieldValue(value, TextRange(value.length))  // 바깥에서 값이 바뀜 (예: 추가 후 비우기)

    // 조합 결과를 커서 자리에 넣고 커서는 조합 글자 바로 뒤에 둠
    fun show() {
        val head = prefix + composeHangul(jamo)
        field = TextFieldValue(head + suffix, TextRange(head.length))
        onValueChange(field.text)
    }
    // 조합 끝: 다음 조합은 지금 커서·선택 영역 자리에서 시작 (선택한 글자는 새 글자로 바뀜)
    fun commit() {
        val s = field.selection
        prefix = field.text.take(s.min)
        suffix = field.text.drop(s.max)
        jamo.clear()
    }

    BasicTextField(
        value = field,
        onValueChange = {
            field = it
            commit()  // 화면 키보드·커서 이동 등 다른 입력이 오면 조합을 끝냄
            onValueChange(it.text)
        },
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = cursorBrush,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; if (!it.isFocused) commit() }
            .onPreviewKeyEvent { e ->
                if (!hangul) return@onPreviewKeyEvent false
                val code = e.key.nativeKeyCode
                val toggle = code == AndroidKey.KEYCODE_LANGUAGE_SWITCH || code == AndroidKey.KEYCODE_KANA ||
                    code == AndroidKey.KEYCODE_ALT_RIGHT || (code == AndroidKey.KEYCODE_SPACE && e.isShiftPressed)
                val letter = code in AndroidKey.KEYCODE_A..AndroidKey.KEYCODE_Z && !e.isCtrlPressed && !e.isAltPressed
                when {
                    toggle -> {
                        if (e.type == KeyEventType.KeyDown) { commit(); korean = !korean }
                        true
                    }
                    korean && letter -> {
                        if (e.type == KeyEventType.KeyDown) {
                            if (jamo.isEmpty() || field.text != prefix + composeHangul(jamo) + suffix) commit()  // 새 조합 시작 (다른 경로로 글자가 바뀐 경우 포함)
                            jamo.add(jamoForKey('a' + (code - AndroidKey.KEYCODE_A), e.isShiftPressed))
                            show()
                        }
                        true
                    }
                    korean && code == AndroidKey.KEYCODE_DEL && jamo.isNotEmpty() -> {
                        if (e.type == KeyEventType.KeyDown) {
                            jamo.removeAt(jamo.lastIndex)
                            show()
                        }
                        true
                    }
                    else -> {
                        if (e.type == KeyEventType.KeyDown && jamo.isNotEmpty()) commit()  // 숫자·띄어쓰기 등: 조합을 끝내고 기본 처리
                        false
                    }
                }
            },
        decorationBox = { inner ->
            decorationBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { inner() }
                    // PC 키보드 입력 모드 표시 (입력 중일 때만)
                    if (hangul && focused) {
                        Text(
                            if (korean) "한" else "A", color = colors.accentText, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.track).padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    )
}
