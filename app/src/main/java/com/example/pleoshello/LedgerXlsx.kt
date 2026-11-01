package com.example.pleoshello

import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// 운행기록부(이번 달 또는 사업연도)를 엑셀(xlsx) 파일로 만들기 (외부 라이브러리 없이 엑셀 표준 형식을 직접 작성)
// 1번 시트: 국세청 업무용승용차 운행기록부 서식의 항목 순서 / 2번 시트: 수정 이력
object LedgerXlsx {
    // 셀 서식 번호 (아래 STYLES의 cellXfs 순서와 같음)
    private const val TITLE = 1
    private const val NOTE_RIGHT = 2
    private const val NOTE_LEFT = 3
    private const val SECTION = 4
    private const val LABEL = 5
    private const val VALUE = 6
    private const val HEADER = 7
    private const val CELL_CENTER = 8
    private const val CELL_NUM = 9
    private const val CELL_TEXT = 10
    private const val CELL_WARN = 11
    private const val TOTAL_LABEL = 12
    private const val TOTAL_NUM = 13
    private const val TOTAL_PCT = 14

    const val SHEET_NAME = "운행기록부"
    const val EDIT_SHEET_NAME = "수정 이력"
    private const val FIRST_DATA_ROW = 11
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd(E)", Locale.KOREAN)

