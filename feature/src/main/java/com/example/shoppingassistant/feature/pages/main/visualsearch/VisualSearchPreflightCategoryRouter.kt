package com.example.shoppingassistant.feature.pages.main.visualsearch

import com.example.shoppingassistant.domain.visualsearch.VisualSearchPreflightCategoryCandidate
import java.util.Locale

internal object VisualSearchPreflightCategoryRouter {

    fun route(
        selectedCategoryCode: String?,
        barcodeValue: String?,
        ocrTextHints: List<String>,
        imageLabelHints: List<String>,
        objectLabel: String?,
        objectConfidence: Float?,
    ): List<VisualSearchPreflightCategoryCandidate> {
        val ranked = linkedMapOf<String, RankedCategory>()
        selectedCategoryCode
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { code ->
                ranked.bump(
                    code = code,
                    score = 0.98f,
                    source = "user_category",
                    label = "selected",
                )
            }

        val weightedSignals = buildList {
            addAll(ocrTextHints.map { signal -> WeightedSignal(signal, "ocr", 0.18f) })
            addAll(imageLabelHints.map { signal -> WeightedSignal(signal, "image_label", 0.14f) })
            objectLabel?.let { signal ->
                add(
                    WeightedSignal(
                        raw = signal,
                        source = "object_label",
                        weight = 0.20f + ((objectConfidence ?: 0f).coerceIn(0f, 1f) * 0.14f),
                    ),
                )
            }
            barcodeValue
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { signal -> add(WeightedSignal(signal, "barcode", 0.05f)) }
        }

        val normalizedSignals = weightedSignals
            .map { signal -> signal to normalize(signal.raw) }
            .filter { (_, normalized) -> normalized.isNotBlank() }

        normalizedSignals.forEach { (signal, normalized) ->
            categoryRules.forEach { rule ->
                val matched = rule.match(normalized) ?: return@forEach
                if (
                    rule.categoryCode == "HOME.KITCHEN_DINING" &&
                    signal.source == "image_label" &&
                    !hasKitchenDiningConfirmation(normalizedSignals)
                ) {
                    return@forEach
                }
                ranked.bump(
                    code = rule.categoryCode,
                    score = (rule.baseScore + signal.weight + matched.weight).coerceAtMost(0.96f),
                    source = signal.source,
                    label = matched.label,
                )
            }
        }

        applyDominantObjectGuards(
            ranked = ranked,
            normalizedSignals = normalizedSignals.map { (_, normalized) -> normalized },
            hasUserCategory = !selectedCategoryCode.isNullOrBlank(),
        )

        return ranked.values
            .sortedWith(compareByDescending<RankedCategory> { it.score }.thenBy { it.code })
            .take(maxCandidates)
            .map { item ->
                VisualSearchPreflightCategoryCandidate(
                    categoryCode = item.code,
                    confidence = item.score.coerceIn(0f, 1f),
                    source = item.sources.joinToString("+"),
                    label = item.labels.joinToString(", ").take(maxLabelLength),
                )
            }
    }

    private fun applyDominantObjectGuards(
        ranked: LinkedHashMap<String, RankedCategory>,
        normalizedSignals: List<String>,
        hasUserCategory: Boolean,
    ) {
        val mouseSignal = normalizedSignals.firstOrNull(::containsComputerMouseSignal) ?: return
        if (!hasUserCategory) {
            ranked.remove("HOME.KITCHEN_DINING")
        }
        ranked.bump(
            code = "TECH.PC_COMPONENTS",
            score = 0.96f,
            source = "dominant_object",
            label = mouseSignal.take(maxLabelLength),
        )
    }

    private fun LinkedHashMap<String, RankedCategory>.bump(
        code: String,
        score: Float,
        source: String,
        label: String,
    ) {
        val existing = this[code]
        if (existing == null) {
            this[code] = RankedCategory(
                code = code,
                score = score,
                sources = linkedSetOf(source),
                labels = linkedSetOf(label),
            )
            return
        }
        existing.score = maxOf(existing.score, score)
        existing.sources.add(source)
        existing.labels.add(label)
    }

