package tj.techcom.quotation

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode

private data class QuotationLine(
    val id: String,
    val model: String,
    val unit: String,
    val description: String,
    val imageFile: java.io.File?,
    val quantity: Int,
    val price: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewQuotationScreen(context: Context, onBack: () -> Unit) {
    var products by remember { mutableStateOf<List<CatalogProduct>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var showNewModelDialog by remember { mutableStateOf(false) }
    var newModelName by remember { mutableStateOf("") }
    var newModelUnit by remember { mutableStateOf("шт.") }
    var newModelPrice by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<QuotationLine>() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        val result = loadCatalog(context)
        products = result.products
        loadError = result.error
        loading = false
    }

    val matches = products.filter {
        search.isNotBlank() &&
            (it.model.contains(search.trim(), ignoreCase = true) ||
             it.description.contains(search.trim(), ignoreCase = true))
    }.take(12)

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
                    price = product.installerPriceUsd.orEmpty()
                )
            )
        }
        search = ""
        menuExpanded = false
        focusManager.clearFocus()
    }

    val total = lines.fold(BigDecimal.ZERO) { sum, line ->
        val price = line.price.toBigDecimalOrNull() ?: BigDecimal.ZERO
        sum + price.multiply(BigDecimal(line.quantity))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новое КП") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }
            )
        },
        bottomBar = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Позиций: ${lines.size}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Итого: $${total.setScale(2, RoundingMode.HALF_UP).toPlainString()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it; menuExpanded = it.isNotBlank() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Найти модель или название") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { showNewModelDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Новая модель")
                        }
                    }
                )
                DropdownMenu(
                    expanded = menuExpanded && search.isNotBlank(),
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    DropdownMenuItem(
                        text = { Text("+ Новая модель", fontWeight = FontWeight.SemiBold) },
                        onClick = {
                            newModelName = search
                            showNewModelDialog = true
                            menuExpanded = false
                        }
                    )
                    matches.forEach { product ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(product.model, fontWeight = FontWeight.SemiBold)
                                    Text("Монтажник: " + (product.installerPriceUsd?.let { "$" + it } ?: "цена не указана"), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = { addProduct(product) }
                        )
                    }
                }
            }

            if (loading) {
                Column(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                    Text("Загружаем каталог…")
                }
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
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Text("Оборудование в предложении", style = MaterialTheme.typography.titleMedium)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(lines, key = { it.id }) { line ->
                        val index = lines.indexOfFirst { it.id == line.id }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(line.model, fontWeight = FontWeight.SemiBold)
                                        if (line.unit.isNotBlank()) Text("Ед. изм.: ${line.unit}", style = MaterialTheme.typography.bodySmall)
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
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("Сумма: $" + ((line.price.toBigDecimalOrNull() ?: BigDecimal.ZERO) * BigDecimal(line.quantity)).setScale(2, RoundingMode.HALF_UP).toPlainString())
                                }
                                OutlinedTextField(
                                    value = line.price,
                                    onValueChange = { value ->
                                        if (value.matches(Regex("^\\d{0,9}([.,]\\d{0,4})?$"))) lines[index] = line.copy(price = value.replace(',', '.'))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Цена за единицу, USD") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                            }
                        }
                    }
                }
            }
        }
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
                        onValueChange = { if (it.matches(Regex("^\\d{0,9}([.,]\\d{0,4})?$"))) newModelPrice = it.replace(',', '.') },
                        label = { Text("Цена, USD") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newModelName.isNotBlank(),
                    onClick = {
                        val id = "manual-${System.currentTimeMillis()}"
                        lines.add(QuotationLine(id, newModelName.trim(), newModelUnit.trim().ifBlank { "шт." }, "", null, 1, newModelPrice))
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
