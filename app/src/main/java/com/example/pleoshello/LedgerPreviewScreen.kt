package com.example.pleoshello

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// 엑셀 미리보기: 저장되는 엑셀 파일과 같은 모양의 장부를 앱에서 바로 확인 (차량에는 엑셀 프로그램이 없으므로)
// 문서처럼 보이도록 테마와 상관없이 흰 종이 색으로 표시 (색은 엑셀 파일의 서식과 같음)
private val Paper = Color.White
private val Ink = Color(0xFF1F2328)
private val InkSub = Color(0xFF59636E)
private val Grid = Color(0xFF8C959F)
private val HeadFill = Color(0xFFE7ECF2)
private val TotalFill = Color(0xFFDDF4FF)
private val WarnFill = Color(0xFFFFF1E5)
private val WarnInk = Color(0xFFBC4C00)

// 열 너비 비율 (엑셀 파일의 A~I열 너비와 같음, EW = 수정 이력 시트의 A~F열)
private val W = listOf(15f, 11f, 10f, 15f, 15f, 12f, 12f, 12f, 26f)
private val EW = listOf(20f, 16f, 10f, 9f, 34f, 34f)
private val SHEET_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd(E)", Locale.KOREAN)

@Composable
fun LedgerPreviewScreen(colors: AppColors, log: MonthlyLog, p: Profile, resultText: String, onSave: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(colors.bg).padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                PillButton(AppIcons.Back, "내보내기", colors, onClick = onBack)
                Column {
                    Text("엑셀 미리보기", color = colors.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("저장되는 엑셀 파일과 같은 내용입니다", color = colors.sub, fontSize = 18.sp)
                }
            }
            PillButton(AppIcons.Save, "엑셀 저장", colors, onClick = onSave)
        }
        if (resultText.isNotEmpty()) Text(resultText, color = colors.body, fontSize = 20.sp)

        // 종이 한 장
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Paper)
                .verticalScroll(rememberScrollState()).padding(horizontal = 36.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("업무용승용차 운행기록부", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("국세청 업무용승용차 운행기록부 서식 항목 기준", color = InkSub, fontSize = 14.sp)
                Text("${log.periodLabel}: ${log.period}", color = InkSub, fontSize = 14.sp)
            }

            SectionTitle("1. 기본정보")
            Column {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Cell("상호명", W[0], HeadFill, bold = true)
                    Cell(info(p.company), W[1] + W[2] + W[3], align = TextAlign.Center)
                    Cell("사업자등록번호", W[4] + W[5], HeadFill, bold = true)
                    Cell(info(p.bizNo), W[6] + W[7] + W[8], align = TextAlign.Center)
                }
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Cell("① 차종", W[0], HeadFill, bold = true)
                    Cell(info(p.carModel), W[1] + W[2] + W[3], align = TextAlign.Center)
                    Cell("② 자동차등록번호", W[4] + W[5], HeadFill, bold = true)
                    Cell(info(p.carNo), W[6] + W[7] + W[8], align = TextAlign.Center)
                }
            }

            SectionTitle("2. 업무용 사용비율 계산")
            Column {
                // 머리글 2줄 (묶음 칸 + 세부 칸)
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Cell("③ 사용일자\n(요일)", W[0], HeadFill, bold = true)
                    HeaderGroup("④ 사용자", listOf("부서", "성명"), W.subList(1, 3))
                    HeaderGroup("운행내역", listOf("⑤ 주행 전\n계기판의 거리(km)", "⑥ 주행 후\n계기판의 거리(km)", "⑦ 주행거리(km)"), W.subList(3, 6))
                    HeaderGroup("업무용 사용거리(km)", listOf("⑧ 출·퇴근용(km)", "⑨ 일반 업무용(km)"), W.subList(6, 8))
                    Cell("⑩ 비고", W[8], HeadFill, bold = true)
                }
                if (log.trips.isEmpty()) {
                    Row(Modifier.height(IntrinsicSize.Min)) { Cell("이 기간의 운행 기록이 없습니다", W.sum()) }
                }
                log.trips.forEach { t ->
                    val warn = t.needsCheck() || t.purpose.isEmpty()
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Cell(sheetDate(t.date), W[0])
                        Cell(t.driverDept(p), W[1])
                        Cell(t.driverName(p), W[2])
                        Cell(num(t.startKm.toDouble()), W[3], align = TextAlign.Center)
                        Cell(num(t.endKm.toDouble()), W[4], align = TextAlign.Center)
                        Cell(num(t.distanceKm.toDouble()), W[5], align = TextAlign.Center)
                        Cell(if (t.purpose == "출퇴근") num(t.distanceKm.toDouble()) else "", W[6], align = TextAlign.Center)
                        Cell(if (t.purpose == "일반 업무") num(t.distanceKm.toDouble()) else "", W[7], align = TextAlign.Center)
                        Cell(remarkOf(t), W[8], if (warn) WarnFill else Paper, bold = warn, align = TextAlign.Center, ink = if (warn) WarnInk else Ink)
                    }
                }
            }

            // 합계 (엑셀 파일에서는 수식으로 계산)
            val (totalLabel, businessLabel, ratioLabel) = log.totalLabels
            Row(Modifier.height(IntrinsicSize.Min)) {
                Cell(totalLabel, W[0] + W[1], TotalFill, bold = true)
                Cell(num(log.totalKm), W[2], TotalFill, bold = true, align = TextAlign.Center)
                Cell(businessLabel, W[3] + W[4], TotalFill, bold = true)
                Cell(num(log.businessKm), W[5], TotalFill, bold = true, align = TextAlign.Center)
                Cell(ratioLabel, W[6] + W[7], TotalFill, bold = true)
                Cell(String.format(Locale.US, "%.1f%%", log.ratio), W[8], TotalFill, bold = true, align = TextAlign.Center)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (ledgerNotes(log) + "차량 계기판 누적 주행거리를 자동 기록해 작성 (위치 정보 미수집)").forEach {
                    Text(it, color = InkSub, fontSize = 13.sp)
                }
            }

            // 엑셀 파일의 2번 시트와 같은 내용
            SectionTitle("수정 이력 (엑셀 파일의 '수정 이력' 시트)")
            Column {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    listOf("수정 일시", "운행 사용일자", "출발 시각", "항목", "수정 전", "수정 후").forEachIndexed { i, h -> Cell(h, EW[i], HeadFill, bold = true) }
                }
                if (log.edits.isEmpty()) {
                    Row(Modifier.height(IntrinsicSize.Min)) { Cell("이 기간의 수정 이력이 없습니다", EW.sum()) }
                }
                log.edits.forEach { e ->
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Cell(e.at, EW[0])
                        Cell(sheetDate(e.tripDate), EW[1])
                        Cell(e.tripStart.take(5), EW[2])
                        Cell(e.field, EW[3])
                        Cell(e.before, EW[4])
                        Cell(e.after, EW[5])
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
}

