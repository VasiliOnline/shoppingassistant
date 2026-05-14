# Identity policy — TECH.DEVICE_IDENTITY_COMMON

1. `brand/family/line/model` — это identity surface, не дерево категорий.
2. Exact model допускается только с evidence.
3. `model_name_text` может хранить сырой или нормализованный текст модели, но не гарантирует exact model без `identity_resolution_status=FULL`.
4. `model_number`, `mpn`, `gtin/ean/upc` — сильные идентификаторы, но требуют consistency checks.
5. Compatibility context (`для`, `совместим`, `чехол для`) переводит model tokens в подсказку совместимости, а не в main product identity.
6. Bundle context требует split primary/secondary item и может блокировать auto-merge.
7. Serial/IMEI всегда hidden/masked/hashed.
