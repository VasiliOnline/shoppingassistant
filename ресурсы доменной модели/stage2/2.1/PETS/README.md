# Stage 2.1 L0 — PETS (Питомцы): пакет наполнения (product)

Версия: v1.0  
Дата: 2026-02-13  

## Состав пакета
- `GG_Taxonomy_Stage_2_1_L0_PETS_Packages_v1_0_RU.docx` — master ТЗ для агента
- `browse_nodes.pets.tsv` — BrowseNode дерево для UI/роутинга
- `aliases.pets.tsv` — алиасы/синонимы + blocked noise
- `routing_rules.pets.yaml` — нормализация, дизамбигуация, scoring, fallback
- `queries_golden.pets.tsv` — golden-набор тестовых запросов (≥200)
- `coverage.pets.json` — конфиг coverage-гейта для CI/валидатора

## Ключевые дизамбигуации (важное)
- “витамины/омега/БАД” без pet‑токенов → `BEAUTY.HEALTH`
- “шампунь/бальзам” без “для собак/кошек” → `BEAUTY.HAIRCARE`
- хлеб/молоко/сыр и прочие продукты → `FOOD.GROCERIES`
- грунт/удобрение → `HOME.GARDEN`
- how‑to (“как приучить/дрессировать”) → BLOCKED
