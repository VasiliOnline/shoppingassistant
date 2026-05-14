# TECH.COMPUTERS public category pack v1.0 RU

**Pack:** `TECH_COMPUTERS_public_category_pack_v1_0_RU`  
**categoryCode:** `TECH.COMPUTERS`  
**pack_type:** `public_category_pack`  
**status:** `production_ready_static_candidate`

## Назначение

Пакет закрывает базовую production-ветку `TECH.COMPUTERS`: ноутбуки, ультрабуки, игровые ноутбуки, 2-в-1, Chromebook, desktop PC, mini PC, моноблоки, рабочие станции и тонкие клиенты.

## Границы

Включено: schema, attributes, values, aliases, model head seed, user surface, facet templates, route guards, vision/no-guess rules, golden queries, sample offers, publish fixtures, integration mapping, quality gates, expected snapshot и checksums.

Не включено: presets/collections/shelves/SEO-лендинги, отдельные public packs для планшетов, мониторов, аксессуаров, компонентов и накопителей, а также микрокатегории `TECH.MACBOOK`, `TECH.THINKPAD`, `TECH.LAPTOPS`.

## Runtime policy

Модель/CPU/GPU/RAM/storage нельзя угадывать по фото без evidence. Визуально можно извлечь только coarse type, цвет и видимые признаки состояния. Детальные specs — через текст, OCR, structured source или seller fields.

## Validators after mount

Запустить: Stage22SeedValidator, FacetSchemaValidator, EffectiveSpecSnapshotGate, RouteConflictGate, AliasCollisionGate, UserSurfaceGate, VisionRulesGate, SampleOffersSmokeGate, IdentityCandidateGate, SpecsCoverageGate, DedupKeyGate.
