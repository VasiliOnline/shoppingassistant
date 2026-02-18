# Stage 2.1 L0 — FOOD (Еда и продукты): пакет наполнения (product)

Версия: v1.0  
Дата: 2026-02-13  

## Состав пакета
- `GG_Taxonomy_Stage_2_1_L0_FOOD_Packages_v1_0_RU.docx` — master ТЗ для агента
- `browse_nodes.food.tsv` — BrowseNode дерево для UI/роутинга
- `aliases.food.tsv` — алиасы/синонимы + blocked noise
- `routing_rules.food.yaml` — нормализация, дизамбигуация, scoring, fallback
- `queries_golden.food.tsv` — golden-набор тестовых запросов (≥200)
- `coverage.food.json` — конфиг coverage-гейта для CI/валидатора

## Ключевые дизамбигуации (важное)
- корм/кот/собака + бренды кормов → `PETS.FOOD`
- витамины/омега/БАД → `BEAUTY.HEALTH`
- кофемашина/микроволновка/электрочайник → `APPL.SMALL`
- посуда/кастрюли/сковороды/контейнеры → `HOME.KITCHEN_DINING`
- “рецепт/как приготовить/калорийность/БЖУ” → BLOCKED (контент, не оффер)
