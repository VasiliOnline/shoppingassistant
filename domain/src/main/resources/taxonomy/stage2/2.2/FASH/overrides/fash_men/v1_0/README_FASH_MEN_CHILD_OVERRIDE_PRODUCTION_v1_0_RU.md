# FASH.MEN Child Override Production Pack v1.0

Категория: `FASH.MEN`  
Тип пакета: `child_override_pack`  
Наследует: `FASH.APPAREL_COMMON`  
Дата: 2026-05-12

## Назначение

Пакет подключает мужскую одежду как продуктовую ветку поверх общего стандарта одежды. Он **не дублирует** базовые атрибуты `FASH.APPAREL_COMMON`; он задаёт только child defaults, type priorities, мужские алиасы, routing guards, vision policy, user surface и фасетные overrides.

## Главное решение

`FASH.MEN = FASH.APPAREL_COMMON + male child overrides`.

Не создавать отдельные копии `color`, `size`, `material`, `condition`, `fit`, `season`, `sleeve_length`, `leg_fit`, `waist_rise` и других common-полей.

## Runtime-модель

- `target_gender.default = MEN`
- `target_gender.allowed = MEN, UNISEX`
- `age_group.default = ADULT`
- `age_group.allowed = ADULT, TEEN`
- `DRESS/SKIRT/BRA/BLOUSE/ROMPER/TIGHTS/TOP` не являются primary values для `FASH.MEN`
- route guards должны срабатывать до позитивных men aliases

## Интеграционный порядок

1. Убедиться, что `FASH.APPAREL_COMMON` подключён как shared standard.
2. Применить `common_dependency_patch.fash_apparel_common.v1_1_for_fash_men.yaml` или эквивалентные правила адаптера.
3. Импортировать child defaults, type priorities, value overrides, aliases, user/facet overrides.
4. Собрать effective spec и сравнить с `effective_spec_snapshot.expected.fash_men.v1_0.json`.
5. Прогнать golden queries и sample offers.

## Обязательные гейты

- `FASH_MEN_EXTENDS_COMMON`
- `NO_DUPLICATE_COMMON_ATTRIBUTES`
- `TARGET_GENDER_DEFAULT_MEN`
- `AGE_GROUP_ADULT_TEEN_ONLY`
- `BLOCKED_TYPES_NOT_PRIMARY`
- `NEGATIVE_ROUTE_GUARDS_GREEN`
- `VISION_NO_GENDER_GUESS_BY_COLOR`
- `EFFECTIVE_SPEC_SNAPSHOT_MATCHES`

## Почему пакет продуктовый

Пакет не пытается покрыть “всё на свете” отдельной мужской таксономией. Он оставляет широкий общий стандарт одежды в common layer, а для `FASH.MEN` добавляет только то, что меняет runtime-поведение: default gender/age, priorities, размеры, route safety, user surface, vision rules и тестовые fixtures.