// 표의 칸 하나: 같은 줄의 칸은 높이를 맞추고 테두리를 그림
@Composable
private fun RowScope.Cell(
    text: String, weight: Float, fill: Color = Paper, bold: Boolean = false,
    align: TextAlign = TextAlign.Center, ink: Color = Ink, size: Int = 14,
) {
    Box(
        Modifier.weight(weight).fillMaxHeight().background(fill).border(0.5.dp, Grid)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = when (align) {
            TextAlign.Start -> Alignment.CenterStart
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        Text(text, color = ink, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textAlign = align)
    }
}

// 묶음 머리글: 위 칸(묶음 이름) + 아래 세부 칸들
@Composable
private fun RowScope.HeaderGroup(title: String, subs: List<String>, weights: List<Float>) {
    Column(Modifier.weight(weights.sum()).fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) { Cell(title, 1f, HeadFill, bold = true) }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            subs.forEachIndexed { i, s -> Cell(s, weights[i], HeadFill, bold = true, size = 13) }
        }
    }
}

private fun info(v: String) = v.ifBlank { "(미입력)" }
private fun num(v: Double) = String.format(Locale.KOREA, "%,.1f", v)
private fun sheetDate(s: String) = runCatching { LocalDate.parse(s).format(SHEET_DATE) }.getOrDefault(s)

@Preview(name = "엑셀 미리보기", widthDp = 1200, heightDp = 840)
@Composable
fun LedgerPreview() {
    val sample = listOf(
        Trip("2026-10-21", "08:12:00", "08:47:00", 48182.2f, 48196.0f, 13.8f, "", "출퇴근"),
        Trip("2026-10-21", "10:05:00", "10:41:00", 48196.0f, 48213.6f, 17.6f, "", "일반 업무"),
        Trip("2026-10-22", "14:02:00", "14:41:00", 48213.6f, 48242.5f, 28.9f, "확인 완료", "개인"),
    )
    val edits = listOf(EditEntry("2026-10-23 09:10:05", "2026-10-22", "14:02:00", "비고", "확인 필요", "확인 완료"))
    LedgerPreviewScreen(
        DarkColors, fiscalYearLog(sample, LocalDate.of(2026, 10, 22), 1, edits),
        Profile("Tech1,판교점", "335-18-02175", "아반떼", "12가4567", "영업팀", "강지민", true), "", onSave = {}
    ) {}
}
