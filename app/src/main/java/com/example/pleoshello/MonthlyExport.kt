package com.example.pleoshello

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// 운행기록부: 기간(이번 달 또는 사업연도)의 운행만 골라 국세청 서식 항목 순서로 정리
data class MonthlyLog(
    val title: String,          // "2026년 10월 운행기록부" / "2026년 사업연도 운행기록부"
    val period: String,         // "2026-10-01 ~ 2026-10-31"
    val trips: List<Trip>,
    val totalKm: Double,
    val businessKm: Double,
    val flagged: Int,
    val unclassified: Int,
    val annual: Boolean = false,               // true = 사업연도 장부 (세법상 작성·보관 단위)
    val edits: List<EditEntry> = emptyList(),  // 이 장부에 들어간 운행의 수정 이력
) {
    val ratio: Double get() = if (totalKm > 0) businessKm / totalKm * 100 else 0.0

    // 저장 파일 이름: 월별 logbook_2026-10, 사업연도 logbook_FY2026
    val fileBase: String get() = if (annual) "logbook_FY${period.take(4)}" else "logbook_${period.take(7)}"
    val periodLabel: String get() = if (annual) "사업연도" else "기간"

    // 합계 칸 이름: 사업연도 장부는 서식 그대로 "사업연도 총주행거리"
    val totalLabels: List<String>
        get() = if (annual) listOf("⑪ 사업연도\n총주행거리(km)", "⑫ 사업연도\n업무용 사용거리(km)", "⑬ 업무사용비율(⑫/⑪)")
        else listOf("⑪ 총주행거리(km)", "⑫ 업무용 사용거리(km)", "⑬ 업무사용비율(⑫/⑪)")
}

private fun ledgerOf(all: List<Trip>, edits: List<EditEntry>, title: String, first: LocalDate, last: LocalDate, annual: Boolean): MonthlyLog {
    // 장부는 사용일자·출발 시각 순서 (사업연도 장부는 1년 치가 한 표에 들어가므로 순서를 보장)
    val trips = all.filter { t ->
        val d = runCatching { LocalDate.parse(t.date) }.getOrNull()
        d != null && !d.isBefore(first) && !d.isAfter(last)
    }.sortedWith(compareBy({ it.date }, { it.start }))
    return MonthlyLog(
        title = title,
        period = "$first ~ $last",
        trips = trips,
        totalKm = trips.sumOf { it.distanceKm.toDouble() },
        businessKm = trips.filter { it.purpose == "출퇴근" || it.purpose == "일반 업무" }.sumOf { it.distanceKm.toDouble() },
        flagged = trips.count { it.needsCheck() },
        unclassified = trips.count { it.purpose.isEmpty() },
        annual = annual,
        edits = editsFor(trips, edits),
    )
}

fun monthlyLog(all: List<Trip>, today: LocalDate, edits: List<EditEntry> = emptyList()): MonthlyLog =
    ledgerOf(all, edits, "${today.year}년 ${today.monthValue}월 운행기록부", today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()), annual = false)

// 오늘이 속한 사업연도의 첫날 (시작 월이 4월이면 2026-10-24 → 2026-04-01, 2026-02-10 → 2025-04-01)
fun fiscalYearStart(today: LocalDate, startMonth: Int): LocalDate {
    val start = LocalDate.of(today.year, startMonth.coerceIn(1, 12), 1)
    return if (today.isBefore(start)) start.minusYears(1) else start
}

// 사업연도 운행기록부: 사업연도 첫날 ~ 마지막 날 (국세청 서식의 ⑪⑫⑬은 사업연도 합계)
fun fiscalYearLog(all: List<Trip>, today: LocalDate, startMonth: Int, edits: List<EditEntry> = emptyList()): MonthlyLog {
    val first = fiscalYearStart(today, startMonth)
    val last = first.plusYears(1).minusDays(1)
    val title = if (first.year == last.year) "${first.year}년 사업연도 운행기록부" else "${first.year}~${last.year} 사업연도 운행기록부"
    return ledgerOf(all, edits, title, first, last, annual = true)
}

// 장부 아래 안내 문구 (엑셀·미리보기 공통)
fun ledgerNotes(log: MonthlyLog): List<String> = listOf(
    "확인 필요 ${log.flagged}건 · 미분류 ${log.unclassified}건 · 수정 이력 ${log.edits.size}건 (수정 이력 시트 참고)",
    "비고: 확인 필요 = 평균 속도 비정상 · 종료 시각 추정 = 앱 종료 중 도착(시각 추정) · 확인 완료 = 담당자 검토 끝 · 미분류 = 목적 미선택",
    "업무사용비율 = ⑫ 업무용 사용거리(⑧ 출·퇴근용 + ⑨ 일반 업무용) ÷ ⑪ 총주행거리",
)

