package com.example.shoppingassistant.feature.chat

import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.LocalImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import com.example.shoppingassistant.core.data.ProductRepository
import com.example.shoppingassistant.core.network.GptApi
import com.example.shoppingassistant.feature.chat.model.Product
import com.example.shoppingassistant.feature.chat.ui.ProductCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import com.example.shoppingassistant.*
import com.example.shoppingassistant.core.ui.AppImageLoader

// фон карточки: немного светлее страницы (близко к rgb(22,19,19), но читаемее)
// рядом с UI-константами
private val CardLayerColor: Color
    @Composable get() = MaterialTheme.colorScheme.background


// цвет «тени» бордера по краю карточки
private val CardBorderColor = Color.White.copy(alpha = 0.08f)


// Простое описание атрибутов товара
data class AttributeDef(
    val key: String,          // machine name: "memory", "color"
    val title: String,        // человекочитаемо: "Память", "Цвет"
    val options: List<String> // варианты значений
)

// Набор атрибутов для популярных подсказок (можно расширять)
private val attributeCatalog: Map<String, List<AttributeDef>> = mapOf(
    "iPhone 17" to listOf(
        AttributeDef("memory", "Память", listOf("128 ГБ", "256 ГБ", "512 ГБ", "1 ТБ")),
        AttributeDef("color", "Цвет", listOf("Чёрный", "Белый", "Синий", "Титановый")),
        AttributeDef("condition", "Состояние", listOf("Новый", "Как новый", "Б/У"))
    ),
    "iPhone 17 Pro" to listOf(
        AttributeDef("memory", "Память", listOf("256 ГБ", "512 ГБ", "1 ТБ")),
        AttributeDef("color", "Цвет", listOf("Титановый", "Синий", "Белый", "Чёрный"))
    ),
    "iPhone 16" to listOf(
        AttributeDef("memory", "Память", listOf("128 ГБ", "256 ГБ", "512 ГБ")),
        AttributeDef("color", "Цвет", listOf("Чёрный", "Белый", "Розовый"))
    ),
    "iPhone 16 Pro" to listOf(
        AttributeDef("memory", "Память", listOf("256 ГБ", "512 ГБ", "1 ТБ")),
        AttributeDef("color", "Цвет", listOf("Натуральный титан", "Синий", "Чёрный"))
    ),
    "iPhone 15" to listOf(
        AttributeDef("memory", "Память", listOf("128 ГБ", "256 ГБ", "512 ГБ")),
        AttributeDef("color", "Цвет", listOf("Чёрный", "Белый", "Синий", "Зелёный", "Жёлтый"))
    ),
    // примеры для других категорий
    "Молоко 2.5%" to listOf(
        AttributeDef("brand", "Бренд", listOf("Простоквашино", "Домик", "Вкуснотеево")),
        AttributeDef("volume", "Объём", listOf("0.5 л", "1 л", "1.5 л"))
    ),
    "Кроссовки Nike 42" to listOf(
        AttributeDef("color", "Цвет", listOf("Чёрные", "Белые", "Синие")),
        AttributeDef("season", "Сезон", listOf("Лето", "Деми", "Зима"))
    )
)
// маленький helper: из выбранного товара + атрибутов получаем категорию и фильтры
private fun toQuery(chosen: String, selectedAttrs: Map<String, String>): Pair<String, Map<String, String>> {
    // Простейшая логика: категория — первое слово без цифр; фильтры = выбранные атрибуты
    val category = chosen.takeWhile { !it.isDigit() }.trim().ifBlank { chosen }

    return category to selectedAttrs
}
class ChatViewModel(private val repo: ProductRepository) : ViewModel() {
    private val _offerCount = MutableStateFlow<Int?>(null)
    val offerCount: StateFlow<Int?> = _offerCount.asStateFlow()

    fun updateOfferCount(chosenItem: String?, selectedAttrs: Map<String, String>) {
        viewModelScope.launch {
            if (chosenItem == null) {
                _offerCount.value = null
            } else {
                val (category, filters) = toQuery(chosenItem, selectedAttrs)
                _offerCount.value = try { repo.offersCount(category, filters) } catch (_: Exception) { null }
            }
        }
    }
}

