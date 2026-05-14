# TECH.MONITORS_DISPLAYS public category pack v1.0 RU

Пакет: `TECH_MONITORS_DISPLAYS_public_category_pack_v1_0_RU`  
categoryCode: `TECH.MONITORS_DISPLAYS`  
Тип: `public_category_pack`  
Главный type-атрибут: `display_type`

## Назначение

Базовое production-grade покрытие ветки мониторов и дисплеев:

- компьютерные мониторы
- игровые мониторы
- профессиональные мониторы
- portable monitors
- smart monitors
- touchscreen monitors
- monitor accessories

Пакет не создаёт микрокатегории и не содержит пресеты/коллекции/SEO-полки.

## Граница

Внутри: schema, attributes, values, aliases, user surface, facet templates, route guards, vision/no-guess rules, golden queries, sample offers, model head seed, effective snapshot, quality gates, validation report.

Не входит:

- `TECH.COMPUTER_ACCESSORIES`
- `TECH.PC_COMPONENTS`
- `TECH.TV_HOME_THEATER`
- presets / collections / shelves
- SEO landing pages
- полный мировой model registry

## Runtime политика

`display_type` управляет UI и фасетами.  
`brand/model/specs` живут как атрибуты и runtime candidates, не как categoryCode.

## Статус

Static data coverage: PASS  
Runtime release: pending project validators.
