
size/material queries
AI/photo-related queries, если применимо

Пример:

мужские кроссовки       -> FASH.SHOES
женское платье          -> FASH.WOMEN
детский рюкзак          -> FASH.BAGS
чехол iphone 13         -> TECH.PHONE_ACCESSORIES
iphone 13               -> TECH.PHONES
15. Sample offers

Нужны synthetic/runtime fixtures.

Пример:

{"title":"Кроссовки Nike мужские 42 белые", "expected":{"categoryCode":"FASH.SHOES","shoe_type":"SNEAKERS"}}
{"title":"Женская сумка через плечо кожаная", "expected":{"categoryCode":"FASH.BAGS","bag_type":"CROSSBODY_BAG"}}

Минимум:

20–40 для простой ветки
40–80 для широкой ветки
80+ для сложной ветки
16. Effective spec snapshot

Проверять нужно не только файлы пакета. Главное — что получилось после inheritance/overrides.

Файл:

effective_spec_snapshot.expected.<category>.v1_0.json

Проверяет:

categoryCode
inherited attributes
child overrides
required fields
facet attributes
hidden/system fields
allowed values
blocked/cross-route values
17. Quality gates

Обязательные gates:

Stage22SeedValidator
FacetSchemaValidator
EffectiveSpecSnapshotGate
RouteConflictGate
UserSurfaceGate
AliasCollisionGate
VisionRulesGate
SampleOffersSmokeGate

Специальные gates по архетипам:

CompatibilityGate
IdentityCandidateGate
SizePolicyGate
SafetyRegulatedGate
ConsumablePolicyGate
18. Как создавать новый пакет

Порядок:

1. выбрать categoryCode
2. выбрать pack_type
3. выбрать category_archetypes
4. понять, есть ли shared_standard
5. определить главный type-атрибут
6. задать T0_CORE
7. задать T1 поля по type
8. задать T2/T3
9. задать values только для head
10. задать user surface
11. задать facet templates
12. задать aliases + generated alias rules
13. задать route guards
14. задать vision rules
15. сделать golden queries
16. сделать sample offers
17. сделать integration_mapping
18. сделать effective spec snapshot
19. сделать quality_gates
20. сделать validation_report
19. Что нельзя менять без review
categoryCode
attributeCode
valueCode
semantic meaning
identity mode
public/shared статус ветки

Можно менять мягко:

порядок фасетов
user surface
aliases
generated alias rules
browse branches
priority values
runtime thresholds
20. FASH reference tree
FASH
├─ FASH.APPAREL_COMMON        [shared, не публичная]
├─ FASH.MEN                   [public]
├─ FASH.WOMEN                 [public]
├─ FASH.KIDS                  [public]
├─ FASH.SHOES                 [public]
├─ FASH.BAGS                  [public]
└─ FASH.ACCESSORIES           [public]

Принцип:

APPAREL_COMMON = общий слой одежды
MEN/WOMEN/KIDS = child override packs
SHOES/BAGS/ACCESSORIES = отдельные category_schema_pack
21. Что написать агенту перед генерацией пакета
Создай production-ready catalog pack по CATALOG_PACK_COMPANION_v1_RU.md.

Не раздувай дерево микрокатегориями.
Используй categoryCode как крупную стабильную ветку.
Детализацию делай через type, facets, user_surface, aliases и route guards.

Обязательно:
- выбрать pack_type
- выбрать category_archetypes
- задать roles T0/T1/T2/T3
- задать usage_scope
- использовать facet templates
- добавить generated alias rules
- добавить route guards
- добавить vision rules
- добавить golden_queries
- добавить sample_offers
- добавить integration_mapping
- добавить effective_spec_snapshot.expected
- добавить quality_gates
- не дублировать shared_standard
- seed only head values, long-tail через runtime candidates/live values
22. Что написать агенту перед подключением пакета
Подключи пакет в каталог по CATALOG_PACK_COMPANION_v1_RU.md.

Проверь:
- Stage22SeedValidator green
- FacetSchemaValidator green
- EffectiveSpecSnapshotGate green
- RouteConflictGate green
- AliasCollisionGate green
- UserSurfaceGate green
- T3_SYSTEM_HIDDEN не попадает в UI
- initial_fields <= 8
- primary_facets <= 7
- sample_offers проходят
- golden_queries проходят

После подключения создай короткий integration report:
- какие файлы подключены
- какие validators прошли
- какие route conflicts проверены
- какие known limitations остались
23. Финальное правило

Каталог должен быть:

богатый внутри
лёгкий в UI
строгий в validation
гибкий в runtime
экономный в ручных данных

Цель — не сделать меньше данных.
Цель — сделать меньше ручного дублирования и будущих переделок.