@Composable
fun ChatScreen(
    nav: NavController,
    viewModel: ChatViewModel = koinViewModel(), // или hiltViewModel()
    modifier: Modifier = Modifier
) {
    val products = remember { mutableStateListOf<Product>() }
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

// Популярные варианты (как было)
    val catalog = remember {
        listOf(
            "iPhone 17", "iPhone 17 Pro",
            "iPhone 16", "iPhone 16 Pro",
            "iPhone 15", "iPhone 15 Pro", "iPhone 15 Plus",
            "Samsung Galaxy S24", "Galaxy S24 Ultra",
            "Pixel 9", "Pixel 9 Pro",
            "Молоко 2.5%", "Роллы Филадельфия",
            "Кроссовки Nike 42", "Dyson Supersonic",
            "Крем SPF 50", "Подгузники размер 3"
        )
    }


// баннер (3 строки) показан?
    var bannerVisible by remember { mutableStateOf(false) }

// карточка показана?
    var showQuickCard by remember { mutableStateOf(false) }



// Что пользователь выбрал из подсказок — чтобы показать под полем панель атрибутов
    var chosenItem by remember { mutableStateOf<String?>(null) }

// Текущее наполнение атрибутов (выбор пользователя)
    val selectedAttrs = remember { mutableStateMapOf<String, String>() }

    val offerCount by viewModel.offerCount.collectAsState()
    // когда изменились chosenItem/selectedAttrs — вызвать
    LaunchedEffect(chosenItem, selectedAttrs) {
        viewModel.updateOfferCount(chosenItem, selectedAttrs)
    }
// Какой атрибут редактируем (для диалога выбора варианта)
    var dialogAttr by remember { mutableStateOf<AttributeDef?>(null) }

// Варианты атрибутов для выбранного товара
    val attrsForChosen = remember(chosenItem) {
        chosenItem?.let { key ->
            // ищем «наиболее близкий» ключ: точное совпадение или startsWith
            attributeCatalog[key]
                ?: attributeCatalog.entries.firstOrNull { key.startsWith(it.key, ignoreCase = true) }?.value
        }
    }

// Автоподсказки при наборе (он-девайс), берём топ-5
    val suggestions = remember(input) {
        if (input.isBlank()) emptyList()
        else catalog.filter { it.contains(input, ignoreCase = true) }.take(5)
    }

// Флаг, чтобы скрыть список во время анимации подстановки выбранной подсказки
    var hideSuggestions by remember { mutableStateOf(false) }

    val typeIntoField: (String) -> Unit = { text ->
        scope.launch {
            hideSuggestions = true       // ← прячем дропдаун до окончания анимации
            input = ""
            for (ch in text) {
                input += ch
                delay(35)
            }
            // остаётся скрытым, пока пользователь не начнёт печатать заново
        }
    }



    val handleSubmit: (String) -> Unit = { text ->
        if (text.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                val normalized = GptApi.normalizeQuery(text)
                val product = Product(
                    id = "1",
                    title = "iPhone 15 Pro 256GB Black",
                    category = normalized.category,
                    price = 119990.0,
                    deliveryTime = 2,
                    sellerRating = 4.8
                )
                withContext(Dispatchers.Main) { products.add(product) }
            }
            input = ""
        }
    }

    if (products.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentSize()
            ) {
                InputRow(
                    input = input,
                    onInputChange = { new ->
                        input = new
                        hideSuggestions = false
                    },
                    onSubmit = handleSubmit,
                    suggestions = suggestions,
                    onSuggestionClick = { choice ->
                        chosenItem = choice
                        bannerVisible = true      // показываем баннер
                        showQuickCard = false     // карточку НЕ показываем
                        typeIntoField(choice)
                    }
                    ,
                    hideSuggestions = hideSuggestions,
                    hasSelection = (chosenItem != null),          // ← добавили
                    onClear = {
                        input = ""
                        chosenItem = null
                        bannerVisible = false
                        showQuickCard = false
                        hideSuggestions = false
                        selectedAttrs.clear()
                    },
                    modifier = Modifier.padding(8.dp)
                )
// Баннер (3 строки) — показываем при выборе из списка, до нажатия "Показать подробнее"
                if (bannerVisible && chosenItem != null) {
                    //Spacer(Modifier.height(8.dp))
                    SuggestionBanner(
                        count = offerCount,
                        onShowBest = { /* TODO: отбор «лучшее» */ },
                        onShowDetails = {
                            showQuickCard = true   // теперь показываем карточку
                            bannerVisible = false  // и убираем баннер
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .align(Alignment.CenterHorizontally)
                    )
                }

// Карточка — только после «Показать подробнее»
                // СРАЗУ после InputRow(...) — без Spacer
                if (showQuickCard && chosenItem != null) {
                    ProductQuickCard(
                        title = chosenItem!!,
                        images = listOf( /* сюда реальные URL, когда появятся */ ),
                        attributes = attrsForChosen ?: emptyList(),
                        selected = selectedAttrs,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .align(Alignment.CenterHorizontally)
                    )

                }

            }
        }
    }
        else {
        Column(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(8.dp),
                reverseLayout = true
            ) {
                items(products.reversed()) { product ->
                    ProductQuickCard(
                        title = chosenItem!!,
                        images = listOf( /* сюда реальные URL, когда появятся */ ),
                        attributes = attrsForChosen ?: emptyList(),
                        selected = selectedAttrs,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .align(Alignment.CenterHorizontally)
                    )

                }
            }
            InputRow(
                input = input,
                onInputChange = { new ->
                    input = new
                    hideSuggestions = false
                },
                onSubmit = handleSubmit,
                suggestions = suggestions,
                onSuggestionClick = { choice ->
                    chosenItem = choice
                    bannerVisible = true          // показываем баннер под полем
                    showQuickCard = false         // карточку пока не показываем
                    typeIntoField(choice)         // анимированно подставляем текст в поле
                }
                ,
                hideSuggestions = hideSuggestions,
                hasSelection = (chosenItem != null),
                onClear = {
                    input = ""
                    chosenItem = null
                    bannerVisible = false
                    showQuickCard = false
                    hideSuggestions = false
                    selectedAttrs.clear()
                },
                modifier = Modifier.padding(8.dp)
            )
            // Баннер (3 строки) — показываем при выборе из списка, до нажатия "Показать подробнее"
            if (bannerVisible && chosenItem != null) {
                Spacer(Modifier.height(8.dp))
                SuggestionBanner(
                    count = offerCount,
                    onShowBest = { /* TODO: отбор «лучшее» */ },
                    onShowDetails = {
                        showQuickCard = true   // теперь показываем карточку
                        bannerVisible = false  // и убираем баннер
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .align(Alignment.CenterHorizontally)
                )
            }

// Карточка — только после «Показать подробнее»
            // СРАЗУ после InputRow(...) — без Spacer
            if (showQuickCard && chosenItem != null) {
                ProductQuickCard(
                    title = chosenItem!!,
                    images = emptyList(),
                    attributes = attrsForChosen ?: emptyList(),
                    selected = selectedAttrs,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)         // как раньше
                        .align(Alignment.CenterHorizontally)
                )
            }
        }
    }
    dialogAttr?.let { attr ->
        AttributePickerDialog(
            attr = attr,
            onSelect = { opt ->
                selectedAttrs[attr.key] = opt
                dialogAttr = null
            },
            onDismiss = { dialogAttr = null }
        )
    }


}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductQuickCard(
    title: String,
    images: List<Any>,
    attributes: List<AttributeDef>,
    selected: MutableMap<String, String>,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val pagerState = rememberPagerState(pageCount = { maxOf(images.size, 1) })

    Box(
        modifier = modifier
            .drawBehind {
                // бордер: по бокам и снизу; ВЕРХ НЕ РИСУЕМ
                val stroke = 1.dp.toPx()
                val c = CardBorderColor
                // слева
                drawLine(c, start = Offset(0f, 0f), end = Offset(0f, size.height), strokeWidth = stroke)
                // справа
                drawLine(c, start = Offset(size.width, 0f), end = Offset(size.width, size.height), strokeWidth = stroke)
                // снизу
                drawLine(c, start = Offset(0f, size.height), end = Offset(size.width, size.height), strokeWidth = stroke)
            }
            .background(CardLayerColor)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scroll)
                .padding(horizontal = 12.dp, vertical = 10.dp) // небольшой верхний отступ внутри
        ) {
            // Фото «в край» контента, но сдвинуто вниз на 8.dp от поля ввода
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                val imageLoader = AppImageLoader.current


                HorizontalPager(state = pagerState) { idx ->
                    val url = images.getOrNull(idx) as? String
                    if (url.isNullOrBlank()) {
                        NoPhotoPlaceholder(Modifier.fillMaxSize())
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(url)
                                .diskCacheKey(url)
                                .memoryCacheKey(url)
                                .build(),
                            contentDescription = null,
                            imageLoader = imageLoader,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

            }

            // Атрибуты сразу следом — без промежуточных фоновых блоков
            attributes.forEachIndexed { i, attr ->
                AttributeSection(
                    attr = attr,
                    selectedValue = selected[attr.key],
                    onSelect = { v -> selected[attr.key] = v }
                )
                if (i != attributes.lastIndex) Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { /* TODO */ },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .wrapContentWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    "Показать лучшие предложения",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // видимый белый скроллбар — только если есть что скроллить
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val viewportPx = with(density) { maxHeight.toPx() }
            if (scroll.maxValue > 0) {
                val contentPx = viewportPx + scroll.maxValue
                val proportion = (viewportPx / contentPx).coerceIn(0.1f, 1f)
                val thumbHeight = viewportPx * proportion
                val travel = viewportPx - thumbHeight
                val progress = scroll.value.toFloat() / scroll.maxValue.toFloat()
                val top = travel * progress

                Canvas(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(6.dp)
                        .align(Alignment.CenterEnd)
                        .padding(end = 6.dp)
                ) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.12f),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.85f),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, thumbHeight),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }
            }
        }
    }
}





