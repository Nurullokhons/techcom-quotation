package tj.techcom.quotation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext

private val TechcomRed = Color(0xFFCE1118)
private val TechcomGray = Color(0xFF716F72)
private val TechcomWhite = Color(0xFFFFFFFF)
private val TechcomLightGray = Color(0xFFF3F3F3)

private val TechcomColorScheme = lightColorScheme(
    primary = TechcomRed,
    onPrimary = TechcomWhite,
    primaryContainer = TechcomRed,
    onPrimaryContainer = TechcomWhite,
    secondary = TechcomGray,
    onSecondary = TechcomWhite,
    secondaryContainer = TechcomLightGray,
    onSecondaryContainer = TechcomGray,
    background = TechcomWhite,
    onBackground = TechcomGray,
    surface = TechcomWhite,
    onSurface = TechcomGray,
    surfaceVariant = TechcomLightGray,
    onSurfaceVariant = TechcomGray,
    outline = TechcomGray
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = TechcomColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = TechcomColorScheme.background
                ) {
                    TechcomApp()
                }
            }
        }
    }
}

private enum class AppScreen(val title: String) {
    HOME("КП-Techcom"),
    NEW_QUOTATION("Новое КП"),
    HISTORY("История КП"),
    CATALOG("Каталог"),
    SETTINGS("Настройки")
}

private data class HomeSection(
    val screen: AppScreen,
    val subtitle: String,
    val icon: ImageVector
)

@Composable
private fun TechcomApp() {
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var quotationToEdit by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    BackHandler(enabled = currentScreen != AppScreen.HOME) {
        currentScreen = AppScreen.HOME
    }

    when (currentScreen) {
        AppScreen.HOME -> HomeScreen(onOpen = { currentScreen = it })
        AppScreen.NEW_QUOTATION -> NewQuotationScreen(
            context = context,
            initialQuotationJson = quotationToEdit,
            onBack = {
                quotationToEdit = null
                currentScreen = AppScreen.HOME
            }
        )
        AppScreen.HISTORY -> QuotationHistoryScreen(
            context = context,
            onBack = { currentScreen = AppScreen.HOME },
            onOpen = { saved ->
                quotationToEdit = saved
                currentScreen = AppScreen.NEW_QUOTATION
            },
            onCopy = { copy ->
                quotationToEdit = copy
                currentScreen = AppScreen.NEW_QUOTATION
            }
        )
        AppScreen.CATALOG -> CatalogScreen(
            context = context,
            onBack = { currentScreen = AppScreen.HOME }
        )
        AppScreen.SETTINGS -> PlaceholderScreen(
            title = "Настройки",
            description = "Здесь будут настраиваться валюта, курс валюты и параметры коммерческого предложения.",
            onBack = { currentScreen = AppScreen.HOME }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onOpen: (AppScreen) -> Unit) {
    val sections = listOf(
        HomeSection(AppScreen.NEW_QUOTATION, "Создать коммерческое предложение", Icons.Default.Add),
        HomeSection(AppScreen.HISTORY, "Ранее созданные предложения", Icons.Default.History),
        HomeSection(AppScreen.CATALOG, "Оборудование и цены Techcom", Icons.Default.Inventory2),
        HomeSection(AppScreen.SETTINGS, "Валюта и параметры документа", Icons.Default.Settings)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Image(
                            painter = painterResource(id = R.drawable.techcom_logo),
                            contentDescription = "TECHCOM",
                            modifier = Modifier.height(26.dp).width(132.dp),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            "Коммерческие предложения",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Добро пожаловать!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Здесь продавец сможет быстро собрать оборудование и подготовить PDF для клиента.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("Рабочие разделы", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sections) { section ->
                    SectionCard(section = section, onClick = { onOpen(section.screen) })
                }
            }
        }
    }
}

@Composable
private fun SectionCard(section: HomeSection, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(142.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(section.screen.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                section.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScreen(
    title: String,
    description: String,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(description, style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onBack) {
                Text("Вернуться на главную")
            }
        }
    }
}
