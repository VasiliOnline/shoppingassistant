# FASH.ACCESSORIES production pack v1.0 RU

**categoryCode:** `FASH.ACCESSORIES`  
**pack_type:** `category_schema_pack`  
**version:** `1.0.0`  
**language:** `ru`  
**created_at:** `2026-05-13`  
**status:** production

## Назначение

Пакет описывает управляемую fashion-ветку для аксессуаров: ремни, головные уборы, шарфы, перчатки, галстуки, солнцезащитные очки, украшения, обычные не-smart часы, аксессуары для волос, зонты и близкие small fashion items.

Ключевая идея: **FASH.ACCESSORIES не является корзиной для всего маленького**. Ветка должна срабатывать только после route guards против обуви, сумок, одежды, tech wearables/accessories, health/sport/tools и non-fashion омонимов.

## Что входит

| Группа | Типы |
|---|---|
| BELTS | `BELT`, `SUSPENDERS` |
| HEADWEAR | `HAT`, `CAP`, `BEANIE`, `BERET`, `BALACLAVA`, `PANAMA_HAT`, `VISOR`, `BANDANA`, `HEADBAND_FASHION` |
| NECKWEAR | `SCARF`, `SHAWL`, `SNOOD`, `STOLE`, `TIE`, `BOW_TIE`, `POCKET_SQUARE` |
| HANDWEAR | `GLOVES`, `MITTENS`, `FINGERLESS_GLOVES` |
| EYEWEAR | `SUNGLASSES`, `GLASSES_FRAME`, `EYEGLASS_CASE` |
| JEWELRY | `RING`, `EARRINGS`, `NECKLACE`, `BRACELET`, `BROOCH`, `PENDANT`, `CHAIN`, `ANKLET`, `CUFFLINKS`, `CHARM`, `JEWELRY_SET` |
| WATCHES | `WATCHES_NON_SMART`, `WATCH_STRAP` |
| HAIR_ACCESSORIES | `HAIR_CLIP`, `HAIR_BAND`, `SCRUNCHIE`, `HAIR_PIN`, `HAIR_COMB` |
| UMBRELLAS | `UMBRELLA` |
| OTHER_ACCESSORIES | `KEYCHAIN`, `OTHER_ACCESSORY` |

## Что не входит

- `FASH.SHOES`: кроссовки, сапоги, ботинки, туфли, сандалии, тапочки.
- `FASH.BAGS`: сумки, рюкзаки, чемоданы, кошельки, кардхолдеры, косметички, ремни для сумок.
- `FASH.APPAREL`: футболки, платья, куртки, брюки, джинсы, рубашки, юбки и другая одежда.
- `TECH.WEARABLES`: Apple Watch, Galaxy Watch, smart watch, фитнес-браслеты, Mi Band.
- `TECH.ACCESSORIES`: ремешки для Apple Watch / Galaxy Watch / Mi Band, чехлы для телефонов.
- `HEALTH/SPORT/TOOLS/AUTO`: медицинские маски, защитные очки, очки для плавания, ремень для йоги, ремень ГРМ.

## Route conflict layer

В пакет включен общий слой `route_conflict_layer.fash_common.v1_0.yaml`, чтобы apparel/shoes/bags/accessories не спорили между собой.

Рекомендуемый порядок:

1. `TECH.WEARABLES / TECH.ACCESSORIES`
2. `FASH.SHOES`
3. `FASH.BAGS`
4. `FASH.APPAREL`
5. `FASH.ACCESSORIES`
6. parent `FASH` / `OTHER`

Примеры:

| Query | Route |
|---|---|
| `женский кожаный ремень` | `FASH.ACCESSORIES` |
| `мужские кроссовки nike` | `FASH.SHOES` |
| `рюкзак nike` | `FASH.BAGS` |
| `женское платье zara` | `FASH.APPAREL` |
| `apple watch series 9` | `TECH.WEARABLES` |
| `ремешок для apple watch` | `TECH.ACCESSORIES` |
| `часы casio g shock` | `FASH.ACCESSORIES` |

## Brand/model maturity

Пакет использует режим:

- `brand_layer = brand_aware`
- `model_layer = optional_text_only`
- `curated_model_graph = false`

То есть `brand` нормализуется как бренд, а `model_name_text` хранится свободным текстом: `Ray-Ban Aviator`, `Casio G-Shock`, `Pandora Moments` и т.д. Обязательного графа моделей нет.

## Core attributes

Обязательные:

- `accessory_type`
- `condition`

Рекомендуемые:

- `accessory_group`
- `target_gender`
- `age_group`
- `brand`
- `color`
- `material_primary`

Type-specific поля включаются динамически по `accessory_type`: например, `belt_size_cm` для ремней, `head_size_cm` для головных уборов, `glove_size` для перчаток, `ring_size_ru` для колец, `case_diameter_mm` для часов.

## Vision policy

Vision может помогать с:

- coarse `accessory_type`,
- `color`,
- очевидным `pattern`,
- грубым material hint: textile / leather-like / metal / plastic / straw.

Vision **не должен** сам утверждать:

- бренд,
- модель,
- золото/серебро/бриллианты/жемчуг как подлинные материалы,
- UV400/поляризацию,
- водозащиту часов,
- smartwatch/non-smart статус без текстового или визуального smart-признака.

## Файлы пакета

| Файл | Назначение |
|---|---|
| `schema_pack.fash_accessories.v1_0.json` | основной контракт категории |
| `attributes.fash_accessories.v1_0.tsv` | атрибуты и применимость |
| `values.fash_accessories.v1_0.tsv` | value sets |
| `aliases.fash_accessories.ru.v1_0.tsv` | RU aliases и route-out aliases |
| `conditional_rules.fash_accessories.v1_0.yaml` | type-specific правила |
| `routing_guardrails.fash_accessories.v1_0.yaml` | guards против Shoes/Bags/Apparel/Tech/etc. |
| `route_conflict_layer.fash_common.v1_0.yaml` | общий FASH route conflict layer |
| `vision_rules.fash_accessories.v1_0.yaml` | visual extraction policy |
| `user_surface.fash_accessories.v1_0.yaml` | UI groups, listing form, suggestions |
| `facet_schema.fash_accessories.v1_0.yaml` | facet model |
| `golden_queries.fash_accessories.v1_0.tsv` | golden routing tests |
| `sample_offers.fash_accessories.v1_0.jsonl` | normalized sample offers |
| `integration_mapping.fash_accessories.v1_0.yaml` | integration contract |
| `quality_gates.fash_accessories.v1_0.yaml` | production gates |
| `validation_report.fash_accessories.v1_0.json` | validation report |
| `checksums.sha256.json` | SHA-256 checksums |
| `manifest.fash_accessories.v1_0.json` | file manifest |

## Pack counts

- Attributes: **67**
- Value rows: **298**
- Value sets: **32**
- Alias rows: **157**
- Golden queries: **54**  
  - Positive accessories: **32**
  - Route-out cases: **22**
- Sample offers: **15**

## Production notes

1. Wallet/cardholder intentionally route to `FASH.BAGS`.
2. Smartwatch and smartwatch straps intentionally route to `TECH`.
3. Jewelry is kept in `FASH.ACCESSORIES` by default; if the platform has a dedicated jewelry vertical, use `integration_mapping` to fork `JEWELRY`.
4. Any strong explicit route-out guard beats a weak accessory alias.
5. `target_gender` and `age_group` are attributes, not separate category branches.