    private data class WeightedSignal(
        val raw: String,
        val source: String,
        val weight: Float,
    )

    private data class RankedCategory(
        val code: String,
        var score: Float,
        val sources: LinkedHashSet<String>,
        val labels: LinkedHashSet<String>,
    )

    private data class CategoryRule(
        val categoryCode: String,
        val baseScore: Float,
        val positiveTerms: List<WeightedTerm>,
        val negativeTerms: List<String> = emptyList(),
    ) {
        fun match(normalized: String): WeightedTerm? {
            if (negativeTerms.any { term -> normalized.contains(term) }) return null
            return positiveTerms
                .filter { term -> normalized.contains(term.term) }
                .maxByOrNull { term -> term.weight }
        }
    }

    private data class WeightedTerm(
        val term: String,
        val weight: Float,
        val label: String = term,
    )

    private fun normalize(raw: String): String =
        raw
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace(symbolsRegex, " ")
            .replace(spacingRegex, " ")
            .trim()

    private fun terms(vararg values: Pair<String, Float>): List<WeightedTerm> =
        values.map { (term, weight) -> WeightedTerm(normalize(term), weight, term) }

    private fun containsComputerMouseSignal(normalized: String): Boolean {
        if (computerMouseStrongTerms.any { term -> normalized.contains(term) }) return true
        val hasLooseMouseToken = computerMouseLooseTerms.any { term ->
            normalized == term ||
                normalized.startsWith("$term ") ||
                normalized.endsWith(" $term") ||
                normalized.contains(" $term ")
        }
        if (!hasLooseMouseToken) return false
        return computerMouseAnimalTerms.none { term -> normalized.contains(term) }
    }

    private fun containsSpecificKitchenDiningSignal(normalized: String): Boolean =
        specificKitchenDiningTerms.any { term -> normalized.contains(term) }

    private fun containsKitchenDiningSignal(normalized: String): Boolean =
        containsSpecificKitchenDiningSignal(normalized) ||
            broadKitchenImageLabelTerms.any { term -> normalized.contains(term) }

    private fun hasKitchenDiningConfirmation(signals: List<Pair<WeightedSignal, String>>): Boolean =
        signals.any { (signal, normalized) ->
            signal.source != "image_label" && containsKitchenDiningSignal(normalized)
        }

    private val symbolsRegex = Regex("[^\\p{L}\\p{N}]+")
    private val spacingRegex = Regex("\\s+")
    private const val maxCandidates = 5
    private const val maxLabelLength = 96
    private val computerMouseStrongTerms = listOf(
        "computer mouse",
        "wireless mouse",
        "gaming mouse",
        "pc mouse",
        "optical mouse",
        "mouse pad",
        "mousepad",
        "компьютерная мышь",
        "беспроводная мышь",
        "игровая мышь",
        "оптическая мышь",
        "коврик для мыши",
    ).map(::normalize)
    private val computerMouseLooseTerms = listOf("mouse", "мышь").map(::normalize)
    private val computerMouseAnimalTerms = listOf(
        "animal",
        "rodent",
        "pet",
        "rat",
        "mickey",
        "minnie",
        "грызун",
        "мышонок",
        "крыса",
        "микки",
    ).map(::normalize)
    private val kitchenDiningNegativeTerms = listOf(
        "computer mouse",
        "wireless mouse",
        "gaming mouse",
        "pc mouse",
        "optical mouse",
        "mouse pad",
        "mousepad",
        "keyboard",
        "usb",
        "dpi",
        "logitech",
        "razer",
        "компьютерная мышь",
        "беспроводная мышь",
        "игровая мышь",
        "оптическая мышь",
        "коврик для мыши",
        "клавиатура",
    ).map(::normalize)
    private val broadKitchenImageLabelTerms = listOf(
        "tableware",
        "dishware",
        "cutlery",
        "flatware",
        "silverware",
        "serveware",
        "kitchenware",
        "utensil",
        "utensils",
        "посуда",
        "столовые приборы",
        "приборы",
    ).map(::normalize)
    private val specificKitchenDiningTerms = listOf(
        "knife",
        "fork",
        "spoon",
        "mug",
        "cup",
        "plate",
        "нож",
        "вилка",
        "ложка",
        "кружка",
        "чашка",
        "тарелка",
    ).map(::normalize)