private fun km(v: Float) = String.format(Locale.US, "%.1f", v)
private fun km(v: Double) = String.format(Locale.US, "%.1f", v)
private val CSV_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd(E)", Locale.KOREAN)
private val SHORT_DATE = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN)
private fun dateShort(s: String) = runCatching { LocalDate.parse(s).format(SHORT_DATE) }.getOrDefault(s)

// 비고: 확인 필요 / 미분류 / 개인
fun remarkOf(t: Trip): String = listOfNotNull(
    t.flag.ifEmpty { null },
    if (t.purpose.isEmpty()) "미분류" else null,
    if (t.purpose == "개인") "개인" else null,
).joinToString(" / ")

// CSV 칸 하나: 쉼표·따옴표·줄바꿈이 있으면 따옴표로 감싸고, 안쪽 따옴표는 두 번 씀
fun csvCell(s: String): String =
    if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

// CSV 내용 만들기 (엑셀에서 한글이 깨지지 않도록 맨 앞에 BOM, 사용자가 입력한 값은 csvCell로 감쌈)
fun buildCsv(log: MonthlyLog, p: Profile): String {
    fun info(v: String) = csvCell(v.ifBlank { "(미입력)" })
    val sb = StringBuilder("\uFEFF")
    sb.append("업무용승용차 운행기록부\n")
    sb.append("${log.periodLabel},${log.period}\n")
    sb.append("상호명,${info(p.company)}\n")
    sb.append("사업자등록번호,${info(p.bizNo)}\n")
    sb.append("차종,${info(p.carModel)}\n")
    sb.append("자동차등록번호,${info(p.carNo)}\n\n")
    sb.append("사용일자,부서,성명,주행 전 계기판 거리(km),주행 후 계기판 거리(km),주행거리(km),출퇴근용(km),일반 업무용(km),비고\n")
    log.trips.forEach { t ->
        val date = runCatching { LocalDate.parse(t.date).format(CSV_DATE) }.getOrDefault(t.date)
        val commute = if (t.purpose == "출퇴근") km(t.distanceKm) else ""
        val work = if (t.purpose == "일반 업무") km(t.distanceKm) else ""
        sb.append(listOf(date, t.driverDept(p), t.driverName(p), km(t.startKm), km(t.endKm), km(t.distanceKm), commute, work, remarkOf(t)).joinToString(",") { csvCell(it) })
        sb.append("\n")
    }
    sb.append("\n")
    val fy = if (log.annual) "사업연도 " else ""
    sb.append("${fy}총주행거리(km),${km(log.totalKm)}\n")
    sb.append("${fy}업무용 사용거리(km),${km(log.businessKm)}\n")
    sb.append("업무사용비율(%),${String.format(Locale.US, "%.1f", log.ratio)}\n")
    sb.append("확인 필요,${log.flagged}건\n")
    sb.append("미분류,${log.unclassified}건\n")
    // 수정 이력: 목적·운전자·비고를 바꾼 기록 (거리·시각은 수정 불가)
    sb.append("\n수정 이력,${log.edits.size}건\n")
    sb.append("수정 일시,사용일자,출발 시각,항목,수정 전,수정 후\n")
    log.edits.forEach { e ->
        sb.append(listOf(e.at, e.tripDate, e.tripStart.take(5), e.field, e.before, e.after).joinToString(",") { csvCell(it) })
        sb.append("\n")
    }
    return sb.toString()
}

// 운행기록부(이번 달 또는 사업연도)를 엑셀(xlsx)과 CSV로 저장하고 결과 문구를 돌려줌 (엑셀: 사람이 보고 출력하는 장부, CSV: 세무 프로그램 입력용)
fun saveLedgerFiles(dir: File, log: MonthlyLog, p: Profile): String {
    val now = LocalDateTime.now(MainActivity.KST)
    val base = log.fileBase
    return runCatching {
        File(dir, "$base.xlsx").outputStream().use { LedgerXlsx.write(it, log, p, now.format(CREATED_FMT)) }
        File(dir, "$base.csv").writeText(buildCsv(log, p))
    }.fold(
        onSuccess = { "저장 완료: $base.xlsx + CSV (운행 ${log.trips.size}건 · 수정 이력 ${log.edits.size}건 · ${now.format(SAVED_FMT)})" },
        onFailure = { "저장 실패: ${it.message}" }
    )
}

private val CREATED_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd a h:mm", Locale.KOREAN)
private val SAVED_FMT = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

