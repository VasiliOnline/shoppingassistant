# Live values policy

Для аудио нельзя вручную поддерживать полный мировой реестр моделей. В пакете есть только head seed.

Runtime-контур должен:
1. извлекать кандидаты из title/description/OCR/URL;
2. складывать новые brand/family/model в live values;
3. проверять collision/route/identity;
4. продвигать частотные модели в будущий head seed delta только при необходимости.
