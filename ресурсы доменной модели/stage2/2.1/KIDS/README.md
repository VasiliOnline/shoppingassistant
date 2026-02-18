# Stage 2.1 L0 — KIDS (Товары для детей): пакет наполнения (product)

Версия: v1.0  
Дата: 2026-02-13  

## Состав пакета
- `GG_Taxonomy_Stage_2_1_L0_KIDS_Packages_v1_0_RU.docx` — master ТЗ для агента
- `browse_nodes.kids.tsv` — BrowseNode дерево для UI/роутинга
- `aliases.kids.tsv` — алиасы/синонимы + blocked noise
- `routing_rules.kids.yaml` — нормализация, дизамбигуация, scoring, fallback
- `queries_golden.kids.tsv` — golden-набор тестовых запросов (≥200)
- `coverage.kids.json` — конфиг coverage-гейта для CI/валидатора

## Ключевые дизамбигуации (важное)
- детская одежда/школьная форма/обувь детская → `FASH.KIDS`
- смартфоны/iphone → `TECH.PHONES`
- “корм/кот/собака” → НЕ KIDS (fallback в `HOME`/прочее)
- lego/игрушки/настолки → `KIDS.TOYS_GAMES`
- коляски/автокресла/isofix → `KIDS.STROLLERS_CARSEATS`