@Composable
private fun NoPhotoPlaceholder(
    modifier: Modifier = Modifier,
    bg: Color = Color.Transparent
) {
    Box(
        modifier = modifier.background(bg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(52.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Извините, фото нет",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}




@Composable
private fun AttributeSection(
    attr: AttributeDef,
    selectedValue: String?,
    onSelect: (String) -> Unit
) {
    val icon = when (attr.key.lowercase()) {
        "memory", "storage" -> Icons.Outlined.Memory
        "color"             -> Icons.Outlined.Palette
        "brand"             -> Icons.Outlined.Storefront
        "volume", "size"    -> Icons.Outlined.Scale
        else                -> Icons.Outlined.Tune
    }

    // 1-я строка: иконка + название
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(
            attr.title,
            style = MaterialTheme.typography.titleLarge, // крупнее
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 2-я и далее: чипы строками, НИКОГДА не пересекаются с заголовком
    Spacer(Modifier.height(10.dp))
    // …
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = Int.MAX_VALUE
    ) {
        if (attr.key.equals("color", ignoreCase = true)) {
            attr.options.forEach { opt ->
                val color = colorFromName(opt)
                val selected = (selectedValue == opt)
                ColorSwatchSelectedBorderOnly(
                    color = color,
                    selected = selected,
                    onClick = { onSelect(opt) }
                )
            }
        } else {
            attr.options.forEach { opt ->
                val selected = (selectedValue == opt)
                TextChip(
                    text = opt,
                    selected = selected,
                    onClick = { onSelect(opt) }
                )
            }
        }
    }
}

@Composable
private fun TextChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        modifier = Modifier
            .height(50.dp)
            .wrapContentWidth()
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
        border = BorderStroke(
            width = if (selected) 3.dp else 2.dp,
            brush = SolidColor(
                if (selected)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
                else
                    MaterialTheme.colorScheme.outline
            )
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}




@Composable
private fun ColorSwatchSelectedBorderOnly(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = onClick),
        shape = shape,
        color = color,
        border = if (selected)
            BorderStroke(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.95f))
        else null,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {}
}






@Composable
private fun ColorChip(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    // Квадрат → при выборе «окружить» круглой рамкой
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = if (selected) CircleShape else RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
    )
}

// Грубая функция: преобразовать имя цвета в Color (можешь заменить на справочник)
@Composable
private fun colorFromName(name: String): Color =
    when (name.lowercase()) {
        "чёрный", "черный", "black" -> Color(0xFF000000)
        "белый", "white"            -> Color(0xFFFFFFFF)
        "синий", "blue"             -> Color(0xFF1E88E5)
        "зелёный", "зеленый", "green" -> Color(0xFF43A047)
        "титановый", "натуральный титан" -> Color(0xFF8D8D8D)
        "розовый", "pink"           -> Color(0xFFE91E63)
        "жёлтый", "желтый", "yellow"-> Color(0xFFFFEB3B)
        else                        -> MaterialTheme.colorScheme.surfaceTint
    }


@Composable
    private fun AttributeRow(
        attr: AttributeDef,
        value: String?,
        onClick: () -> Unit
    ) {
        // Иконка подбирается по ключу (минималистично)
        val icon = when (attr.key.lowercase()) {
            "memory", "storage" -> Icons.Outlined.Memory
            "color"             -> Icons.Outlined.Palette
            "condition"         -> Icons.Outlined.Verified
            "brand"             -> Icons.Outlined.Storefront
            "volume", "size"    -> Icons.Outlined.Scale
            else                -> Icons.Outlined.Tune
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text(attr.title, style = MaterialTheme.typography.bodyLarge)
                    if (value != null) {
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            "Выбрать…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    fun AttributePickerDialog(
        attr: AttributeDef,
        onSelect: (String) -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(attr.title) },
            text = {
                LazyColumn {
                    items(attr.options) { opt ->
                        Text(
                            text = opt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(opt) }
                                .padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        )
    }



@Composable
private fun InputRow(
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    hideSuggestions: Boolean = false,
    hasSelection: Boolean,                 // ← добавили
    onClear: () -> Unit,                   // ← добавили
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    // размер поля, чтобы не «срезался» glow снизу
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }

    val bg = MaterialTheme.colorScheme.background
    val bright = MaterialTheme.colorScheme.primary.copy(alpha = 1f)

    // Плавный рандомный цвет (как раньше)
    val borderAnim = remember { Animatable(bright) }
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.inversePrimary
    ).map { it.copy(alpha = 1f) }

    fun nextColor(exclude: Color): Color {
        val c = palette.filter { it != exclude }
        return if (c.isNotEmpty()) c.random() else exclude
    }

    // Если фокус — фикс яркий; если нет — мягкие переливы
    LaunchedEffect(isFocused) {
        if (isFocused) {
            borderAnim.animateTo(bright, animationSpec = tween(220))
        } else {
            while (true) {
                val target = nextColor(borderAnim.value)
                borderAnim.animateTo(
                    targetValue = target,
                    animationSpec = tween(1400, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    // 🔧 Возвращаем тонкий контур и уменьшенный glow, но чуть ярче по альфе
    val glowAlpha = if (isFocused) 0.38f else 0.26f   // яркость мягко ↑
    val lineAlpha = if (isFocused) 0.92f else 0.62f
    val strokeGlowDp = 8.dp       // как «было»
    val strokeLineDp = 1.6.dp     // как «было»

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp) // чтобы glow снизу не резался
            .drawBehind {
                if (fieldSize != IntSize.Zero) {
                    val corner = 16.dp.toPx()
                    val inset = 1.5.dp.toPx()
                    val strokeGlow = strokeGlowDp.toPx()
                    val strokeLine = strokeLineDp.toPx()

                    val w = fieldSize.width.toFloat()
                    val h = fieldSize.height.toFloat()

                    // Мягкое сияние
                    drawRoundRect(
                        color = borderAnim.value.copy(alpha = glowAlpha),
                        topLeft = Offset(inset, inset),
                        size = Size(w - inset * 2, h - inset * 2),
                        cornerRadius = CornerRadius(corner, corner),
                        style = Stroke(width = strokeGlow)
                    )
                    // Тонкая «живая» линия
                    drawRoundRect(
                        color = borderAnim.value.copy(alpha = lineAlpha),
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(w - inset * 2, h - inset * 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
                        style = Stroke(width = strokeLine)
                    )
                }
            }
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { fieldSize = it.size },
            interactionSource = interaction,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,   // свой бордер рисуем сами
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = bg,
                unfocusedContainerColor = bg,
                cursorColor = bright
            ),
            // Печаталка-плейсхолдер включена ТОЛЬКО если поле пустое и НЕ в фокусе
            placeholder = { TypewriterPlaceholder(enabled = !isFocused && input.isBlank()) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit(input) }),
            trailingIcon = {
                if (input.isNotBlank() || hasSelection) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Outlined.Close, contentDescription = "Очистить")
                    }
                }
            }
        )

        // Подсказки показываем при наборе (input не пуст) и если их не скрыли на время анимации
        DropdownSuggestions(
            expanded = suggestions.isNotEmpty() && input.isNotBlank() && !hideSuggestions,
            items = suggestions,
            onClick = onSuggestionClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 56.dp)
        )
    }
}


