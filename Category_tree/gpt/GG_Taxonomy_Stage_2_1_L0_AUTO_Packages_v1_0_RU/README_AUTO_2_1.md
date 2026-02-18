# Stage 2.1 L0 — AUTO (Авто и транспорт): пакет наполнения (product)

Версия: v1.0  
Дата: 2026-02-13  

## Состав пакета
- `GG_Taxonomy_Stage_2_1_L0_AUTO_Packages_v1_0_RU.docx` — master ТЗ для агента
- `browse_nodes.auto.tsv` — BrowseNode дерево для UI/роутинга
- `aliases.auto.tsv` — алиасы/синонимы + blocked noise
- `routing_rules.auto.yaml` — нормализация, дизамбигуация, scoring, fallback
- `queries_golden.auto.tsv` — golden-набор тестовых запросов (≥200)
- `coverage.auto.json` — конфиг coverage-гейта для CI/валидатора

## Ключевые дизамбигуации (важное)
- “детское автокресло/isofix/автолюлька/коляска” → `KIDS.STROLLERS_CARSEATS`
- “кроватка детская/манеж/барьер безопасности” → `KIDS.NURSERY_FURNITURE`
- “дрель/шуруповерт/перфоратор …” → `HOME.REPAIR_TOOLS`
- “зарядка usb-c/powerbank” → `TECH.PHONES`
- контентные запросы “как/инструкция/схема/pdf/каталог” → BLOCKED
