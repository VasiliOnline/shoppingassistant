# TECH_DEVICE_IDENTITY_COMMON shared standard pack v1.0 RU

Статус: `production_ready_static_candidate`  
Тип: `shared_standard_pack`  
CategoryCode: `TECH.DEVICE_IDENTITY_COMMON`  
Public category: `false`  
Accepts offers directly: `false`

## Назначение

Пакет задаёт общий слой идентичности TECH-устройств: `brand`, `manufacturer`, `family`, `line`, `model`, `model_aliases`, `model_number`, `mpn`, `gtin/ean/upc`, `release_year`, `region_variant`, evidence/confidence/review/no-guess правила и dedup identity key.

Это НЕ public category и НЕ пакет телефонов. Пакет не создаёт `TECH.IPHONE`, `TECH.SAMSUNG_PHONES`, `TECH.MACBOOK`, `TECH.CASES`, `TECH.CHARGERS`, `TECH.CABLES`.

## Границы пакета

Включено:
- identity attribute contract;
- controlled head values для статусов/evidence/region/identifier schemes;
- common brand/family alias hints, но не полный model registry;
- route guardrails для device vs accessory/decorative/bundle contexts;
- vision/no-guess rules;
- golden queries и sample offers;
- effective spec snapshot;
- quality gates и validation report;
- checksums.

Не включено:
- `TECH.SPECS_COMMON`;
- `TECH.COMPATIBILITY_COMMON`;
- `TECH.PHONES`;
- `TECH.PHONE_ACCESSORIES`;
- подробный specs registry;
- compatibility refs;
- полный model head seed.

## Принцип no-guess

Точная модель не выводится только по форме, цвету, количеству камер или логотипу на фото. Для exact model нужен явный evidence: title, model number, MPN, GTIN/EAN/UPC, OCR на корпусе/упаковке, vendor feed, official DB match или подтверждение пользователя.

## Файлы

См. `package_inventory.json`.

## Runtime подключение

1. Смонтировать после `TECH_ELECTRONICS_COMMON_shared_standard_pack_v1_0_RU`.
2. Не подключать как public category.
3. Прогнать gates из `quality_gates.tech_device_identity_common.v1_0.yaml`.
4. Сравнить actual effective spec с `effective_spec_snapshot.expected.tech_device_identity_common.v1_0.json`.
5. Только после green gates разрешать downstream packs: `TECH_SPECS_COMMON`, `TECH_COMPATIBILITY_COMMON`, `TECH_PHONES_IDENTITY_PACK_v1_0_RU`.

## Release boundary

Пакет подготовлен как завершённый static production artifact. Внешние runtime validators проекта должны быть запущены в репозитории/registry после импорта.
