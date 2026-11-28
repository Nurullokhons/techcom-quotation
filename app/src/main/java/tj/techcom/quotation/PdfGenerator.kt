package tj.techcom.quotation

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
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
    val margin = 28f
    val doc = PdfDocument()
    val normal = Paint(3).apply { color = android.graphics.Color.BLACK; textSize = 8f }
    val bold = Paint(normal).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val small = Paint(normal).apply { textSize = 7f; color = android.graphics.Color.DKGRAY }
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
    val x = floatArrayOf(28f, 53f, 105f, 210f, 365f, 405f, 445f, 505f, 567f)
    var pageNo = 0
    var page: PdfDocument.Page
    var canvas: Canvas
    var y = 0f
    var total = BigDecimal.ZERO
    fun begin(first: Boolean) {
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, pageNo).create())
        canvas = page.canvas
        if (showHeader) {
            val left = runCatching { context.assets.open("techcom_logo.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
            val right = runCatching { context.assets.open("hikvision_logo.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
            if (left != null) canvas.drawBitmap(left, null, Rect(28, 18, 190, 57), Paint(3))
            else canvas.drawText("TECHCOM", 30f, 48f, Paint(bold).apply { textSize = 18f; color = android.graphics.Color.RED })
            if (right != null) canvas.drawBitmap(right, null, Rect(410, 22, 567, 55), Paint(3))
            else canvas.drawText("HIKVISION", 420f, 48f, Paint(bold).apply { textSize = 14f; color = android.graphics.Color.RED })
            val center = Paint(bold).apply { textSize = 6.7f; textAlign = Paint.Align.CENTER }
            canvas.drawText("г. Худжанд, ул. Исмаила Сомони 16", 300f, 20f, center)
            canvas.drawText("г. Душанбе, ул. Сафармухаммад Аюби 60", 300f, 30f, center)
            canvas.drawText("г. Худжанд (+992)926447474", 300f, 40f, center)
            canvas.drawText("г. Душанбе (+992)927447474", 300f, 50f, center)
            canvas.drawText("email: aliksystem@mail.ru", 300f, 60f, center)
            canvas.drawLine(margin, 76f, width - margin, 76f, Paint().apply { color = android.graphics.Color.LTGRAY })
        }
        if (first) {
            val title = Paint(bold).apply { textSize = 15f; textAlign = Paint.Align.CENTER }
            canvas.drawText("КОММЕРЧЕСКОЕ ПРЕДЛОЖЕНИЕ", 297f, if (showHeader) 105f else 42f, title)
            var cy = if (showHeader) 125f else 65f
            listOf(organization, contact, phone, address).filter { it.isNotBlank() }.forEach { canvas.drawText(it, margin, cy, normal); cy += 10f }
            y = if (showHeader) 155f else 105f
        } else {
            canvas.drawText("Коммерческое предложение (продолжение)", margin, 35f, bold)
            y = 55f
        }
        canvas.drawRect(margin, y, width - margin, y + 20f, Paint().apply { color = android.graphics.Color.rgb(235, 237, 240) })
        listOf("№", "Фото", "Модель", "Описание", "Ед.", "Кол-во", "Цена", "Сумма").forEachIndexed { i, label -> canvas.drawText(label, x[i] + 2f, y + 13f, bold) }
        y += 20f
    }
    begin(true)
    lines.forEachIndexed { index, line ->
        val models = wrapped(line.model, bold, x[3] - x[2] - 5f)
        val descriptions = wrapped(line.description, small, x[4] - x[3] - 5f)
        val rowHeight = maxOf(44f, maxOf(models.size, descriptions.size) * 9f + 10f)
        if (y + rowHeight > height - 115f) { doc.finishPage(page); begin(false) }
        canvas.drawLine(margin, y + rowHeight, width - margin, y + rowHeight, Paint().apply { color = android.graphics.Color.LTGRAY })
        canvas.drawText((index + 1).toString(), x[0] + 2f, y + 14f, normal)
        line.imageFile?.takeIf { it.exists() }?.let { file ->
            BitmapFactory.decodeFile(file.absolutePath)?.let { bmp ->
                canvas.drawBitmap(bmp, null, Rect(x[1].toInt() + 2, y.toInt() + 3, x[2].toInt() - 3, (y + rowHeight - 3).toInt()), Paint(3))
                bmp.recycle()
            }
        }
        models.forEachIndexed { n, s -> canvas.drawText(s, x[2] + 2f, y + 13f + n * 9f, bold) }
        descriptions.forEachIndexed { n, s -> canvas.drawText(s, x[3] + 2f, y + 13f + n * 9f, small) }
        canvas.drawText(line.unit.take(6), x[4] + 2f, y + 14f, normal)
        canvas.drawText(line.quantity.toString(), x[5] + 2f, y + 14f, normal)
        canvas.drawText(amount(line.price), x[6] + 2f, y + 14f, normal)
        val sum = (line.price.toBigDecimalOrNull() ?: BigDecimal.ZERO).multiply(BigDecimal(line.quantity))
        total = total.add(sum)
        canvas.drawText(amount(sum.toPlainString()), x[7] + 2f, y + 14f, normal)
        y += rowHeight
    }
    if (y > height - 110f) { doc.finishPage(page); begin(false) }
    y += 14f
    canvas.drawText("ИТОГО ПО СМЕТЕ: " + amount(total.toPlainString()) + " " + currency, margin, y, Paint(bold).apply { textSize = 11f })
    y += 22f
    canvas.drawText("Цены указаны в " + if (currency == "TJS") "сомони" else "долларах США", margin, y, normal)
    y += 12f
    canvas.drawText(if (taxIncluded) "Цены указаны с учётом налогов" else "Цены указаны без учёта налогов", margin, y, normal)
    y += 12f
    canvas.drawText("Данное коммерческое предложение действует 14 дней с даты выдачи", margin, y, normal)
    y += 12f
    canvas.drawText("Гарантия на оборудование 12 месяцев", margin, y, normal)
    doc.finishPage(page)
    val dir = File(context.getExternalFilesDir(null), "quotations").apply { mkdirs() }
    val file = File(dir, "KP_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".pdf")
    FileOutputStream(file).use { doc.writeTo(it) }
    doc.close()
    return file
}

internal fun shareQuotationPdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Отправить коммерческое предложение"))
}
