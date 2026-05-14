# Model seed boundary

`computer_model_head_seed` — это head seed для нормализации популярных и частых семейств/моделей, не полный мировой registry.

Новые модели должны проходить через `runtime_candidates/live_values` и не требуют нового categoryCode.

Запрещено создавать микрокатегории: `TECH.MACBOOK`, `TECH.THINKPAD`, `TECH.DELL_XPS`, `TECH.GAMING_LAPTOPS`.
