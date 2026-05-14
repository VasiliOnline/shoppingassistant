# TECH_PHONE_ACCESSORIES_public_category_pack_v1_0_RU

Production-grade static candidate для публичной ветки `TECH.PHONE_ACCESSORIES`.

## Назначение

Пакет покрывает аксессуары телефонов: чехлы, защитные стекла/плёнки, защиту камеры, зарядные устройства, беспроводные зарядки, кабели, power banks, держатели, подставки, автодержатели, адаптеры, доки, MagSafe-аксессуары, стилусы, ремешки/charm, SIM tools, запчасти и чистящие наборы.

## Граница

- Это public category pack: `categoryCode = TECH.PHONE_ACCESSORIES`.
- Главный type-атрибут: `phone_accessory_type`.
- Наследует: `TECH.ELECTRONICS_COMMON`, `TECH.SPECS_COMMON`, `TECH.COMPATIBILITY_COMMON`.
- Использует `TECH.PHONES` как target для compatibility resolution.
- Не создаёт `TECH.CASES`, `TECH.CHARGERS`, `TECH.CABLES`, `TECH.IPHONE_CASES`.
- Не содержит presets, collections, shelves, landing pages.

## Runtime-политика

Совместимость с моделью телефона нельзя угадывать по фото. Для model-specific claim нужно evidence: title, description, OCR packaging, barcode/vendor source или URL-source. Если ref на модель не resolved, но текст явный, допускается `TEXT_ONLY` publish с предупреждением.

## Статус

Static validation: PASS. Runtime validators проекта нужно прогнать после mount.
