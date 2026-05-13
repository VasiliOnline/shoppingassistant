# Goody Goods — FASH.WOMEN Child Override Production Pack v1.0

**categoryCode:** `FASH.WOMEN`  
**extends:** `FASH.APPAREL_COMMON`  
**pack_type:** `child_override_pack`  
**created_at:** 2026-05-12

Этот пакет — продуктовый child override для женской одежды. Он не дублирует общие атрибуты одежды, а наследует `FASH.APPAREL_COMMON` и добавляет только женские overrides: defaults, type priorities, size priorities, aliases, routing guardrails, vision rules, user surface, facet overrides и тестовые fixtures.

## Главная модель

```text
FASH.WOMEN = FASH.APPAREL_COMMON + women overrides
```

`FASH.APPAREL_COMMON` остаётся shared standard, а не публичной категорией оффера.

## Runtime defaults

- `target_gender.default = WOMEN`
- `target_gender.allowed = WOMEN, UNISEX`
- `age_group.default = ADULT`
- `age_group.allowed = ADULT, TEEN`

## Ключевые отличия от FASH.MEN

- `DRESS`, `SKIRT`, `BRA`, `BLOUSE`, `TOP`, `TIGHTS`, `SWIMWEAR` являются нормальными типами `FASH.WOMEN`.
- Generic basics (`TSHIRT`, `HOODIE`, `JEANS`, `JACKET`) без пола не должны автоматически финализироваться как `FASH.WOMEN` без контекста.
- Vision не должен угадывать `WOMEN` по цвету, принту, slim-fit или стилю.

## В пакете

- aliases: 251
- golden queries: 96
- sample offers: 52
- allowed apparel types: 46
- blocked/cross-route pseudo-types: 3
- type groups: 13
- value override rows: 97

## Перед merge в каталог

1. Применить/учесть `common_dependency_patch.fash_apparel_common.v1_1_for_fash_women.yaml`.
2. Подключить child overrides поверх `FASH.APPAREL_COMMON`.
3. Построить effective spec и сравнить с `effective_spec_snapshot.expected.fash_women.v1_0.json`.
4. Прогнать:
   - `Stage22SeedValidator`
   - `FacetSchemaValidator`
   - `FashWomenChildOverrideGate`
   - `FashRouteConflictGate`
   - `FashApparelInheritanceGate`
5. Прогнать `golden_queries` и `sample_offers`.

## Важное для UX

Форма не должна показывать полный общий профиль одежды. Начальный surface ограничен 8 полями, а type-specific поля открываются по `apparel_type`.

## Важное для AI/Vision

AI может уверенно определить тип одежды и видимые признаки, но не должен ставить `target_gender=WOMEN`, если evidence слабый. Для базовых унисекс-вещей лучше вернуть `UNISEX/UNKNOWN/NEED_MORE_CONTEXT`.