    private val categoryRules = listOf(
        CategoryRule(
            categoryCode = "TECH.PHONE_ACCESSORIES",
            baseScore = 0.56f,
            positiveTerms = terms(
                "phone charger" to 0.28f,
                "charger" to 0.24f,
                "charging" to 0.22f,
                "adapter" to 0.22f,
                "power bank" to 0.22f,
                "usb c" to 0.20f,
                "type c" to 0.20f,
                "cable" to 0.18f,
                "case" to 0.18f,
                "зарядка" to 0.26f,
                "зарядное" to 0.24f,
                "адаптер" to 0.22f,
                "кабель" to 0.18f,
                "пауэрбанк" to 0.22f,
                "чехол" to 0.18f,
                "5v" to 0.12f,
                "9v" to 0.12f,
                "65w" to 0.14f,
            ),
        ),
        CategoryRule(
            categoryCode = "TECH.PHONES",
            baseScore = 0.58f,
            positiveTerms = terms(
                "smartphone" to 0.28f,
                "cell phone" to 0.26f,
                "iphone" to 0.28f,
                "samsung galaxy" to 0.26f,
                "galaxy s" to 0.24f,
                "galaxy z" to 0.24f,
                "realme" to 0.22f,
                "xiaomi" to 0.22f,
                "смартфон" to 0.28f,
                "телефон" to 0.20f,
                "айфон" to 0.24f,
            ),
            negativeTerms = listOf("charger", "adapter", "заряд", "чехол", "кабель"),
        ),
        CategoryRule(
            categoryCode = "TECH.COMPUTERS",
            baseScore = 0.58f,
            positiveTerms = terms(
                "laptop" to 0.28f,
                "notebook" to 0.24f,
                "macbook" to 0.28f,
                "ultrabook" to 0.22f,
                "intel core" to 0.20f,
                "intel arc" to 0.20f,
                "ноутбук" to 0.28f,
                "ноут" to 0.22f,
            ),
        ),
        CategoryRule(
            categoryCode = "TECH.PC_COMPONENTS",
            baseScore = 0.60f,
            positiveTerms = terms(
                "computer mouse" to 0.34f,
                "wireless mouse" to 0.34f,
                "gaming mouse" to 0.34f,
                "pc mouse" to 0.32f,
                "optical mouse" to 0.32f,
                "mouse pad" to 0.26f,
                "mousepad" to 0.26f,
                "mouse" to 0.28f,
                "keyboard" to 0.18f,
                "ssd" to 0.18f,
                "gpu" to 0.18f,
                "graphics card" to 0.22f,
                "компьютерная мышь" to 0.34f,
                "беспроводная мышь" to 0.34f,
                "игровая мышь" to 0.34f,
                "оптическая мышь" to 0.32f,
                "коврик для мыши" to 0.26f,
                "клавиатура" to 0.18f,
                "мышь" to 0.28f,
                "видеокарта" to 0.22f,
            ),
        ),
        CategoryRule(
            categoryCode = "TECH.TABLETS_E_READERS",
            baseScore = 0.56f,
            positiveTerms = terms(
                "tablet" to 0.24f,
                "ipad" to 0.26f,
                "kindle" to 0.22f,
                "e reader" to 0.22f,
                "планшет" to 0.24f,
                "электронная книга" to 0.22f,
            ),
        ),
        CategoryRule(
            categoryCode = "TECH.TV_HOME_THEATER",
            baseScore = 0.54f,
            positiveTerms = terms(
                "television" to 0.24f,
                "smart tv" to 0.24f,
                "tv" to 0.16f,
                "projector" to 0.18f,
                "телевизор" to 0.24f,
                "проектор" to 0.18f,
            ),
        ),
        CategoryRule(
            categoryCode = "TECH.AUDIO",
            baseScore = 0.54f,
            positiveTerms = terms(
                "headphones" to 0.24f,
                "earphones" to 0.22f,
                "speaker" to 0.18f,
                "soundbar" to 0.20f,
                "наушники" to 0.24f,
                "колонка" to 0.18f,
            ),
        ),
        CategoryRule(
            categoryCode = "TECH.CAMERAS_DRONES",
            baseScore = 0.54f,
            positiveTerms = terms(
                "camera" to 0.22f,
                "camcorder" to 0.20f,
                "lens" to 0.18f,
                "gopro" to 0.22f,
                "фотоаппарат" to 0.22f,
                "камера" to 0.16f,
                "объектив" to 0.18f,
            ),
        ),
        CategoryRule(
            categoryCode = "TECH.GAMING",
            baseScore = 0.58f,
            positiveTerms = terms(
                "xbox" to 0.30f,
                "playstation" to 0.30f,
                "ps5" to 0.28f,
                "nintendo" to 0.26f,
                "game console" to 0.28f,
                "video game console" to 0.28f,
                "controller" to 0.18f,
                "dualsense" to 0.24f,
                "игровая приставка" to 0.28f,
                "приставка" to 0.20f,
                "геймпад" to 0.20f,
            ),
        ),
        CategoryRule(
            categoryCode = "TECH.SMART_HOME_SECURITY",
            baseScore = 0.52f,
            positiveTerms = terms(
                "smart bulb" to 0.22f,
                "smart plug" to 0.22f,
                "zigbee" to 0.22f,
                "датчик" to 0.18f,
                "умная лампа" to 0.22f,
                "умный дом" to 0.22f,
            ),
        ),
        CategoryRule(
            categoryCode = "APPL.SMALL",
            baseScore = 0.58f,
            positiveTerms = terms(
                "microwave" to 0.28f,
                "kettle" to 0.24f,
                "electric kettle" to 0.28f,
                "blender" to 0.22f,
                "vacuum" to 0.22f,
                "robot vacuum" to 0.28f,
                "coffee machine" to 0.24f,
                "bbk" to 0.18f,
                "микроволновка" to 0.28f,
                "свч" to 0.24f,
                "чайник" to 0.24f,
                "блендер" to 0.22f,
                "пылесос" to 0.22f,
                "кофемашина" to 0.24f,
            ),
        ),
        CategoryRule(
            categoryCode = "APPL.MAJOR",
            baseScore = 0.54f,
            positiveTerms = terms(
                "refrigerator" to 0.24f,
                "washing machine" to 0.24f,
                "dishwasher" to 0.22f,
                "oven" to 0.16f,
                "fridge" to 0.22f,
                "холодильник" to 0.24f,
                "стиральная машина" to 0.24f,
                "посудомойка" to 0.22f,
                "духовой шкаф" to 0.20f,
            ),
        ),
        CategoryRule(
            categoryCode = "APPL.CLIMATE",
            baseScore = 0.54f,
            positiveTerms = terms(
                "air conditioner" to 0.24f,
                "heater" to 0.20f,
                "humidifier" to 0.20f,
                "fan" to 0.16f,
                "кондиционер" to 0.24f,
                "обогреватель" to 0.20f,
                "увлажнитель" to 0.20f,
            ),
        ),
        CategoryRule(
            categoryCode = "HOME.KITCHEN_DINING",
            baseScore = 0.50f,
            positiveTerms = terms(
                "tableware" to 0.18f,
                "dishware" to 0.18f,
                "cutlery" to 0.20f,
                "flatware" to 0.18f,
                "silverware" to 0.18f,
                "knife" to 0.16f,
                "fork" to 0.16f,
                "spoon" to 0.16f,
                "mug" to 0.16f,
                "cup" to 0.14f,
                "plate" to 0.14f,
                "посуда" to 0.18f,
                "столовые приборы" to 0.20f,
                "приборы" to 0.14f,
                "нож" to 0.16f,
                "вилка" to 0.16f,
                "ложка" to 0.16f,
                "кружка" to 0.16f,
                "тарелка" to 0.14f,
            ),
            negativeTerms = kitchenDiningNegativeTerms,
        ),
        CategoryRule(
            categoryCode = "HOME.TEXTILES",
            baseScore = 0.50f,
            positiveTerms = terms(
                "pillow" to 0.16f,
                "blanket" to 0.16f,
                "curtain" to 0.16f,
                "bedding" to 0.18f,
                "подушка" to 0.16f,
                "одеяло" to 0.16f,
                "штора" to 0.16f,
            ),
        ),
        CategoryRule(
            categoryCode = "HOME.GARDEN",
            baseScore = 0.48f,
            positiveTerms = terms(
                "bouquet" to 0.20f,
                "flowers" to 0.18f,
                "rose" to 0.14f,
                "plant" to 0.14f,
                "букет" to 0.20f,
                "цветы" to 0.18f,
                "роза" to 0.14f,
                "растение" to 0.14f,
            ),
        ),
        CategoryRule(
            categoryCode = "FASH.SHOES",
            baseScore = 0.52f,
            positiveTerms = terms(
                "shoe" to 0.22f,
                "sneaker" to 0.22f,
                "boot" to 0.18f,
                "обувь" to 0.22f,
                "кроссовки" to 0.22f,
                "ботинки" to 0.18f,
            ),
        ),
        CategoryRule(
            categoryCode = "FASH.BAGS",
            baseScore = 0.52f,
            positiveTerms = terms(
                "bag" to 0.18f,
                "backpack" to 0.22f,
                "handbag" to 0.20f,
                "сумка" to 0.18f,
                "рюкзак" to 0.22f,
            ),
        ),
        CategoryRule(
            categoryCode = "BEAUTY.DEVICES",
            baseScore = 0.52f,
            positiveTerms = terms(
                "hair dryer" to 0.22f,
                "trimmer" to 0.18f,
                "shaver" to 0.18f,
                "фен" to 0.22f,
                "триммер" to 0.18f,
                "бритва" to 0.18f,
            ),
        ),
        CategoryRule(
            categoryCode = "KIDS.TOYS_GAMES",
            baseScore = 0.58f,
            positiveTerms = terms(
                "toy" to 0.24f,
                "robot dog" to 0.30f,
                "robot toy" to 0.28f,
                "lego" to 0.24f,
                "doll" to 0.20f,
                "puzzle" to 0.20f,
                "board game" to 0.22f,
                "remote control toy" to 0.26f,
                "игрушка" to 0.24f,
                "робот собака" to 0.30f,
                "робот" to 0.18f,
                "конструктор" to 0.22f,
                "кукла" to 0.20f,
                "пазл" to 0.20f,
            ),
            negativeTerms = listOf("robot vacuum", "робот пылесос"),
        ),
        CategoryRule(
            categoryCode = "PETS.ACCESSORIES",
            baseScore = 0.46f,
            positiveTerms = terms(
                "dog collar" to 0.18f,
                "pet toy" to 0.16f,
                "leash" to 0.18f,
                "ошейник" to 0.18f,
                "поводок" to 0.18f,
            ),
        ),
        CategoryRule(
            categoryCode = "SPORT.FITNESS",
            baseScore = 0.50f,
            positiveTerms = terms(
                "dumbbell" to 0.20f,
                "fitness" to 0.16f,
                "yoga mat" to 0.18f,
                "гантель" to 0.20f,
                "коврик для йоги" to 0.18f,
            ),
        ),
    )
}
