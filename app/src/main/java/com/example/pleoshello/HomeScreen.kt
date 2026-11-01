package com.example.pleoshello

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// 기간별 요약 (오늘 / 이번 달 / 올해)
data class PeriodSummary(
    val label: String, val period: String,
    val commute: Double, val work: Double, val personal: Double, val unclassified: Double, val trips: Int,
) {
    val business: Double get() = commute + work
    val total: Double get() = commute + work + personal + unclassified
}

// 미리보기용 예시 값
val sampleSummaries = listOf(
    PeriodSummary("오늘", "10월 22일 (목)", 13.8, 17.6, 0.0, 28.9, 3),
    PeriodSummary("이번 달", "10월 1일 ~ 10월 22일", 412.0, 620.0, 223.1, 28.9, 46),
    PeriodSummary("사업연도", "2026년 1월 1일 ~ 10월 22일", 3880.0, 5730.0, 2281.1, 28.9, 431),
)

fun fmt1(v: Double): String = String.format(Locale.KOREA, "%,.1f", v)

@Composable
fun HomeScreen(
    colors: AppColors,
    statusText: String,
    odometerText: String,
    summaries: List<PeriodSummary>,
    onOpenStatus: () -> Unit, onOpenExport: () -> Unit, onOpenTrips: () -> Unit = {}, unclassified: Int = 0,
    onToggleTheme: (() -> Unit)? = null,  // 시험용 테마 전환 (null이면 버튼 숨김, 차량은 주간·야간 모드를 따라감)
    onOpenSettings: () -> Unit = {},
    driverText: String? = null,  // 얼굴 확인한 운전자 이름 또는 "운전자 확인" (null 이면 얼굴 기능을 안 써서 숨김)
    onCheckDriver: () -> Unit = {},
) {
    var selected by remember { mutableStateOf(1) }
    val s = summaries[selected]

    Column(
        modifier = Modifier.fillMaxSize().background(colors.bg).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 머리말: 앱 이름 + 한 줄 상태 칩 (누르면 상태 상세 화면)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("운행일지", color = colors.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Row(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(24.dp))
                        .clickable(onClick = onOpenStatus)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(colors.ok))
                    Text("$statusText · 누적 $odometerText", color = colors.text, fontSize = 22.sp, maxLines = 1)
                    Text("자세히 ›", color = colors.accentText, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (onToggleTheme != null) PillButton("테마 전환", colors, onToggleTheme)
                if (driverText != null) PillButton(AppIcons.Person, driverText, colors, tint = if (driverText == "운전자 확인") colors.warnText else colors.ok, onClick = onCheckDriver)
                if (unclassified > 0) PillButton(AppIcons.Trips, "운행 목록 · 미분류 $unclassified", colors, tint = colors.warnText, onClick = onOpenTrips)
                else PillButton(AppIcons.Trips, "운행 목록", colors, onClick = onOpenTrips)
                PillButton(AppIcons.Export, "장부 내보내기", colors, onClick = onOpenExport)
                IconButtonBox(AppIcons.Settings, "설정", colors, onOpenSettings)
            }
        }

        // 기간 탭
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(42.dp)).background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(42.dp)).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                summaries.forEachIndexed { i, item ->
                    val on = i == selected
                    Box(
                        Modifier.height(72.dp).clip(RoundedCornerShape(36.dp))
                            .background(if (on) colors.accent else Color.Transparent)
                            .clickable { selected = i }
                            .padding(horizontal = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(item.label, color = if (on) Color.White else colors.sub, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Text(s.period, color = colors.sub, fontSize = 22.sp)
        }

        // 숫자 카드 2×2
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatCard("업무 주행", "출퇴근 + 일반 업무", fmt1(s.business), "km", colors, Modifier.weight(1f), highlight = true)
                StatCard("총 주행", "운행별 누적 주행거리 차이의 합", fmt1(s.total), "km", colors, Modifier.weight(1f))
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatCard("운행 횟수", "기어 P → D 시작 기준", String.format(Locale.KOREA, "%,d", s.trips), "회", colors, Modifier.weight(1f))
                StatCard(
                    "업무사용비율", "업무 주행 ÷ 총 주행",
                    if (s.total > 0) String.format(Locale.KOREA, "%.1f", s.business / s.total * 100) else "-",
                    "%", colors, Modifier.weight(1f)
                )
            }
        }

        CompositionBar(s, colors)
    }
}

@Composable
fun PillButton(text: String, colors: AppColors, onClick: () -> Unit) {
    Box(
        Modifier.height(76.dp).clip(RoundedCornerShape(20.dp)).background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}

// 아이콘 + 짧은 글자 버튼 (아이콘만 보고도 기능을 알 수 있게, 글자는 보조)
@Composable
fun PillButton(icon: ImageVector, text: String, colors: AppColors, tint: Color = colors.text, onClick: () -> Unit) {
    Row(
        Modifier.height(76.dp).clip(RoundedCornerShape(20.dp)).background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(start = 22.dp, end = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
        Text(text, color = tint, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}

// 아이콘만 있는 정사각 버튼 (설정 톱니바퀴처럼 누구나 아는 기능). 글자 대신 화면 읽기용 설명만 둠
@Composable
fun IconButtonBox(icon: ImageVector, description: String, colors: AppColors, onClick: () -> Unit) {
    Box(
        Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)).background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = colors.text, modifier = Modifier.size(36.dp))
    }
}

@Composable
fun StatCard(title: String, caption: String, value: String, unit: String, colors: AppColors, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(20.dp))
            .padding(horizontal = 28.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.alignByBaseline())
            Text(caption, color = colors.sub, fontSize = 20.sp, modifier = Modifier.alignByBaseline())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(value, color = if (highlight) colors.accentText else colors.text, fontSize = 76.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alignByBaseline())
            Text(unit, color = colors.sub, fontSize = 30.sp, modifier = Modifier.alignByBaseline())
        }
    }
}

@Composable
fun CompositionBar(s: PeriodSummary, colors: AppColors) {
    val parts = listOf(
        Triple("출퇴근", s.commute, colors.commute),
        Triple("일반 업무", s.work, colors.work),
        Triple("개인", s.personal, colors.personal),
        Triple("미분류", s.unclassified, colors.warn),
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(20.dp))
            .padding(horizontal = 28.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp)).background(colors.track)) {
            parts.filter { it.second > 0 }.forEach { (_, km, color) ->
                Box(Modifier.weight(km.toFloat()).fillMaxHeight().background(color))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            parts.forEach { (name, km, color) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(color))
                    Text("$name ${fmt1(km)} km", color = if (name == "미분류") colors.warnText else colors.body, fontSize = 20.sp)
                }
            }
        }
    }
}

@Preview(name = "홈 다크", widthDp = 1200, heightDp = 840)
@Composable
fun HomeDarkPreview() {
    HomeScreen(DarkColors, "대기 중", "48,242.5 km", sampleSummaries, onOpenStatus = {}, onOpenExport = {}) {}
}

@Preview(name = "홈 라이트", widthDp = 1200, heightDp = 840)
@Composable
fun HomeLightPreview() {
    HomeScreen(LightColors, "대기 중", "48,242.5 km", sampleSummaries, onOpenStatus = {}, onOpenExport = {}) {}
}