    fun write(out: OutputStream, log: MonthlyLog, p: Profile, createdAt: String) {
        val sheet = buildSheet(log, p, createdAt)
        val editSheet = buildEditSheet(log)
        ZipOutputStream(out).use { zip ->
            fun put(name: String, xml: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", CONTENT_TYPES)
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", WORKBOOK)
            put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            put("xl/styles.xml", STYLES)
            put("xl/worksheets/sheet1.xml", sheet)
            put("xl/worksheets/sheet2.xml", editSheet)
        }
    }

    private fun buildSheet(log: MonthlyLog, p: Profile, createdAt: String): String {
        val s = Sheet()
        fun info(v: String) = v.ifBlank { "(미입력)" }

        // 제목과 기간
        s.text(0, 1, "업무용승용차 운행기록부", TITLE, toCol = 8)
        s.height(1, 34.0)
        s.text(0, 2, "국세청 업무용승용차 운행기록부 서식 항목 기준", NOTE_LEFT, toCol = 4)
        s.text(5, 2, "${log.periodLabel}: ${log.period}", NOTE_RIGHT, toCol = 8)
        s.height(3, 8.0)

        // 1. 기본정보
        s.text(0, 4, "1. 기본정보", SECTION, toCol = 3)
        s.text(0, 5, "상호명", LABEL)
        s.text(1, 5, info(p.company), VALUE, toCol = 3)
        s.text(4, 5, "사업자등록번호", LABEL, toCol = 5)
        s.text(6, 5, info(p.bizNo), VALUE, toCol = 8)
        s.text(0, 6, "① 차종", LABEL)
        s.text(1, 6, info(p.carModel), VALUE, toCol = 3)
        s.text(4, 6, "② 자동차등록번호", LABEL, toCol = 5)
        s.text(6, 6, info(p.carNo), VALUE, toCol = 8)
        s.height(5, 22.0)
        s.height(6, 22.0)
        s.height(7, 8.0)

        // 2. 업무용 사용비율 계산: 머리글 2줄
        s.text(0, 8, "2. 업무용 사용비율 계산", SECTION, toCol = 3)
        s.text(0, 9, "③ 사용일자\n(요일)", HEADER, toRow = 10)
        s.text(1, 9, "④ 사용자", HEADER, toCol = 2)
        s.text(3, 9, "운행내역", HEADER, toCol = 5)
        s.text(6, 9, "업무용 사용거리(km)", HEADER, toCol = 7)
        s.text(8, 9, "⑩ 비고", HEADER, toRow = 10)
        listOf("부서", "성명", "⑤ 주행 전\n계기판의 거리(km)", "⑥ 주행 후\n계기판의 거리(km)", "⑦ 주행거리(km)", "⑧ 출·퇴근용(km)", "⑨ 일반 업무용(km)")
            .forEachIndexed { i, label -> s.text(1 + i, 10, label, HEADER) }
        s.height(9, 22.0)
        s.height(10, 50.0)  // 실제 엑셀은 글자 폭이 넓어 "⑤ 주행 전 계기판의 거리(km)"가 3줄이 됨

        // 운행 한 줄 = 운행기록부 한 줄
        var row = FIRST_DATA_ROW
        if (log.trips.isEmpty()) {
            s.text(0, row, "이 기간의 운행 기록이 없습니다", CELL_CENTER, toCol = 8)
            s.height(row, 22.0)
            row++
        }
        log.trips.forEach { t ->
            val date = sheetDate(t.date)
            val remark = remarkOf(t)
            s.text(0, row, date, CELL_CENTER)
            s.text(1, row, t.driverDept(p), CELL_CENTER)
            s.text(2, row, t.driverName(p), CELL_CENTER)
            s.num(3, row, t.startKm.toDouble(), CELL_NUM)
            s.num(4, row, t.endKm.toDouble(), CELL_NUM)
            s.num(5, row, t.distanceKm.toDouble(), CELL_NUM)
            if (t.purpose == "출퇴근") s.num(6, row, t.distanceKm.toDouble(), CELL_NUM) else s.blank(6, row, CELL_NUM)
            if (t.purpose == "일반 업무") s.num(7, row, t.distanceKm.toDouble(), CELL_NUM) else s.blank(7, row, CELL_NUM)
            s.text(8, row, remark, if (t.needsCheck() || t.purpose.isEmpty()) CELL_WARN else CELL_TEXT)
            s.height(row, 20.0)
            row++
        }
        val lastDataRow = row - 1
        s.height(row, 8.0)
        row++

        // 합계: 수식으로 넣어 엑셀에서 값을 고쳐도 자동 계산 (열 때 보이는 값도 함께 저장)
        val totalRow = row
        val hasTrips = log.trips.isNotEmpty()
        val (totalLabel, businessLabel, ratioLabel) = log.totalLabels
        s.text(0, totalRow, totalLabel, TOTAL_LABEL, toCol = 1)
        s.formula(2, totalRow, if (hasTrips) "SUM(F$FIRST_DATA_ROW:F$lastDataRow)" else null, log.totalKm, TOTAL_NUM)
        s.text(3, totalRow, businessLabel, TOTAL_LABEL, toCol = 4)
        s.formula(5, totalRow, if (hasTrips) "SUM(G$FIRST_DATA_ROW:H$lastDataRow)" else null, log.businessKm, TOTAL_NUM)
        s.text(6, totalRow, ratioLabel, TOTAL_LABEL, toCol = 7)
        s.formula(8, totalRow, "IF(C$totalRow=0,0,F$totalRow/C$totalRow)", log.ratio / 100, TOTAL_PCT)
        s.height(totalRow, if (log.annual) 36.0 else 24.0)

        // 안내 문구
        val notes = ledgerNotes(log) + "생성 $createdAt · 차량 계기판 누적 주행거리를 자동 기록해 작성 (위치 정보 미수집)"
        notes.forEachIndexed { i, note -> s.text(0, totalRow + 2 + i, note, NOTE_LEFT, toCol = 8) }

        return s.toXml(widths = listOf(15.0, 11.0, 10.0, 15.0, 15.0, 12.0, 12.0, 12.0, 26.0), freezeBelowRow = if (log.trips.size > 20) 10 else 0)
    }

    // 2번 시트: 수정 이력 (언제, 어느 운행의, 어떤 항목을, 무엇에서 무엇으로 바꿨는지)
    private fun buildEditSheet(log: MonthlyLog): String {
        val s = Sheet()
        s.text(0, 1, "수정 이력", TITLE, toCol = 5)
        s.height(1, 34.0)
        s.text(0, 2, "운행기록부의 목적·운전자·비고를 바꾼 기록 (거리와 시각은 차량 원본이라 수정할 수 없음)", NOTE_LEFT, toCol = 4)
        s.text(5, 2, "${log.periodLabel}: ${log.period}", NOTE_RIGHT)
        s.height(3, 8.0)
        listOf("수정 일시", "운행 사용일자", "출발 시각", "항목", "수정 전", "수정 후").forEachIndexed { i, label -> s.text(i, 4, label, HEADER) }
        s.height(4, 24.0)
        var row = 5
        if (log.edits.isEmpty()) {
            s.text(0, row, "이 기간의 수정 이력이 없습니다", CELL_CENTER, toCol = 5)
            s.height(row, 22.0)
        }
        log.edits.forEach { e ->
            s.text(0, row, e.at, CELL_CENTER)
            s.text(1, row, sheetDate(e.tripDate), CELL_CENTER)
            s.text(2, row, e.tripStart.take(5), CELL_CENTER)
            s.text(3, row, e.field, CELL_CENTER)
            s.text(4, row, e.before, CELL_TEXT)
            s.text(5, row, e.after, CELL_TEXT)
            s.height(row, 20.0)
            row++
        }
        return s.toXml(widths = listOf(20.0, 16.0, 10.0, 9.0, 34.0, 34.0), freezeBelowRow = if (log.edits.size > 20) 4 else 0)
    }

    private fun sheetDate(date: String) = runCatching { LocalDate.parse(date).format(DATE) }.getOrDefault(date)

    // 셀을 모아 두었다가 행·열 순서대로 XML로 만듦
    private class Sheet {
        private val cells = sortedMapOf<Int, java.util.SortedMap<Int, String>>()
        private val heights = mutableMapOf<Int, Double>()
        private val merges = mutableListOf<String>()

        fun height(row: Int, h: Double) { heights[row] = h }

        fun text(col: Int, row: Int, value: String, style: Int, toCol: Int = col, toRow: Int = row) {
            area(col, row, toCol, toRow, style)
            put(col, row, "<c r=\"${ref(col, row)}\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(value)}</t></is></c>")
        }

        fun num(col: Int, row: Int, value: Double, style: Int) {
            put(col, row, "<c r=\"${ref(col, row)}\" s=\"$style\"><v>${String.format(Locale.US, "%.1f", value)}</v></c>")
        }

        fun formula(col: Int, row: Int, f: String?, cached: Double, style: Int) {
            val v = String.format(Locale.US, "%.4f", cached)
            put(col, row, "<c r=\"${ref(col, row)}\" s=\"$style\">" + (if (f != null) "<f>${esc(f)}</f>" else "") + "<v>$v</v></c>")
        }

        fun blank(col: Int, row: Int, style: Int) = put(col, row, "<c r=\"${ref(col, row)}\" s=\"$style\"/>")

        // 병합 영역: 테두리가 끊기지 않도록 가려지는 칸에도 같은 서식을 넣음
        private fun area(col: Int, row: Int, toCol: Int, toRow: Int, style: Int) {
            if (toCol == col && toRow == row) return
            merges.add("${ref(col, row)}:${ref(toCol, toRow)}")
            for (r in row..toRow) for (c in col..toCol) if (r != row || c != col) blank(c, r, style)
        }

        private fun put(col: Int, row: Int, xml: String) {
            cells.getOrPut(row) { sortedMapOf() }[col] = xml
        }

        fun toXml(widths: List<Double>, freezeBelowRow: Int): String {
            val sb = StringBuilder()
            sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
            sb.append("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>""")
            sb.append("""<sheetViews><sheetView workbookViewId="0" showGridLines="0">""")
            if (freezeBelowRow > 0) sb.append("""<pane ySplit="$freezeBelowRow" topLeftCell="A${freezeBelowRow + 1}" activePane="bottomLeft" state="frozen"/>""")
            sb.append("""</sheetView></sheetViews>""")
            sb.append("""<sheetFormatPr defaultRowHeight="18"/>""")
            sb.append("<cols>")
            widths.forEachIndexed { i, w -> sb.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
            sb.append("</cols><sheetData>")
            val rows = (cells.keys + heights.keys).toSortedSet()
            for (r in rows) {
                val h = heights[r]
                sb.append("<row r=\"$r\"" + (if (h != null) " ht=\"$h\" customHeight=\"1\"" else "") + ">")
                cells[r]?.values?.forEach { sb.append(it) }
                sb.append("</row>")
            }
            sb.append("</sheetData>")
            if (merges.isNotEmpty()) {
                sb.append("<mergeCells count=\"${merges.size}\">")
                merges.forEach { sb.append("<mergeCell ref=\"$it\"/>") }
                sb.append("</mergeCells>")
            }
            sb.append("""<printOptions horizontalCentered="1"/>""")
            sb.append("""<pageMargins left="0.4" right="0.4" top="0.6" bottom="0.6" header="0.3" footer="0.3"/>""")
            sb.append("""<pageSetup paperSize="9" orientation="landscape" fitToWidth="1" fitToHeight="0"/>""")
            sb.append("""<headerFooter><oddFooter>&amp;C&amp;P / &amp;N</oddFooter></headerFooter>""")
            sb.append("</worksheet>")
            return sb.toString()
        }

        private fun ref(col: Int, row: Int) = "${'A' + col}$row"

        private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    // 인쇄할 때 머리글(운행기록부 9~10행, 수정 이력 4행)을 페이지마다 반복, 열 때 수식 다시 계산
    private const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><bookViews><workbookView/></bookViews><sheets><sheet name="$SHEET_NAME" sheetId="1" r:id="rId1"/><sheet name="$EDIT_SHEET_NAME" sheetId="2" r:id="rId3"/></sheets><definedNames><definedName name="_xlnm.Print_Titles" localSheetId="0">'$SHEET_NAME'!${'$'}9:${'$'}10</definedName><definedName name="_xlnm.Print_Titles" localSheetId="1">'$EDIT_SHEET_NAME'!${'$'}4:${'$'}4</definedName></definedNames><calcPr calcId="0" fullCalcOnLoad="1"/></workbook>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/></Relationships>"""

    // 글꼴: 0 기본, 1 굵게, 2 제목, 3 작은 회색, 4 소제목, 5 주황 굵게 / 채우기: 2 머리글, 3 합계, 4 경고
    private const val FONT = """<name val="맑은 고딕"/><family val="3"/><charset val="129"/>"""
    private const val B = """<border><left style="thin"><color rgb="FF8C959F"/></left><right style="thin"><color rgb="FF8C959F"/></right><top style="thin"><color rgb="FF8C959F"/></top><bottom style="thin"><color rgb="FF8C959F"/></bottom><diagonal/></border>"""
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
        """<numFmts count="2"><numFmt numFmtId="164" formatCode="#,##0.0"/><numFmt numFmtId="165" formatCode="0.0%"/></numFmts>""" +
        """<fonts count="6"><font><sz val="10"/>$FONT</font><font><b/><sz val="10"/>$FONT</font><font><b/><sz val="18"/>$FONT</font><font><sz val="9"/><color rgb="FF59636E"/>$FONT</font><font><b/><sz val="11"/>$FONT</font><font><b/><sz val="10"/><color rgb="FFBC4C00"/>$FONT</font></fonts>""" +
        """<fills count="5"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE7ECF2"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFDDF4FF"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFFF1E5"/><bgColor indexed="64"/></patternFill></fill></fills>""" +
        """<borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border>$B</borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="15">""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>""" +
        """<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>""" +
        """<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>""" +
        """<xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>""" +
        """<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>""" +
        """<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>""" +
        """<xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>""" +
        """<xf numFmtId="0" fontId="5" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>""" +
        """<xf numFmtId="0" fontId="1" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>""" +
        """<xf numFmtId="164" fontId="1" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>""" +
        """<xf numFmtId="165" fontId="1" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>""" +
        """</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