@Composable
private fun TypewriterPlaceholder(
    enabled: Boolean,
    hints: List<String> = listOf(
        "Найти iPhone 15",
        "Молоко 2.5%",
        "Кроссовки Nike 42",
        "Dyson Supersonic",
        "Крем SPF 50",
        "Подгузники размер 3"
    ),
    typingDelayMs: Long = 120,
    pauseAfterTypeMs: Long = 1200,
    deletingDelayMs: Long = 60,
    pauseBetweenHintsMs: Long = 900
) {
    var index by remember { mutableStateOf(0) }
    var visibleChars by remember { mutableStateOf(0) }
    var deleting by remember { mutableStateOf(false) }

    val current = hints[index % hints.size]

    // Мигающий курсор
    val blink by rememberInfiniteTransition(label = "cursor")
        .animateFloat(
            0f, 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "cursorAnim"
        )

    LaunchedEffect(enabled, index, deleting) {
        if (!enabled) {
            visibleChars = 0
            deleting = false
            return@LaunchedEffect
        }
        if (!deleting) {
            while (enabled && visibleChars < current.length) {
                delay(typingDelayMs)
                visibleChars++
            }
            if (!enabled) return@LaunchedEffect
            delay(pauseAfterTypeMs)
            deleting = true
        } else {
            while (enabled && visibleChars > 0) {
                delay(deletingDelayMs)
                visibleChars--
            }
            if (!enabled) return@LaunchedEffect
            delay(pauseBetweenHintsMs)
            deleting = false
            index = (index + 1) % hints.size
        }
    }

    val shown = if (enabled) current.take(visibleChars) else ""
    val cursor = if (enabled && blink > 0.5f) " _" else " " // «старый» курсор

    Text(
        shown + cursor,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
}

@Composable
private fun DropdownSuggestions(
    expanded: Boolean,
    items: List<String>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = expanded) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp) // не больше 4–6 пунктов по высоте
            ) {
                items(items) { item ->
                    SuggestionRow(
                        text = item,
                        onClick = { onClick(item) }
                    )
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
@Composable
private fun SuggestionBanner(
    count: Int?,
    onShowBest: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Найдено ${count?.toString() ?: "—"} предложений", style = MaterialTheme.typography.bodyMedium)
        Text("Хотите уточнить товар по характеристикам?", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onShowBest) { Text("Показать лучшее") }
            Button(onClick = onShowDetails) { Text("Показать подробнее") }
        }
    }
}



