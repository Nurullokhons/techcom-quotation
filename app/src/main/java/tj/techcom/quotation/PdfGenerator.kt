package tj.techcom.quotation

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.math.BigDecimal
import java.math.RoundingMode

internal fun createQuotationPdf(context: Context, lines: List<QuotationLine>, organization: String, contact: String, phone: String, address: String, currency: String, exchangeRate: String, taxIncluded: Boolean, showHeader: Boolean): File {
    val width = 595
    val height = 842
    val margin = 10f
    val doc = PdfDocument()
    val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 8.5f }
    val boldTypeface = runCatching { context.assets.open("verdana_bold.ttf").use { Typeface.createFromFile(it.fd) } }.getOrElse { Typeface.create("sans-serif", Typeface.BOLD) }
    val bold = Paint(normal).apply { typeface = boldTypeface; textSize = 10f }
    val small = Paint(normal).apply { textSize = 7f; color = android.graphics.Color.BLACK }
    val rate = exchangeRate.toBigDecimalOrNull() ?: BigDecimal.ONE
    fun amount(value: String): String {
        val v = value.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val out = if (currency == "TJS") v.multiply(rate) else v
        return out.setScale(2, RoundingMode.HALF_UP).toPlainString()
    }
    fun wrapped(value: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        var line = ""
        value.split(Regex("\\s+")).forEach { word ->
            val candidate = if (line.isEmpty()) word else line + " " + word
            if (paint.measureText(candidate) <= maxWidth || line.isEmpty()) line = candidate
            else { result.add(line); line = word }
        }
        if (line.isNotEmpty()) result.add(line)
        return if (result.isEmpty()) listOf("") else result
    }
    val x = floatArrayOf(10f, 29f, 102f, 212f, 351f, 393f, 430f, 494f, 585f)
    var pageNo = 0
    lateinit var page: PdfDocument.Page
    lateinit var canvas: Canvas
    val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 0.8f }
    fun centered(text: String, left: Float, right: Float, top: Float, bottom: Float, paint: Paint) {
        val metrics = paint.fontMetrics
        val baseline = (top + bottom - metrics.ascent - metrics.descent) / 2f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, (left + right) / 2f, baseline, paint)
        paint.textAlign = Paint.Align.LEFT
    }
    var y = 0f
    var total = BigDecimal.ZERO
    fun begin(first: Boolean) {
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, pageNo).create())
        canvas = page.canvas
        if (first && showHeader) {
            val header = runCatching { context.assets.open("quotation_header.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
            if (header != null) {
                val targetWidth = width - margin * 2
                val targetHeight = targetWidth * header.height / header.width.toFloat()
                canvas.drawBitmap(header, null, RectF(margin, 8f, width - margin, 8f + targetHeight), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                header.recycle()
            } else {
                val center = Paint(bold).apply { textSize = 6.7f; textAlign = Paint.Align.CENTER }
                canvas.drawText("TECHCOM                         г. Худжанд, ул. Исмаила Сомони 16                         HIKVISION", 297f, 24f, center)
                canvas.drawText("г. Душанбе, ул. Сафармухаммад Аюби 60", 297f, 34f, center)
                canvas.drawText("г. Худжанд (+992)926447474   |   г. Душанбе (+992)927447474", 297f, 44f, center)
                canvas.drawText("email: aliksystem@mail.ru", 297f, 54f, center)
            }
            canvas.drawLine(margin, 64f, width - margin, 64f, Paint().apply { color = android.graphics.Color.LTGRAY })
        }
        if (first) {
            val barY = if (showHeader) 76f else 24f
            canvas.drawRect(margin, barY, width - margin, barY + 10f, Paint().apply { color = android.graphics.Color.LTGRAY })
            val title = Paint(bold).apply { textSize = 7.5f; textAlign = Paint.Align.CENTER }
            canvas.drawText("Коммерческое предложение", width / 2f, barY + 7.5f, title)
            val datePaint = Paint(bold).apply { textSize = 7.5f; textAlign = Paint.Align.RIGHT }
            canvas.drawText("Дата: " + SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date()), width - margin - 2f, barY - 2f, datePaint)
            var cy = barY + 22f
            listOf(organization, contact, phone, address).filter { it.isNotBlank() }.forEach { canvas.drawText(it, margin + 2f, cy, normal); cy += 9f }
            y = maxOf(if (showHeader) 91f else 39f, cy + 2f)
        } else {
            y = 34f
        }
        canvas.drawRect(margin, y, width - margin, y + 14f, Paint().apply { color = android.graphics.Color.LTGRAY })
        val headerPaint = Paint(bold).apply { textSize = 7f }
        listOf("№", "Изображение", "Модель", "Описание", "Един. Изм", "Кол-во", "Цена", "Сумма").forEachIndexed { i, label ->
            centered(label, x[i], x[i + 1], y, y + 20f, headerPaint)
        }
        canvas.drawRect(margin, y, width - margin, y + 20f, gridPaint)
        for (column in 1 until x.size - 1) canvas.drawLine(x[column], y, x[column], y + 20f, gridPaint)
        y += 20f
    }
    begin(true)
    lines.forEachIndexed { index, line ->
        val models = wrapped(line.model, bold, x[3] - x[2] - 6f).take(7)
        val descriptions = wrapped(line.description, small, x[4] - x[3] - 6f).take(7)
        val rowHeight = 72f
        if (y + rowHeight > height - 115f) { doc.finishPage(page); begin(false) }
        canvas.drawRect(margin, y, width - margin, y + rowHeight, gridPaint)
        for (column in 1 until x.size - 1) canvas.drawLine(x[column], y, x[column], y + rowHeight, gridPaint)
        centered((index + 1).toString(), x[0], x[1], y, y + rowHeight, bold)
        line.imageFile?.takeIf { it.exists() }?.let { file ->
            BitmapFactory.decodeFile(file.absolutePath)?.let { bmp ->
                val box = RectF(x[1] + 3f, y + 3f, x[2] - 3f, y + rowHeight - 3f)
                val scale = minOf(box.width() / bmp.width, box.height() / bmp.height)
                val drawWidth = bmp.width * scale
                val drawHeight = bmp.height * scale
                val dest = RectF(box.centerX() - drawWidth / 2f, box.centerY() - drawHeight / 2f, box.centerX() + drawWidth / 2f, box.centerY() + drawHeight / 2f)
                canvas.drawBitmap(bmp, null, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                bmp.recycle()
            }
        }
        models.forEachIndexed { n, s -> canvas.drawText(s, x[2] + 2f, y + 13f + n * 9f, bold) }
        descriptions.forEachIndexed { n, s -> canvas.drawText(s, x[3] + 2f, y + 13f + n * 9f, small) }
        centered(line.unit.take(8), x[4], x[5], y, y + rowHeight, bold)
        centered(line.quantity.toString(), x[5], x[6], y, y + rowHeight, bold)
        centered(amount(line.price) + " " + if (currency == "TJS") "смн" else "USD", x[6], x[7], y, y + rowHeight, Paint(bold).apply { textSize = 9f })
        val sum = (line.price.toBigDecimalOrNull() ?: BigDecimal.ZERO).multiply(BigDecimal(line.quantity))
        total = total.add(sum)
        centered(amount(sum.toPlainString()) + " " + if (currency == "TJS") "смн" else "USD", x[7], x[8], y, y + rowHeight, Paint(bold).apply { textSize = 9f })
        y += rowHeight
    }
    if (y > height - 110f) { doc.finishPage(page); begin(false) }
    y += 14f
    canvas.drawText("ИТОГО ПО СМЕТЕ: " + amount(total.toPlainString()) + " " + (if (currency == "TJS") "смн" else "USD"), margin, y, Paint(bold).apply { textSize = 11f; textAlign = Paint.Align.LEFT })
    y += 22f
    canvas.drawText("* Цены указаны в " + if (currency == "TJS") "Сомони" else "долларах США", margin, y, normal)
    y += 12f
    canvas.drawText(if (taxIncluded) "* Цены указаны с учётом налогов" else "* Цены указаны без учёта налогов", margin, y, normal)
    y += 12f
    canvas.drawText("* Данное коммерческое предложение действует 14 дней с даты выдачи.", margin, y, normal)
    y += 12f
    canvas.drawText("* Гарантия на оборудование 12 месяцев.", margin, y, normal)
    y += 12f
    canvas.drawText("* Расходные материалы считаются по факту.", margin, y, normal)
    doc.finishPage(page)
    val dir = File(context.getExternalFilesDir(null), "quotations").apply { mkdirs() }
    val file = File(dir, "KP_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".pdf")
    FileOutputStream(file).use { doc.writeTo(it) }
    doc.close()
    return file
}

internal fun previewQuotationPdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(view, "Просмотреть PDF"))
}
