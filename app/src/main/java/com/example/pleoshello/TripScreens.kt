package com.example.pleoshello

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// 주행 중 화면: 입력 없이 큰 글씨만 (시스템이 주행 중 화면을 허용할 때만 보임)
@Composable
fun DrivingScreen(colors: AppColors, distanceText: String, startText: String) {
    Column(
        modifier = Modifier.fillMaxSize().background(colors.bg).padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically)
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(28.dp)).background(colors.surface)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(colors.ok))
            Text("운행 기록 중", color = colors.ok, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Text(distanceText, color = colors.text, fontSize = 140.sp, fontWeight = FontWeight.Bold)
        Text(startText, color = colors.sub, fontSize = 30.sp)
    }
}

// 정차 후 화면: ① 운전자 이름 고르기 (미리 골라 두지 않음) → ② 목적 고르기 → ③ 기록 완료
// 시동 때 얼굴로 확인한 운전자(verified)가 있으면 그 이름을 골라야만 다음으로 넘어감 (다른 사람 이름으로 기록하는 것 방지)
// 얼굴 확인을 건너뛴 운행은 '본인 확인 안 됨', 3번 틀린 뒤 관리자 확인으로 넘긴 운행은 '본인 확인 불일치'로 비고에 남김
@Composable
fun PurposeScreen(
    colors: AppColors, summary: String, distanceText: String, flagged: Boolean,
    drivers: List<Driver>, verified: String?, faceActive: Boolean,
    onSave: (purpose: String, driver: Driver?, idNote: String) -> Unit, onHome: () -> Unit,
) {
    var step by remember { mutableStateOf(if (drivers.isEmpty()) 1 else 0) }  // 0 이름, 1 목적, 2 완료
    var chosen by remember { mutableStateOf<Driver?>(null) }
    var idNote by remember { mutableStateOf("") }
    var mismatches by remember { mutableStateOf(0) }
    var purposeDone by remember { mutableStateOf("") }
    fun save(purpose: String, d: Driver?, note: String) {
        onSave(purpose, d, note)
        purposeDone = purpose.ifEmpty { "미분류" }
        chosen = d
        idNote = note
        step = 2
    }
    if (step == 2) LaunchedEffect(Unit) {
        delay(5000)
        onHome()
    }
    Column(
        modifier = Modifier.fillMaxSize().background(colors.bg).padding(horizontal = 56.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (step == 2) "기록 완료" else "운행이 끝났어요", color = colors.text, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                Text(summary, color = colors.body, fontSize = 26.sp)
                if (flagged) Text("확인 필요: 평균 속도가 비정상입니다", color = colors.warnText, fontSize = 22.sp)
            }
            Text(distanceText, color = colors.text, fontSize = 96.sp, fontWeight = FontWeight.Bold)
        }
        when (step) {
            0 -> {
                Text("운전한 분의 이름을 눌러 주세요", color = colors.text, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                if (mismatches > 0) Text(
                    "정보가 맞지 않습니다. 다시 시도해 주세요" + if (mismatches >= 3) " · 계속 맞지 않으면 관리자 확인으로 넘기세요" else "",
                    color = colors.warnText, fontSize = 24.sp, fontWeight = FontWeight.Bold
                )
                if (faceActive && verified == null) Text("시동 때 얼굴 확인을 하지 않아 관리자 확인 대상으로 기록됩니다", color = colors.sub, fontSize = 20.sp)
                DriverPicker(colors, drivers, null, Modifier.weight(1f).verticalScroll(rememberScrollState())) { d ->
                    when {
                        !faceActive -> { chosen = d; idNote = ""; step = 1 }
                        verified == null -> { chosen = d; idNote = "본인 확인 안 됨"; step = 1 }
                        verified == d.name -> { chosen = d; idNote = ""; step = 1 }
                        else -> mismatches++
                    }
                }
                if (mismatches >= 3) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PillButton(AppIcons.Lock, "관리자 확인 필요로 저장", colors, tint = colors.warnText) {
                        save("", null, "본인 확인 불일치 (얼굴 확인: $verified)")
                    }
                }
            }
            1 -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(AppIcons.Person, null, tint = colors.accentText, modifier = Modifier.size(32.dp))
                    Text(chosen?.label ?: "운전자 명단 없음", color = colors.body, fontSize = 26.sp)
                    if (chosen != null && faceActive && idNote.isEmpty()) Icon(AppIcons.Check, "얼굴 확인됨", tint = colors.ok, modifier = Modifier.size(30.dp))
                }
                Text("이번 운행의 목적을 골라 주세요", color = colors.text, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    PurposeButton(AppIcons.Commute, "출퇴근", "집 ↔ 회사", colors, Modifier.weight(1f)) { save("출퇴근", chosen, idNote) }
                    PurposeButton(AppIcons.Work, "일반 업무", "거래처 방문, 회의, 배송", colors, Modifier.weight(1f)) { save("일반 업무", chosen, idNote) }
                    PurposeButton(AppIcons.Person, "개인", "업무와 무관한 운행", colors, Modifier.weight(1f)) { save("개인", chosen, idNote) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PillButton(AppIcons.Clock, "나중에 분류", colors) { save("", chosen, idNote) }
                }
            }
            else -> {
                Column(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(28.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                ) {
                    Icon(AppIcons.Check, null, tint = colors.ok, modifier = Modifier.size(96.dp))
                    Text("운행기록부에 저장했습니다", color = colors.text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text("운전자 ${chosen?.label ?: "미지정"} · 목적 $purposeDone", color = colors.body, fontSize = 26.sp)
                    if (idNote.isNotEmpty()) Text("비고: $idNote (관리자 확인 대상)", color = colors.warnText, fontSize = 22.sp)
                    PillButton(AppIcons.Home, "홈", colors, onClick = onHome)
                }
            }
        }
    }
}

