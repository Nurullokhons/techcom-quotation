package tj.techcom.quotation

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

internal data class CatalogProduct(
    val id: String,
    val model: String,
    val description: String,
    val unit: String,
    val dealerPriceUsd: String?,
    val installerPriceUsd: String?,
    val retailPriceUsd: String?,
    val category: String,
    val subcategory: String,
    val subsubcategory: String,
    val imageFile: File?
)

private data class CatalogLoadResult(
    val products: List<CatalogProduct>,
    val error: String? = null
)

private suspend fun loadCatalog(context: Context): CatalogLoadResult = withContext(Dispatchers.IO) {
    try {
        val imageDirectory = File(context.filesDir, "catalog-images")
        if (!imageDirectory.exists()) imageDirectory.mkdirs()

        val marker = File(imageDirectory, ".extracted")
        if (!marker.exists()) {
            ZipInputStream(context.assets.open("catalog_images.zip").buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val output = File(imageDirectory, entry.name).canonicalFile
                        val root = imageDirectory.canonicalPath + File.separator
                        require(output.path.startsWith(root)) { "Invalid image path in archive" }
                        output.parentFile?.mkdirs()
                        output.outputStream().buffered().use { stream -> zip.copyTo(stream) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            marker.writeText("ok")
        }

        val jsonText = context.assets.open("catalog.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(jsonText)
        val array = root.getJSONArray("products")
        val products = ArrayList<CatalogProduct>(array.length())

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val imageName = item.optString("image").takeIf { it.isNotBlank() && it != "null" }
            val imageFile = imageName?.let { File(imageDirectory, it) }?.takeIf { it.exists() }

            products += CatalogProduct(
                id = item.optString("id"),
                model = item.optString("model"),
                description = item.optString("description"),
                unit = item.optString("unit"),
                dealerPriceUsd = item.optString("dealerPriceUsd").takeIf { it.isNotBlank() && it != "null" },
                installerPriceUsd = item.optString("installerPriceUsd").takeIf { it.isNotBlank() && it != "null" },
                retailPriceUsd = item.optString("retailPriceUsd").takeIf { it.isNotBlank() && it != "null" },
                category = item.optString("category"),
                subcategory = item.optString("subcategory"),
                subsubcategory = item.optString("subsubcategory"),
                imageFile = imageFile
            )
        }
        CatalogLoadResult(products)
    } catch (e: Exception) {
        CatalogLoadResult(emptyList(), e.message ?: "Не удалось загрузить каталог")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogScreen(context: Context, onBack: () -> Unit) {
    var products by remember { mutableStateOf<List<CatalogProduct>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Все") }
    var selectedSubcategory by remember { mutableStateOf("Все") }
    var selectedProduct by remember { mutableStateOf<CatalogProduct?>(null) }

    LaunchedEffect(Unit) {
        val result = loadCatalog(context)
        products = result.products
        error = result.error
        loading = false
    }

    val categories = listOf("Все") + products.map { it.category }.distinct().sorted()
    val subcategories = listOf("Все") + products
        .filter { selectedCategory == "Все" || it.category == selectedCategory }
        .map { it.subcategory }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()

    val filtered = products.filter { product ->
        val matchesSearch = search.isBlank() ||
            product.model.contains(search, ignoreCase = true) ||
            product.description.contains(search, ignoreCase = true)
        val matchesCategory = selectedCategory == "Все" || product.category == selectedCategory
        val matchesSubcategory = selectedSubcategory == "Все" || product.subcategory == selectedSubcategory
        matchesSearch && matchesCategory && matchesSubcategory
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Каталог оборудования") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Поиск по модели или описанию") },
                singleLine = true
            )

            if (!loading && products.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = {
                                selectedCategory = category
                                selectedSubcategory = "Все"
                            },
                            label = { Text(category) }
                        )
                    }
                }

                if (subcategories.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(subcategories) { subcategory ->
                            FilterChip(
                                selected = selectedSubcategory == subcategory,
                                onClick = { selectedSubcategory = subcategory },
                                label = { Text(subcategory) }
                            )
                        }
                    }
                }

                Text("Найдено: ${filtered.size} из ${products.size}", style = MaterialTheme.typography.labelMedium)
            }

            when {
                loading -> Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text("Загружаем каталог и фотографии…", modifier = Modifier.padding(top = 12.dp))
                }
                error != null -> Text("Ошибка загрузки каталога: $error")
                filtered.isEmpty() -> Text("Ничего не найдено. Попробуй изменить запрос.")
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { product ->
                        CatalogProductCard(product = product, onClick = { selectedProduct = product })
                    }
                }
            }
        }
    }

    selectedProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { selectedProduct = null },
            title = { Text(product.model) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    product.imageFile?.let { file ->
                        AsyncImage(
                            model = file,
                            contentDescription = product.model,
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Text(product.description.ifBlank { "Описание отсутствует" })
                    Text("Категория: ${product.category} / ${product.subcategory}")
                    Text("Единица измерения: ${product.unit}")
                    Text("Дилер: ${formatUsd(product.dealerPriceUsd)}")
                    Text("Монтажник: ${formatUsd(product.installerPriceUsd)}")
                    Text("Розничная: ${formatUsd(product.retailPriceUsd)}")
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedProduct = null }) { Text("Закрыть") }
            }
        )
    }
}

@Composable
private fun CatalogProductCard(product: CatalogProduct, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = product.imageFile,
                contentDescription = product.model,
                modifier = Modifier.height(76.dp).weight(0.30f),
                contentScale = ContentScale.Fit
            )
            Column(modifier = Modifier.weight(0.70f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(product.model, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text("${product.category} • ${product.subcategory}", style = MaterialTheme.typography.labelSmall)
                Text("Монтажник: ${formatUsd(product.installerPriceUsd)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun formatUsd(value: String?): String =
    value?.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString()?.let { "\$$it" } ?: "не указана"
