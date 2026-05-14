# AUDIO BRAND DELTA POLICY

## Почему это отдельный patch

Базовая ветка `TECH.AUDIO` data-covered, но брендовый фасет слабее, чем нужно для production UX. Вместо большого hardening-пакета выпускается лёгкий brand delta.

## Что не делаем

- Не создаём `TECH.HEADPHONES`, `TECH.AIRPODS`, `TECH.MICROPHONES`.
- Не добавляем пресеты и коллекции.
- Не создаём полный реестр моделей.
- Не усложняем publish validation.

## Как применять

1. Смонтировать после `TECH_AUDIO_public_category_pack_v1_0_RU`.
2. Append head brand values scoped to `TECH.AUDIO`.
3. Append aliases to brand alias index.
4. Запустить лёгкие gates.
5. Если PASS, ветка `TECH.AUDIO` получает статус `data_covered`.