@Composable
fun PurposeButton(icon: ImageVector, title: String, desc: String, colors: AppColors, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(colors.surface)
            .border(2.dp, colors.line, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(64.dp))
        Text(title, color = colors.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(desc, color = colors.sub, fontSize = 22.sp)
    }
}

// 운전자 고르기: 직원 명단을 큰 버튼으로 3개씩 배치 (선택된 사람은 파란 테두리)
// 스크롤은 감싸는 화면이 정함 (스크롤 화면 안에 또 스크롤을 넣으면 앱이 멈추기 때문)
@Composable
fun DriverPicker(colors: AppColors, drivers: List<Driver>, selected: Driver?, modifier: Modifier = Modifier, onPick: (Driver) -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        drivers.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { d ->
                    val on = d == selected
                    Column(
                        Modifier.weight(1f).height(108.dp).clip(RoundedCornerShape(22.dp)).background(colors.surface)
                            .border(if (on) 3.dp else 1.dp, if (on) colors.accent else colors.line, RoundedCornerShape(22.dp))
                            .clickable { onPick(d) }.padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                    ) {
                        Text(d.name, color = colors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(d.dept.ifBlank { "부서 없음" }, color = colors.sub, fontSize = 20.sp, maxLines = 1)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Preview(name = "운행 중", widthDp = 1200, heightDp = 840)
@Composable
fun DrivingPreview() {
    DrivingScreen(DarkColors, "12.3 km", "출발 14:02 · 출발 시 48,213.6 km")
}

@Preview(name = "목적 선택", widthDp = 1200, heightDp = 840)
@Composable
fun PurposePreview() {
    val drivers = listOf(Driver("강지민", "영업팀"), Driver("김하늘", "총무팀"), Driver("이도윤", ""))
    PurposeScreen(DarkColors, "14:02 → 14:41 · 39분 0초", "28.9 km", false, drivers, "강지민", true, onSave = { _, _, _ -> }, onHome = {})
}