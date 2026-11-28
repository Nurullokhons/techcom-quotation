package tj.techcom.quotation

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode

internal data class QuotationLine(
    val id: String,
    val model: String,
    val unit: String,
    val description: String,
    val imageFile: File?,
    val quantity: Int,
    // All line prices are stored internally in USD.
    val price: String
)

private fun displayPrice(usdPrice: String, currency: String, rate: String): String {
    val amount = usdPrice.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val converted = if (currency == "TJS") {
        amount.multiply(rate.toBigDecimalOrNull() ?: BigDecimal.ZERO)
    } else amount
    return converted.setScale(2, RoundingMode.HALF_UP).toPlainString()
}

private fun enteredPriceToUsd(value: String, currency: String, rate: String): String {
    val amount = value.replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO
    val usd = if (currency == "TJS") {
        val exchangeRate = rate.toBigDecimalOrNull() ?: BigDecimal.ZERO
        if (exchangeRate.compareTo(BigDecimal.ZERO) > 0) amount.divide(exchangeRate, 6, RoundingMode.HALF_UP) else BigDecimal.ZERO
    } else amount
    return usd.stripTrailingZeros().toPlainString().ifBlank { "0" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewQuotationScreen(context: Context, initialQuotationJson: String? = null, onBack: () -> Unit) {
    var products by remember { mutableStateOf<List<CatalogProduct>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var showNewModelDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var newModelName by remember { mutableStateOf("") }
    var newModelUnit by remember { mutableStateOf("шт.") }
    var newModelPrice by remember { mutableStateOf("") }
    var clientOrganization by remember { mutableStateOf("") }
    var clientContact by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    var clientAddress by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("USD") }
    var priceType by remember { mutableStateOf("Монтажник") }
    var priceTypeMenuExpanded by remember { mutableStateOf(false) }
    var exchangeRate by remember { mutableStateOf("") }
    var taxIncluded by remember { mutableStateOf(false) }
    var showHeader by remember { mutableStateOf(true) }
    var pdfMessage by remember { mutableStateOf<String?>(null) }
    var draftRestored by remember { mutableStateOf(false) }
    var quoteId by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<QuotationLine>() }
    val itemBounds = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    var draggingLineId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        val result = loadCatalog(context)
        products = result.products
        loadError = result.error

        val saved = initialQuotationJson ?: context.getSharedPreferences("quotation_draft", Context.MODE_PRIVATE)
            .getString("draft", null)
        if (!saved.isNullOrBlank()) {
            runCatching {
                val root = JSONObject(saved)
                quoteId = root.optString("quoteId")
                clientOrganization = root.optString("clientOrganization")
                clientContact = root.optString("clientContact")
                clientPhone = root.optString("clientPhone")
                clientAddress = root.optString("clientAddress")
                currency = root.optString("currency", "USD").takeIf { it == "TJS" } ?: "USD"
                priceType = root.optString("priceType", "Монтажник").takeIf { it in listOf("Дилер", "Монтажник", "Розничный") } ?: "Монтажник"
                exchangeRate = root.optString("exchangeRate")
                taxIncluded = root.optBoolean("taxIncluded", false)
                showHeader = root.optBoolean("showHeader", true)
                val savedLines = root.optJSONArray("lines") ?: JSONArray()
                for (i in 0 until savedLines.length()) {
                    val item = savedLines.getJSONObject(i)
                    val id = item.optString("id")
                    val product = products.firstOrNull { it.id == id }
                    lines.add(
                        QuotationLine(
                            id = id,
                            model = item.optString("model"),
                            unit = item.optString("unit", "шт."),
                            description = item.optString("description"),
                            imageFile = product?.imageFile,
                            quantity = item.optInt("quantity", 1).coerceAtLeast(1),
                            price = item.optString("price")
                        )
                    )
                }
            }
        }
        loading = false
        draftRestored = true
    }

    LaunchedEffect(lines.toList(), clientOrganization, clientContact, clientPhone, clientAddress, currency, priceType, exchangeRate, taxIncluded, showHeader, quoteId, draftRestored) {
        if (draftRestored) {
            val root = JSONObject()
            root.put("quoteId", quoteId)
            root.put("clientOrganization", clientOrganization)
            root.put("clientContact", clientContact)
            root.put("clientPhone", clientPhone)
            root.put("clientAddress", clientAddress)
            root.put("currency", currency)
            root.put("priceType", priceType)
            root.put("exchangeRate", exchangeRate)
            root.put("taxIncluded", taxIncluded)
            root.put("showHeader", showHeader)
            val savedLines = JSONArray()
            lines.forEach { line ->
                savedLines.put(JSONObject().apply {
                    put("id", line.id)
                    put("model", line.model)
                    put("unit", line.unit)
                    put("description", line.description)
                    put("quantity", line.quantity)
                    put("price", line.price)
                })
            }
            root.put("lines", savedLines)
            context.getSharedPreferences("quotation_draft", Context.MODE_PRIVATE)
                .edit().putString("draft", root.toString()).apply()
        }
    }

    // Substring search in model and dedicated searchable alias only.
    val normalizedSearch = search.trim()
    val matches = products.filter { product ->
        normalizedSearch.isNotBlank() && (
            product.model.contains(normalizedSearch, ignoreCase = true) ||
            product.searchName.contains(normalizedSearch, ignoreCase = true)
        )
    }.take(12)

    fun priceFor(product: CatalogProduct, type: String): String = when (type) {
        "Дилер" -> product.dealerPriceUsd.orEmpty()
        "Розничный" -> product.retailPriceUsd.orEmpty()
        else -> product.installerPriceUsd.orEmpty()
    }

    fun changePriceType(type: String) {
        priceType = type
        lines.indices.forEach { index ->
            val line = lines[index]
            val product = products.firstOrNull { it.id == line.id }
            if (product != null) lines[index] = line.copy(price = priceFor(product, type))
        }
    }

    fun addProduct(product: CatalogProduct) {
        val existing = lines.indexOfFirst { it.id == product.id }
        if (existing >= 0) {
            lines[existing] = lines[existing].copy(quantity = lines[existing].quantity + 1)
        } else {
            lines.add(
                QuotationLine(
                    id = product.id,
                    model = product.model,
                    unit = product.unit.ifBlank { "шт." },
                    description = product.description,
                    imageFile = product.imageFile,
                    quantity = 1,
                    price = priceFor(product, priceType)
                )
            )
        }
        search = ""
        menuExpanded = false
        focusManager.clearFocus()
    }

    val totalUsd = lines.fold(BigDecimal.ZERO) { sum, line ->
        val price = line.price.toBigDecimalOrNull() ?: BigDecimal.ZERO
        sum + price.multiply(BigDecimal(line.quantity))
    }
    val totalDisplay = if (currency == "TJS") {
        totalUsd.multiply(exchangeRate.toBigDecimalOrNull() ?: BigDecimal.ZERO)
    } else totalUsd
    val currencyLabel = if (currency == "TJS") "TJS" else "USD"

    fun serializeQuotation(): String {
        val root = JSONObject()
        root.put("quoteId", quoteId)
        root.put("clientOrganization", clientOrganization)
        root.put("clientContact", clientContact)
        root.put("clientPhone", clientPhone)
        root.put("clientAddress", clientAddress)
        root.put("currency", currency)
        root.put("priceType", priceType)
        root.put("exchangeRate", exchangeRate)
        root.put("taxIncluded", taxIncluded)
        root.put("showHeader", showHeader)
        val savedLines = JSONArray()
        lines.forEach { line ->
            savedLines.put(JSONObject().apply {
                put("id", line.id)
                put("model", line.model)
                put("unit", line.unit)
                put("description", line.description)
                put("quantity", line.quantity)
                put("price", line.price)
            })
        }
        root.put("lines", savedLines)
        return root.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новое КП") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
                actions = {
                    TextButton(onClick = { showDetailsDialog = true }) { Text("Клиент и валюта") }
                }
            )
        },
        bottomBar = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Позиций: ${lines.size}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Итого: ${totalDisplay.setScale(2, RoundingMode.HALF_UP).toPlainString()} $currencyLabel",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                runCatching {
                                    val saved = saveQuotationToHistory(context, serializeQuotation())
                                    quoteId = JSONObject(saved).optString("quoteId")
                                    pdfMessage = "КП сохранено в истории. Его можно открыть и продолжить позже."
                                }.onFailure { pdfMessage = "Не удалось сохранить КП: " + it.message }
                            },
                            modifier = Modifier.weight(0.8f)
                        ) { Text("Сохранить КП") }
                        androidx.compose.material3.Button(
                            onClick = {
                                if (lines.isEmpty()) pdfMessage = "Добавьте хотя бы одну позицию."
                                else if (currency == "TJS" && (exchangeRate.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO) ?: -1) <= 0) pdfMessage = "Укажите корректный курс валюты."
                                else runCatching {
                                    val saved = saveQuotationToHistory(context, serializeQuotation())
                                    quoteId = JSONObject(saved).optString("quoteId")
                                    val pdf = createQuotationPdf(context, lines.toList(), clientOrganization, clientContact, clientPhone, clientAddress, currency, exchangeRate, taxIncluded, showHeader)
                                    previewQuotationPdf(context, pdf)
                                }.onFailure { pdfMessage = "Не удалось создать PDF: " + it.message }
                            },
                            modifier = Modifier.weight(1.2f)
                        ) { Text("Сохранить и просмотреть PDF") }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; menuExpanded = it.isNotBlank() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Найти модель или название") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                trailingIcon = {
                    IconButton(onClick = { showNewModelDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Новая модель")
                    }
                }
            )
            if (menuExpanded && search.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    LazyColumn {
                        item {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    newModelName = search
                                    newModelPrice = ""
                                    showNewModelDialog = true
                                    menuExpanded = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("+ Новая модель") }
                        }
                        if (matches.isEmpty()) {
                            item { Text("Совпадений не найдено", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            items(matches, key = { it.id }) { product ->
                                androidx.compose.material3.TextButton(
                                    onClick = { addProduct(product) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(product.model, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (loading) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
                Text("Загружаем каталог…")
            } else if (loadError != null) {
                Text("Не удалось загрузить каталог: $loadError")
            } else if (lines.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Начни вводить модель в поле поиска выше. Выбери товар из подсказок или добавь новую модель.",
                        modifier = Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Text("Оборудование в предложении", style = MaterialTheme.typography.titleMedium)
                lines.toList().forEach { line ->
                    val index = lines.indexOfFirst { it.id == line.id }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                val top = coordinates.positionInRoot().y
                                itemBounds[line.id] = top to (top + coordinates.size.height)
                            }
                            .graphicsLayer {
                                if (draggingLineId == line.id) {
                                    translationY = dragOffsetY
                                    shadowElevation = 12.dp.toPx()
                                }
                            }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    IconButton(
                                        modifier = Modifier.pointerInput(line.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggingLineId = line.id
                                                    dragOffsetY = 0f
                                                },
                                                onDragEnd = {
                                                    draggingLineId = null
                                                    dragOffsetY = 0f
                                                },
                                                onDragCancel = {
                                                    draggingLineId = null
                                                    dragOffsetY = 0f
                                                }
                                            ) { change, dragAmount ->
                                                change.consume()
                                                if (draggingLineId == line.id) {
                                                    dragOffsetY += dragAmount.y
                                                    val bounds = itemBounds[line.id]
                                                    if (bounds != null) {
                                                        val draggedCenter = (bounds.first + bounds.second) / 2f + dragOffsetY
                                                        val target = lines.firstOrNull { other ->
                                                            other.id != line.id && itemBounds[other.id]?.let { targetBounds ->
                                                                draggedCenter >= targetBounds.first &&
                                                                    draggedCenter <= targetBounds.second
                                                            } == true
                                                        }
                                                        if (target != null) {
                                                            val moving = lines.firstOrNull { it.id == line.id }
                                                            if (moving != null) {
                                                                lines.remove(moving)
                                                                val targetIndex = lines.indexOfFirst { it.id == target.id }
                                                                if (targetIndex >= 0) lines.add(targetIndex, moving)
                                                                dragOffsetY = 0f
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.DragHandle, contentDescription = "Перетащить позицию")
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(line.model, fontWeight = FontWeight.SemiBold)
                                        if (line.unit.isNotBlank()) Text("Ед. изм.: ${line.unit}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                IconButton(onClick = { lines.remove(line) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить")
                                }
                            }
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text("Кол-во", style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = { if (line.quantity > 1) lines[index] = line.copy(quantity = line.quantity - 1) }) {
                                    Icon(Icons.Default.Remove, contentDescription = "Уменьшить")
                                }
                                OutlinedTextField(
                                    value = line.quantity.toString(),
                                    onValueChange = { value ->
                                        val qty = value.toIntOrNull()
                                        if (qty != null && qty > 0 && qty < 100000) lines[index] = line.copy(quantity = qty)
                                        if (value.isEmpty()) lines[index] = line.copy(quantity = 1)
                                    },
                                    modifier = Modifier.width(82.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                IconButton(onClick = { lines[index] = line.copy(quantity = line.quantity + 1) }) {
                                    Icon(Icons.Default.Add, contentDescription = "Увеличить")
                                }
                            }
                            Text(
                                "Сумма: ${displayPrice((line.price.toBigDecimalOrNull()?.multiply(BigDecimal(line.quantity)) ?: BigDecimal.ZERO).toPlainString(), currency, exchangeRate)} $currencyLabel",
                                fontWeight = FontWeight.Medium
                            )
                            OutlinedTextField(
                                value = displayPrice(line.price, currency, exchangeRate),
                                onValueChange = { value ->
                                    if (value.matches(Regex("^\\d{0,12}([.,]\\d{0,4})?$"))) {
                                        lines[index] = line.copy(price = enteredPriceToUsd(value, currency, exchangeRate))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Цена за единицу, $currencyLabel") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (pdfMessage != null) {
        AlertDialog(onDismissRequest = { pdfMessage = null }, title = { Text("Коммерческое предложение") }, text = { Text(pdfMessage.orEmpty()) }, confirmButton = { TextButton(onClick = { pdfMessage = null }) { Text("OK") } })
    }

    if (showDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text("Данные клиента и параметры КП") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(clientOrganization, { clientOrganization = it }, label = { Text("Название организации") }, singleLine = true)
                    OutlinedTextField(clientContact, { clientContact = it }, label = { Text("Контактное лицо") }, singleLine = true)
                    OutlinedTextField(clientPhone, { clientPhone = it }, label = { Text("Телефон") }, singleLine = true)
                    OutlinedTextField(clientAddress, { clientAddress = it }, label = { Text("Адрес") }, singleLine = true)
                    Text("Тип цены каталога", fontWeight = FontWeight.SemiBold)
                    androidx.compose.foundation.layout.Box {
                        TextButton(onClick = { priceTypeMenuExpanded = true }) {
                            Text("$priceType  ▾")
                        }
                        DropdownMenu(
                            expanded = priceTypeMenuExpanded,
                            onDismissRequest = { priceTypeMenuExpanded = false }
                        ) {
                            listOf("Дилер", "Монтажник", "Розничный").forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        changePriceType(type)
                                        priceTypeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Text("Валюта коммерческого предложения", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { currency = "USD" }) { Text(if (currency == "USD") "✓ USD" else "USD") }
                        TextButton(onClick = { currency = "TJS" }) { Text(if (currency == "TJS") "✓ TJS" else "TJS") }
                    }
                    if (currency == "TJS") {
                        OutlinedTextField(
                            value = exchangeRate,
                            onValueChange = { if (it.matches(Regex("^\\d{0,9}([.,]\\d{0,6})?$"))) exchangeRate = it.replace(',', '.') },
                            label = { Text("Курс: 1 USD = ? TJS") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = showHeader, onCheckedChange = { showHeader = it })
                        Text("С шапкой (TECHCOM, контакты и Hikvision)")
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = taxIncluded, onCheckedChange = { taxIncluded = it })
                        Text(if (taxIncluded) "Цены указаны с учётом налогов" else "Цены указаны без учёта налогов")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showDetailsDialog = false },
                    enabled = currency == "USD" || (exchangeRate.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO) ?: -1) > 0
                ) { Text("Готово") }
            }
        )
    }

    if (showNewModelDialog) {
        AlertDialog(
            onDismissRequest = { showNewModelDialog = false },
            title = { Text("Новая модель") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newModelName, onValueChange = { newModelName = it }, label = { Text("Название модели") }, singleLine = true)
                    OutlinedTextField(value = newModelUnit, onValueChange = { newModelUnit = it }, label = { Text("Единица измерения") }, singleLine = true)
                    OutlinedTextField(
                        value = newModelPrice,
                        onValueChange = { if (it.matches(Regex("^\\d{0,12}([.,]\\d{0,4})?$"))) newModelPrice = it.replace(',', '.') },
                        label = { Text("Цена, $currencyLabel") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newModelName.isNotBlank() && (newModelPrice.isBlank() || currency == "USD" || (exchangeRate.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO) ?: -1) > 0),
                    onClick = {
                        val id = "manual-${System.currentTimeMillis()}"
                        val priceUsd = if (newModelPrice.isBlank()) "" else enteredPriceToUsd(newModelPrice, currency, exchangeRate)
                        lines.add(QuotationLine(id, newModelName.trim(), newModelUnit.trim().ifBlank { "шт." }, "", null, 1, priceUsd))
                        showNewModelDialog = false
                        newModelName = ""
                        newModelUnit = "шт."
                        newModelPrice = ""
                        search = ""
                    }
                ) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { showNewModelDialog = false }) { Text("Отмена") } }
        )
    }
}