// 내보내기 화면: 장부 종류(이번 달 / 사업연도) + 요약 + 운행 표 미리보기 + 엑셀 확인·저장
@Composable
fun ExportScreen(
    colors: AppColors, log: MonthlyLog, resultText: String,
    fyStartMonth: Int = 1, onAnnual: (Boolean) -> Unit = {}, onStartMonth: (Int) -> Unit = {},
    onSave: () -> Unit, onPreview: () -> Unit, onBack: () -> Unit,
) {
    var pickingMonth by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(colors.bg).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                PillButton(AppIcons.Back, "홈", colors, onClick = onBack)
                Column {
                    Text(log.title, color = colors.text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text("${log.period} · 운행 ${log.trips.size}건 · 수정 이력 ${log.edits.size}건", color = colors.sub, fontSize = 20.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillButton(AppIcons.Preview, "엑셀 확인", colors, onClick = onPreview)
                PillButton(AppIcons.Save, "엑셀 저장", colors, onClick = onSave)
            }
        }
        // 장부 종류: 세법상 운행기록부는 사업연도 단위로 작성·보관 (이번 달은 중간 점검용)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SegmentTabs(listOf("이번 달", "사업연도"), if (log.annual) 1 else 0, colors) {
                pickingMonth = false
                onAnnual(it == 1)
            }
            if (log.annual) PillButton("사업연도 시작 ${fyStartMonth}월  " + if (pickingMonth) "▴" else "▾", colors) { pickingMonth = !pickingMonth }
        }
        if (log.annual && pickingMonth) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..12).forEach { m ->
                    val on = m == fyStartMonth
                    Box(
                        Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(16.dp)).background(if (on) colors.accent else colors.surface)
                            .border(1.dp, if (on) colors.accent else colors.line, RoundedCornerShape(16.dp))
                            .clickable {
                                pickingMonth = false
                                onStartMonth(m)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${m}월", color = if (on) Color.White else colors.text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SummaryBox("총주행거리", "${km(log.totalKm)} km", colors, Modifier.weight(1f))
            SummaryBox("업무용 사용거리", "${km(log.businessKm)} km", colors, Modifier.weight(1f))
            SummaryBox("업무사용비율", if (log.totalKm > 0) "${String.format(Locale.US, "%.1f", log.ratio)} %" else "- %", colors, Modifier.weight(1f))
        }
        if (log.flagged > 0 || log.unclassified > 0) {
            Text("확인 필요 ${log.flagged}건 · 미분류 ${log.unclassified}건 → 분류하면 업무사용비율이 바뀔 수 있어요", color = colors.warnText, fontSize = 22.sp)
        }
        // 운행 표 미리보기
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
                .border(1.dp, colors.line, RoundedCornerShape(20.dp)).verticalScroll(rememberScrollState())
        ) {
            LogRow(listOf("사용일자", "주행 전", "주행 후", "주행거리", "출퇴근", "일반 업무", "비고"), colors, header = true)
            log.trips.forEach { t ->
                LogRow(
                    listOf(
                        dateShort(t.date), km(t.startKm), km(t.endKm), km(t.distanceKm),
                        if (t.purpose == "출퇴근") km(t.distanceKm) else "–",
                        if (t.purpose == "일반 업무") km(t.distanceKm) else "–",
                        remarkOf(t)
                    ),
                    colors, header = false
                )
            }
        }
        if (resultText.isNotEmpty()) Text(resultText, color = colors.body, fontSize = 22.sp)
    }
}

// 두세 개 중 하나를 고르는 탭 (홈 화면의 기간 탭과 같은 모양)
@Composable
private fun SegmentTabs(labels: List<String>, selected: Int, colors: AppColors, onSelect: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(38.dp)).background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(38.dp)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.height(64.dp).clip(RoundedCornerShape(32.dp))
                    .background(if (on) colors.accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) Color.White else colors.sub, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SummaryBox(label: String, value: String, colors: AppColors, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(20.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, color = colors.sub, fontSize = 20.sp)
        Text(value, color = colors.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LogRow(cells: List<String>, colors: AppColors, header: Boolean) {
    val weights = listOf(1.3f, 1.1f, 1.1f, 1f, 0.9f, 0.9f, 1.6f)
    Row(
        Modifier.fillMaxWidth().background(if (header) colors.track else colors.surface)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        cells.forEachIndexed { i, c ->
            Text(
                c, color = if (header) colors.sub else colors.text, fontSize = 20.sp,
                fontWeight = if (header) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.weight(weights[i]), maxLines = 1
            )
        }
    }
}

@Preview(name = "내보내기 (사업연도)", widthDp = 1200, heightDp = 840)
@Composable
fun ExportPreview() {
    val sample = listOf(
        Trip("2026-10-22", "08:12:00", "08:47:00", 48182.2f, 48196.0f, 13.8f, "", "출퇴근"),
        Trip("2026-10-22", "10:05:00", "10:41:00", 48196.0f, 48213.6f, 17.6f, "", "일반 업무"),
        Trip("2026-10-22", "14:02:00", "14:41:00", 48213.6f, 48242.5f, 28.9f, "", ""),
    )
    ExportScreen(DarkColors, fiscalYearLog(sample, LocalDate.of(2026, 10, 22), 1), "", onSave = {}, onPreview = {}) {}
}
