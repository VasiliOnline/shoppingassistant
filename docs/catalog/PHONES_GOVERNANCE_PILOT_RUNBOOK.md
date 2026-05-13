# TECH.PHONES Governance Pilot Runbook

## Что такое live pilot-loop

`TECH.PHONES` сейчас работает как пилотный governance-refresh контур.

Поток данных такой:

1. `source registry`
   Регистрируются official sources для `Apple / Samsung / Google` и curated seed packs.
2. `manual or scheduled trigger`
   Refresh запускается либо scheduler'ом, либо вручную из debug/admin surface.
3. `connector fetch`
   Connector ходит в официальный источник бренда, получает страницы/spec payload и превращает их в `CatalogGovernanceCuratedSeedPack`.
4. `normalize + scope`
   Значения нормализуются в наши canonical codes и раскладываются по `brand / family / model / value / alias`.
5. `governance sync`
   Данные не пишутся напрямую в UI. Они сначала попадают в governance core через curated-seed sync path.
6. `publish`
   Если source помечен `autoPublish=true`, пересобираются serving artifacts и идёт runtime invalidation.
7. `review queue`
   Если появляются спорные кандидаты, они видны в review queue и требуют `Approve / Reject / Promote`.

Итог: official refresh сейчас не обходит governance. Он идёт через governance sync и только потом попадает в runtime.

## Добавляются ли данные сразу в каталог

Не всегда.

- `safe + autoPublish=true`
  Данные попадают в governance, потом сразу в serving artifacts и становятся видны runtime/UI.
- `manual publish`
  Данные доходят только до governance core и ждут rebuild/publish.
- `review-required`
  Кандидаты попадают в review queue и не становятся частью serving автоматически.

Для текущего phones-pilot official sources `Apple / Samsung / Google` настроены как `autoPublish=true`, поэтому успешный refresh обычно сразу обновляет runtime.

## Что смотреть в админке

Экран: `Profile -> Catalog governance`

### 1. Scheduler loop

Показывает:

- включён ли scheduled refresh
- какой интервал
- какая категория сейчас под автоматическим обновлением
- сколько sources реально матчится под scheduler
- статус и время последнего run

Если здесь `matched sources = 0` или `latest run != COMPLETED/PUBLISHED`, phones-pilot требует внимания.

### 2. Coverage control

Это сводка по веткам.

Она строится из:

- `readiness inventory` по всем категориям
- текущего phones refresh status

Сигналы:

- `WARNING`
  Ветка не готова по coverage/readiness.
- `CRITICAL`
  Для automated ветки refresh loop сломан, устарел или не публикует.

Сейчас автоматический refresh учитывается только для `TECH.PHONES`.
Остальные ветки пока оцениваются по readiness/coverage, без automated refresh сигнала.

### 3. Sources

Показывает зарегистрированные источники:

- display name
- connector type
- source URI
- brand code
- endpoint count
- auto-publish flag

Кнопка `Refresh source` запускает refresh только для одного источника.

### 4. Refresh runs

Показывает последние refresh execution:

- registry code
- status
- сколько моделей/значений/alias было синхронизировано
- publish status
- время завершения

Если run завершился `FAILED`, смотреть publish log и server logs.

### 5. Review queue

Здесь спорные кандидаты:

- attribute code
- normalized value
- score
- recommendation
- existing target

Действия:

- `Approve`
  Кандидат принят как корректный governance candidate, но не промоутится в serving.
- `Reject`
  Кандидат отклонён.
- `Promote`
  Кандидат промоутится в канон и сразу проходит publish/rebuild loop.

### 6. Publish log

Показывает фактические события публикации:

- `REFRESH_SYNC`
- `REVIEW_PROMOTE`
- `REFRESH_RUNTIME_INVALIDATION`
- manual rebuild events

Если refresh run есть, а publish log пустой или `FAILED`, данные в runtime не доехали.

## Как вручную прогонять phones pilot

### Из админки

1. Открыть `Profile -> Catalog governance`
2. Убедиться, что в `Scheduler loop` есть `TECH.PHONES`
3. В `Sources` нажать:
   - `Refresh source` у `Apple`
   - потом у `Samsung`
   - потом у `Google`
4. Обновить экран (`Reload`)
5. Проверить:
   - `Refresh runs`
   - `Review queue`
   - `Publish log`

### Из тестов

Live smoke:

```powershell
$env:CATALOG_GOVERNANCE_LIVE_REFRESH_SMOKE='true'
.\gradlew.bat --no-daemon :server:test --tests "com.example.shoppingassistant.server.catalog.CatalogGovernanceRepositoryIntegrationTest.refreshSurface_liveOfficialPhones_sourceBySource_smoke"
```

Что считается хорошим результатом:

- run status = `COMPLETED`
- publish status = `PUBLISHED`
- `models / values / aliases > 0`
- publish events записаны
- runtime invalidation событие записано

## Как отлаживать проблемы

### Симптом: source refresh не стартует

Проверить:

- source есть в `Sources`
- `enabled=true`
- scheduler/trigger category = `TECH.PHONES`

### Симптом: run есть, но новых данных не видно в UI

Проверить:

- `publish status = PUBLISHED`
- есть `REFRESH_RUNTIME_INVALIDATION` в publish log
- нет ли review-required кейсов
- после этого перезапустить экран/перезагрузить данные клиента

### Симптом: спорные новые значения

Смотреть `Review queue`.
Не промоутить вслепую vendor marketing names, если неясно:

- это новый canonical value
- alias существующего значения
- market-specific SKU artifact

### Симптом: ветка отстаёт по покрытию

Смотреть `Coverage control`.

Если ветка в `WARNING`, обычно причина одна из:

- readiness не `READY`
- completeness gate не пройден
- есть blocking issues

Для phones `CRITICAL` обычно означает проблему refresh loop:

- scheduler выключен
- нет sources
- refresh давно не запускался
- latest run failed

## Как понимать, что пора rebuild

В pilot-loop руками это почти не нужно.

Нормальный порядок:

1. refresh source
2. sync governance
3. auto publish
4. runtime invalidation

Manual `Rebuild` нужен, когда:

- source был `autoPublish=false`
- промоутнули кандидата вручную
- нужно перепересобрать serving artifacts после ручной отладки

## Что сейчас считается сигналом, что ветке нужно покрытие

- readiness не `READY`
- completeness gate не пройден
- есть blocking issues
- в phones-пилоте refresh loop не здоров

## Что ещё не автоматизировано

- automated refresh пока только для `TECH.PHONES`
- coverage-control использует refresh-health только для phones
- остальные ветки пока контролируются readiness/coverage, а не scheduled refresh loop
