package co.cuadra.app.ui

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Archivo de Excel (.xlsx) del cierre, armado a mano (sin librerías pesadas):
 * logo de cybeth arriba, títulos de colores, montos con formato de pesos y tablas con bordes.
 * (Sin nada de Android, para poder probarlo.)
 */
class Xlsx {
    // Estilos (índices de cellXfs en styles.xml)
    companion object {
        const val S_DEFAULT = 0
        const val S_TITLE = 1      // "cybeth" grande verde
        const val S_SUB = 2        // texto gris
        const val S_SECTION = 3    // franja verde con texto blanco
        const val S_LABEL = 4      // negrita
        const val S_MONEY = 5      // $ 1.234
        const val S_MONEY_B = 6    // $ 1.234 negrita
        const val S_TH = 7         // encabezado de tabla
        const val S_TD = 8         // celda de tabla
        const val S_TD_MONEY = 9   // monto en tabla
        const val S_TD_NUM = 10    // número en tabla
        const val S_TD_OK = 11     // estado verde
        const val S_TD_WARN = 12   // estado naranja
        const val S_TD_BAD = 13    // estado rojo
    }

    private class Cell(val col: Int, val text: String?, val num: Double?, val style: Int)

    private val rows = sortedMapOf<Int, MutableList<Cell>>()
    private val merges = mutableListOf<String>()
    private val heights = mutableMapOf<Int, Double>()

    fun text(row: Int, col: Int, v: String, style: Int = S_DEFAULT) {
        rows.getOrPut(row) { mutableListOf() } += Cell(col, v, null, style)
    }

    fun num(row: Int, col: Int, v: Number, style: Int = S_MONEY) {
        rows.getOrPut(row) { mutableListOf() } += Cell(col, null, v.toDouble(), style)
    }

    fun merge(range: String) { merges += range }
    fun height(row: Int, h: Double) { heights[row] = h }

    private fun colName(c: Int): String {
        var n = c; var s = ""
        while (n >= 0) { s = ('A' + n % 26) + s; n = n / 26 - 1 }
        return s
    }

    private fun x(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun sheetXml(widths: List<Double>, hasLogo: Boolean): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>""")
        append("""<sheetViews><sheetView workbookViewId="0" showGridLines="0"/></sheetViews>""")
        append("<cols>")
        widths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        append("</cols><sheetData>")
        val allRows = (rows.keys + heights.keys).toSortedSet()
        for (r in allRows) {
            val h = heights[r]
            append("""<row r="${r + 1}"""" + (if (h != null) """ ht="$h" customHeight="1"""" else "") + ">")
            rows[r]?.sortedBy { it.col }?.forEach { c ->
                val ref = colName(c.col) + (r + 1)
                if (c.num != null) append("""<c r="$ref" s="${c.style}"><v>${c.num}</v></c>""")
                else append("""<c r="$ref" s="${c.style}" t="inlineStr"><is><t xml:space="preserve">${x(c.text ?: "")}</t></is></c>""")
            }
            append("</row>")
        }
        append("</sheetData>")
        if (merges.isNotEmpty()) {
            append("""<mergeCells count="${merges.size}">""")
            merges.forEach { append("""<mergeCell ref="$it"/>""") }
            append("</mergeCells>")
        }
        append("""<pageMargins left="0.5" right="0.5" top="0.6" bottom="0.6" header="0.3" footer="0.3"/>""")
        append("""<pageSetup orientation="portrait" fitToWidth="1" fitToHeight="0"/>""")
        if (hasLogo) append("""<drawing r:id="rId1"/>""")
        append("</worksheet>")
    }

    private val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="1"><numFmt numFmtId="164" formatCode="&quot;$&quot;#,##0;[Red]\-&quot;$&quot;#,##0"/></numFmts>
<fonts count="7">
<font><sz val="11"/><color rgb="FF14213D"/><name val="Calibri"/><family val="2"/></font>
<font><b/><sz val="26"/><color rgb="FF0FB5A3"/><name val="Calibri"/><family val="2"/></font>
<font><sz val="11"/><color rgb="FF6B7690"/><name val="Calibri"/><family val="2"/></font>
<font><b/><sz val="12"/><color rgb="FFFFFFFF"/><name val="Calibri"/><family val="2"/></font>
<font><b/><sz val="11"/><color rgb="FF14213D"/><name val="Calibri"/><family val="2"/></font>
<font><b/><sz val="11"/><color rgb="FF0B8F80"/><name val="Calibri"/><family val="2"/></font>
<font><b/><sz val="11"/><color rgb="FFC0392B"/><name val="Calibri"/><family val="2"/></font>
</fonts>
<fills count="5">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF0FB5A3"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFEAF7F5"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFFF4E0"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="2">
<border><left/><right/><top/><bottom/><diagonal/></border>
<border><left style="thin"><color rgb="FFDDE2EA"/></left><right style="thin"><color rgb="FFDDE2EA"/></right><top style="thin"><color rgb="FFDDE2EA"/></top><bottom style="thin"><color rgb="FFDDE2EA"/></bottom><diagonal/></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="14">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"><alignment vertical="center"/></xf>
<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"><alignment vertical="center"/></xf>
<xf numFmtId="0" fontId="3" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"><alignment vertical="center" indent="1"/></xf>
<xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"><alignment horizontal="right"/></xf>
<xf numFmtId="164" fontId="4" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"><alignment horizontal="right"/></xf>
<xf numFmtId="0" fontId="4" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"><alignment vertical="center"/></xf>
<xf numFmtId="164" fontId="4" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1"><alignment horizontal="right" vertical="center"/></xf>
<xf numFmtId="1" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="5" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="4" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="6" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

    /** Arma el .xlsx. [logoPng] va arriba a la izquierda (puede ser null). */
    fun build(sheetName: String, widths: List<Double>, logoPng: ByteArray?, logoPx: Int = 72): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(name: String, s: String) { z.putNextEntry(ZipEntry(name)); z.write(s.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Default Extension="png" ContentType="image/png"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
${if (logoPng != null) """<Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>""" else ""}
</Types>""")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="${x(sheetName.take(31))}" sheetId="1" r:id="rId1"/></sheets>
</workbook>""")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""")
            put("xl/styles.xml", styles)
            put("xl/worksheets/sheet1.xml", sheetXml(widths, logoPng != null))
            if (logoPng != null) {
                val emu = logoPx * 9525L
                put("xl/worksheets/_rels/sheet1.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/>
</Relationships>""")
                put("xl/drawings/drawing1.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<xdr:oneCellAnchor><xdr:from><xdr:col>0</xdr:col><xdr:colOff>76200</xdr:colOff><xdr:row>0</xdr:row><xdr:rowOff>57150</xdr:rowOff></xdr:from>
<xdr:ext cx="$emu" cy="$emu"/>
<xdr:pic><xdr:nvPicPr><xdr:cNvPr id="2" name="Logo cybeth"/><xdr:cNvPicPr><a:picLocks noChangeAspect="1"/></xdr:cNvPicPr></xdr:nvPicPr>
<xdr:blipFill><a:blip r:embed="rId1"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>
<xdr:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$emu" cy="$emu"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic>
<xdr:clientData/></xdr:oneCellAnchor></xdr:wsDr>""")
                put("xl/drawings/_rels/drawing1.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image1.png"/>
</Relationships>""")
                z.putNextEntry(ZipEntry("xl/media/image1.png")); z.write(logoPng); z.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
