package com.example.pleoshello

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 최초 설정: 1 데이터 이용 동의 → 2 사업자·차량 정보 → 3 사용자 정보
@Composable
fun SetupScreen(colors: AppColors, initial: Profile, onDone: (Profile) -> Unit, onLater: () -> Unit) {
    var step by remember { mutableStateOf(1) }
    var company by remember { mutableStateOf(initial.company) }
    var bizNo by remember { mutableStateOf(initial.bizNo) }
    var carModel by remember { mutableStateOf(initial.carModel) }
    var carNo by remember { mutableStateOf(initial.carNo) }
    var dept by remember { mutableStateOf(initial.dept) }
    var name by remember { mutableStateOf(initial.name) }

    val bizDigits = bizNo.count { it.isDigit() }
    val bizOk = isValidBizNo(bizNo)
    val step2Ok = company.isNotBlank() && bizOk && carModel.isNotBlank() && carNo.isNotBlank()

    Column(
        Modifier.fillMaxSize().background(colors.bg).imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 64.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        StepBar(step, colors)
        when (step) {
            1 -> {
                Title("차량 데이터 이용 동의", "운행기록부를 자동으로 작성하려면 아래 차량 데이터를 읽어야 합니다.", colors)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(20.dp))
                ) {
                    DataRow("누적 주행거리 (km)", "운행 전·후 계기판 거리 기록", colors, divider = true)
                    DataRow("기어 상태 (P · R · N · D)", "운행 시작·종료 판단", colors, divider = true); DataRow("차량 속도 (선택)", "기록 검증 보조 · 속도 값은 저장하지 않음", colors, divider = false)
                }
                Text(
                    "위치(GPS)는 수집하지 않습니다. 기록은 이 차량 안에만 저장되고, 내보내기를 누를 때만 파일로 만들어집니다.",
                    color = colors.body, fontSize = 22.sp
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton("동의하고 다음", colors, enabled = true, modifier = Modifier.weight(1f)) { step = 2 }
                    PillButton("나중에", colors, onLater)
                }
            }
            2 -> {
                Title("사업자·차량 정보", "운행기록부 머리말에 들어갑니다. 국세청 서식 그대로 내보내기 위해 필요해요.", colors)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Field("상호명", company, "예: 강지민상사", colors, Modifier.weight(1f)) { company = it }
                    Field(
                        "사업자등록번호", bizNo, "숫자 10자리", colors, Modifier.weight(1f), number = true,
                        message = if (bizDigits == 10 && !bizOk) "번호를 다시 확인해 주세요 (검증번호 불일치)" else null
                    ) { v -> bizNo = v.filter { it.isDigit() || it == '-' }.take(12) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Field("차종", carModel, "예: 쏘나타", colors, Modifier.weight(1f)) { carModel = it }
                    Field(
                        "자동차등록번호", carNo, "예: 12가 3456", colors, Modifier.weight(1f),
                        message = if (carNo.isNotBlank() && !isValidCarNo(carNo)) "형식을 확인해 주세요 (예: 12가 3456)" else null
                    ) { carNo = it }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton(AppIcons.Back, "이전", colors) { step = 1 }
                    PrimaryButton("다음", colors, enabled = step2Ok, modifier = Modifier.weight(1f)) { step = 3 }
                }
            }
            else -> {
                Title("사용자 정보", "운행기록부의 부서·성명 칸에 들어갑니다.", colors)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Field("부서 (선택)", dept, "예: 영업팀 · 개인사업자는 비워도 됨", colors, Modifier.weight(1f)) { dept = it }
                    Field("성명", name, "예: 강지민", colors, Modifier.weight(1f)) { name = it }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton(AppIcons.Back, "이전", colors) { step = 2 }
                    PrimaryButton("저장하고 시작", colors, enabled = name.isNotBlank(), modifier = Modifier.weight(1f)) {
                        onDone(
                            Profile(
                                company = company.trim(),
                                bizNo = formatBizNo(bizNo),
                                carModel = carModel.trim(),
                                carNo = carNo.trim(),
                                dept = dept.trim(),
                                name = name.trim(),
                                consent = true,
                            )
                        )
                    }
                }
            }
        }
    }
}

// 상단 단계 표시: 지난 단계는 ✓, 현재 단계는 굵게
@Composable
private fun StepBar(step: Int, colors: AppColors) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf("데이터 이용 동의", "사업자·차량 정보", "사용자 정보").forEachIndexed { i, label ->
            val n = i + 1
            if (i > 0) Box(Modifier.width(48.dp).height(2.dp).background(colors.line))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(if (n <= step) colors.accent else colors.bg)
                        .border(2.dp, if (n <= step) colors.accent else colors.line, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (n < step) "✓" else "$n", color = if (n <= step) Color.White else colors.sub, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    label, color = if (n == step) colors.text else colors.sub, fontSize = 22.sp,
                    fontWeight = if (n == step) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun Title(title: String, desc: String, colors: AppColors) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = colors.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(desc, color = colors.sub, fontSize = 24.sp)
    }
}

@Composable
private fun DataRow(title: String, desc: String, colors: AppColors, divider: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 32.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = colors.sub, fontSize = 20.sp)
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
    }
}

// 입력 칸: 제목 + 입력 상자 + 안내 문구 (안내 문구 자리는 비어 있어도 높이를 유지해 화면이 흔들리지 않게)
@Composable
private fun Field(
    label: String, value: String, hint: String, colors: AppColors, modifier: Modifier,
    number: Boolean = false, message: String? = null, onChange: (String) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = colors.sub, fontSize = 22.sp)
        HangulTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(color = colors.text, fontSize = 28.sp),
            cursorBrush = SolidColor(colors.accent),
            colors = colors,
            hangul = !number,
            keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(18.dp)).background(colors.surface)
                        .border(if (message != null) 2.dp else 1.dp, if (message != null) colors.warn else colors.line, RoundedCornerShape(18.dp))
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) Text(hint, color = colors.personal, fontSize = 26.sp)
                    inner()
                }
            }
        )
        Text(message ?: " ", color = colors.warnText, fontSize = 20.sp)
    }
}

@Composable
private fun PrimaryButton(text: String, colors: AppColors, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(84.dp).clip(RoundedCornerShape(22.dp))
            .background(if (enabled) colors.accent else colors.track)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) Color.White else colors.sub, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Preview(name = "최초 설정", widthDp = 1200, heightDp = 840)
@Composable
fun SetupPreview() {
    SetupScreen(DarkColors, Profile(), onDone = {}) {}
}