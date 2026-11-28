package tj.techcom.quotation

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val HISTORY_PREFS = "quotation_history"
private const val HISTORY_KEY = "items"

internal fun readQuotationHistory(context: Context): List<JSONObject> {
    val raw = context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE).getString(HISTORY_KEY, "[]") ?: "[]"
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).map { array.getJSONObject(it) }
            .sortedByDescending { it.optLong("updatedAt") }
    }.getOrDefault(emptyList())
}

internal fun saveQuotationToHistory(context: Context, source: String): String {
    val quote = JSONObject(source)
    if (quote.optString("quoteId").isBlank()) quote.put("quoteId", UUID.randomUUID().toString())
    quote.put("updatedAt", System.currentTimeMillis())
    val org = quote.optString("clientOrganization").ifBlank { "Без названия" }
    val contact = quote.optString("clientContact")
    quote.put("title", if (contact.isBlank()) org else org + " • " + contact)
    val items = readQuotationHistory(context).filterNot { it.optString("quoteId") == quote.optString("quoteId") }
    val array = JSONArray().apply {
        put(quote)
        items.forEach { put(it) }
    }
    context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE).edit().putString(HISTORY_KEY, array.toString()).apply()
    return quote.toString()
}

internal fun deleteQuotationFromHistory(context: Context, quoteId: String) {
    val array = JSONArray()
    readQuotationHistory(context).filterNot { it.optString("quoteId") == quoteId }.forEach { array.put(it) }
    context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE).edit().putString(HISTORY_KEY, array.toString()).apply()
}

internal fun duplicateQuotation(source: JSONObject): String {
    return JSONObject(source.toString()).apply {
        put("quoteId", UUID.randomUUID().toString())
        put("clientOrganization", "")
        put("clientContact", "")
        put("clientPhone", "")
        put("clientAddress", "")
        put("updatedAt", System.currentTimeMillis())
        put("title", "Копия КП")
    }.toString()
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun QuotationHistoryScreen(
    context: Context,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    var quotes by remember { mutableStateOf(readQuotationHistory(context)) }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("История КП") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }
            )
        }
    ) { padding ->
        if (quotes.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Сохранённых предложений пока нет.", style = MaterialTheme.typography.titleMedium)
                Text("Открой новое КП и нажми «Сохранить КП». Оно появится здесь.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quotes, key = { it.optString("quoteId") }) { quote ->
                    val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(quote.optLong("updatedAt")))
                    val count = quote.optJSONArray("lines")?.length() ?: 0
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(quote.optString("title", "Коммерческое предложение"), style = MaterialTheme.typography.titleMedium)
                            Text(date + " • Позиций: " + count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onOpen(quote.toString()) }) { Text("Открыть") }
                                TextButton(onClick = { onCopy(duplicateQuotation(quote)) }) { Text("Создать копию") }
                                TextButton(onClick = { deleting = quote }) { Text("Удалить") }
                            }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { quote ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить КП?") },
            text = { Text("Предложение «" + quote.optString("title") + "» будет удалено из истории.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteQuotationFromHistory(context, quote.optString("quoteId"))
                    quotes = readQuotationHistory(context)
                    deleting = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } }
        )
    }
}
