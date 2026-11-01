package com.example.pleoshello

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LIST_DAY = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
private val LIST_TIME = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
private val PURPOSES = listOf("출퇴근", "일반 업무", "개인")

// 운행 목록: 날짜별로 최신순. 미분류 운행은 주황 테두리, 누르면 목적·운전자 수정 (수정 이력도 함께 표시)
@Composable
fun TripListScreen(
    colors: AppColors, trips: List<Trip>, profile: Profile, drivers: List<Driver>, edits: List<EditEntry> = emptyList(),
    onSave: (Int, Trip) -> Unit, onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<Int?>(null) }
    var onlyUnclassified by remember { mutableStateOf(false) }
    val index = editing
    if (index != null && index in trips.indices) {
        val t = trips[index]
        TripEditPanel(colors, t, profile, drivers, edits.filter { it.matches(t) }, onCancel = { editing = null }) {
            onSave(index, it)
            editing = null
        }
        return
    }

    val unclassified = trips.count { it.purpose.isEmpty() }
    // 원래 순서(파일 위치)를 기억한 채로 최신순 정렬 후 날짜별로 묶음
    val groups = trips.withIndex()
        .filter { !onlyUnclassified || it.value.purpose.isEmpty() }
        .sortedWith(compareByDescending<IndexedValue<Trip>> { it.value.date }.thenByDescending { it.value.start })
        .groupBy { it.value.date }

    Column(
        Modifier.fillMaxSize().background(colors.bg).padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                PillButton(AppIcons.Back, "홈", colors, onClick = onBack)
                Column {
                    Text("운행 목록", color = colors.text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text("전체 ${trips.size}건 · 미분류 ${unclassified}건", color = if (unclassified > 0) colors.warnText else colors.sub, fontSize = 20.sp)
                }
            }
            PillButton(if (onlyUnclassified) AppIcons.Trips else AppIcons.Filter, if (onlyUnclassified) "전체 보기" else "미분류만 보기", colors) { onlyUnclassified = !onlyUnclassified }
        }
        if (groups.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (onlyUnclassified) "미분류 운행이 없습니다" else "아직 운행 기록이 없습니다. 기어를 D로 바꾸면 자동으로 기록돼요", color = colors.sub, fontSize = 24.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                groups.forEach { (date, list) ->
                    item(key = "day-$date") {
                        val km = list.sumOf { it.value.distanceKm.toDouble() }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(dayLabel(date), color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("${list.size}건 · ${fmt1(km)} km", color = colors.sub, fontSize = 22.sp)
                        }
                    }
                    items(list, key = { "trip-${it.index}" }) { (i, t) ->
                        TripCard(colors, t, profile, edits.count { it.matches(t) }) { editing = i }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripCard(colors: AppColors, t: Trip, profile: Profile, editCount: Int, onEdit: () -> Unit) {
    val unclassified = t.purpose.isEmpty()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
            .border(if (unclassified) 2.dp else 1.dp, if (unclassified) colors.warn else colors.line, RoundedCornerShape(20.dp))
            .clickable(onClick = onEdit).padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Column(Modifier.width(130.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(timeLabel(t.start), color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(timeLabel(t.end), color = colors.sub, fontSize = 20.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip(if (unclassified) "목적 선택 필요" else t.purpose, if (unclassified) colors.warnText else colors.accentText, colors)
                if (t.flag.isNotEmpty()) Chip(t.flag, if (t.needsCheck()) colors.warnText else colors.sub, colors)
                if (editCount > 0) Chip("수정 ${editCount}회", colors.sub, colors)
            }
            Text(
                "운전자 ${t.driverName(profile).ifBlank { "미지정" }}" + t.driverDept(profile).let { if (it.isBlank()) "" else " ($it)" } +
                    " · ${fmt1(t.startKm.toDouble())} → ${fmt1(t.endKm.toDouble())} km",
                color = colors.sub, fontSize = 20.sp, maxLines = 1
            )
        }
        Text("${fmt1(t.distanceKm.toDouble())} km", color = colors.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("수정 ›", color = colors.accentText, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Chip(text: String, ink: Color, colors: AppColors) {
    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(colors.track).padding(horizontal = 14.dp, vertical = 4.dp)) {
        Text(text, color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// 운행 1건 수정: 목적·운전자 변경 + 이상치 검토 (거리·시각은 차량이 기록한 원본이라 고정) + 지금까지의 수정 이력
@Composable
private fun TripEditPanel(
    colors: AppColors, t: Trip, profile: Profile, drivers: List<Driver>, history: List<EditEntry>,
    onCancel: () -> Unit, onSave: (Trip) -> Unit,
) {
    var purpose by remember { mutableStateOf(t.purpose) }
    var reviewed by remember { mutableStateOf(false) }
    var driver by remember {
        mutableStateOf(drivers.find { it.name == t.driverName(profile) } ?: t.driverName(profile).takeIf { it.isNotBlank() }?.let { Driver(it, t.driverDept(profile)) })
    }
    Column(
        Modifier.fillMaxSize().background(colors.bg).padding(horizontal = 48.dp, vertical = 36.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                PillButton(AppIcons.Back, "목록", colors, onClick = onCancel)
                Column {
                    Text("운행 수정", color = colors.text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text("${dayLabel(t.date)} ${timeLabel(t.start)} → ${timeLabel(t.end)}", color = colors.sub, fontSize = 20.sp)
                }
            }
            PillButton(AppIcons.Check, "저장", colors) {
                onSave(
                    t.copy(
                        purpose = purpose, name = driver?.name ?: t.name, dept = driver?.dept ?: t.dept,
                        flag = if (reviewed) reviewedFlag(t.flag) else t.flag
                    )
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
                .border(1.dp, colors.line, RoundedCornerShape(20.dp)).padding(horizontal = 28.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("주행 전 ${fmt1(t.startKm.toDouble())} km → 주행 후 ${fmt1(t.endKm.toDouble())} km · ${fmt1(t.distanceKm.toDouble())} km", color = colors.text, fontSize = 24.sp)
            Text("거리와 시각은 차량이 기록한 원본이라 수정할 수 없습니다", color = colors.sub, fontSize = 18.sp)
        }
        // 이상치 검토: 사람이 확인한 운행은 "확인 완료"로 바꿔 경고에서 뺌 (비고에는 기록으로 남음)
        if (t.needsCheck()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
                    .border(2.dp, if (reviewed) colors.line else colors.warn, RoundedCornerShape(20.dp))
                    .padding(start = 28.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (reviewed) reviewedFlag(t.flag) else t.flag, color = if (reviewed) colors.text else colors.warnText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("평균 속도가 비정상이거나 도착 시각을 추정한 운행입니다. 내용을 확인했다면 확인 완료로 바꾸세요", color = colors.sub, fontSize = 18.sp)
                }
                Box(
                    Modifier.height(76.dp).clip(RoundedCornerShape(20.dp)).background(if (reviewed) colors.accent else colors.surface)
                        .border(1.dp, if (reviewed) colors.accent else colors.line, RoundedCornerShape(20.dp))
                        .clickable { reviewed = !reviewed }.padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (reviewed) "✓ 확인 완료" else "확인 완료로 표시", color = if (reviewed) Color.White else colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("목적", color = colors.sub, fontSize = 22.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PURPOSES.forEach { p ->
                val on = p == purpose
                Box(
                    Modifier.weight(1f).height(88.dp).clip(RoundedCornerShape(22.dp)).background(if (on) colors.accent else colors.surface)
                        .border(1.dp, if (on) colors.accent else colors.line, RoundedCornerShape(22.dp))
                        .clickable { purpose = p },
                    contentAlignment = Alignment.Center
                ) {
                    Text(p, color = if (on) Color.White else colors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("운전자", color = colors.sub, fontSize = 22.sp)
        if (drivers.isEmpty()) {
            Text("직원 명단이 비어 있습니다 · 상태 화면 → 직원 명단 관리에서 등록", color = colors.sub, fontSize = 22.sp)
        } else {
            DriverPicker(colors, drivers, driver) { driver = it }
        }
        // 수정 이력: 저장할 때마다 바뀐 항목이 자동으로 남음 (지우거나 고칠 수 없음)
        if (history.isNotEmpty()) {
            Text("수정 이력 ${history.size}건", color = colors.sub, fontSize = 22.sp)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(20.dp)).padding(horizontal = 28.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                history.forEach { e ->
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(e.atShort(), color = colors.sub, fontSize = 20.sp, modifier = Modifier.width(170.dp))
                        Text("${e.field}: ${e.before} → ${e.after}", color = colors.text, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

private fun dayLabel(date: String) = runCatching { LocalDate.parse(date).format(LIST_DAY) }.getOrDefault(date)
private fun timeLabel(time: String) = runCatching { LocalTime.parse(time).format(LIST_TIME) }.getOrDefault(time)

@Preview(name = "운행 목록", widthDp = 1200, heightDp = 840)
@Composable
fun TripListPreview() {
    val trips = listOf(
        Trip("2026-10-22", "08:12:00", "08:47:00", 48182.2f, 48196.0f, 13.8f, "", "출퇴근", "영업팀", "강지민"),
        Trip("2026-10-22", "10:05:00", "10:41:00", 48196.0f, 48213.6f, 17.6f, "", "일반 업무", "총무팀", "김하늘"),
        Trip("2026-10-23", "14:02:00", "14:41:00", 48213.6f, 48242.5f, 28.9f, "확인 필요", ""),
    )
    TripListScreen(DarkColors, trips, Profile(name = "강지민", dept = "영업팀"), listOf(Driver("강지민", "영업팀"), Driver("김하늘", "총무팀")), onSave = { _, _ -> }) {}
}
