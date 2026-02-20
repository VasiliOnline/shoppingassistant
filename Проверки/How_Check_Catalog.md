Отлично. Практический чек-лист:

Когда запускать

Ежедневно на prod после ночных обновлений данных.
После каждого релиза каталога (dataVersion) перед/после выката.
После любых изменений пресетов/фасетов/профилей.
После инцидента поиска (резкий рост пустых результатов).
Где смотреть

SQL: catalog_model_backlog.sql
SLA и процесс: PROD_BACKLOG_LOOP.md
Миграции и parity: MIGRATION_PARITY_RUNBOOK.md
Наблюдаемость пресетов: PRESET_OBSERVABILITY_RUNBOOK.md
Как прогонять

psql "host=$DB_HOST port=$DB_PORT dbname=$DB_NAME user=$DB_USER password=$DB_PASSWORD sslmode=require" \
  -f server/src/main/resources/db/checks/catalog_model_backlog.sql \
  -o "catalog_backlog_prod_$(date +%F).txt"
Как читать отчет

ZERO_RESULTS
Сигнал: выдача пустая (items_json=[]).
Срочность выше, SLA уже в SQL: +3 дня.

UNKNOWN_ATTRIBUTE
Сигнал: в живых офферах есть ключи, которых нет в профиле категории.
SLA: +7 дней.

NORMALIZATION_CONFLICT
Сигнал: значение не совпало со словарем (canonical/synonyms).
SLA: +7 дней.

Как формировать задачи автоматически (Jira/Linear)

Для каждой строки результата строишь уникальный ключ:
issue_type|category_code|issue_key
Если ключа нет в трекере, создаешь задачу.
Если ключ уже есть, обновляешь issue_count, last_seen_at, sla_due_date.
Если ключ исчез из отчета 3 запуска подряд, закрываешь задачу как resolved.
Приоритет ставишь по типу и объему:
ZERO_RESULTS с большим issue_count -> High
UNKNOWN_ATTRIBUTE/NORMALIZATION_CONFLICT -> Medium/High по объему
Минимальные поля задачи

Title: [Catalog][ZERO_RESULTS] FOOD.DRINKS | no-items
Description: source SQL + category + issue_key + issue_count + first/last_seen
Labels: catalog, backlog, issue_type
Due date: из sla_due_date
Owner: команда taxonomy/search
Когда руками, когда автоматом

Руками: первые 1-2 недели, чтобы проверить качество сигналов.
Автоматом: когда ключи стабильны и нет ложных срабатываний.
Обязательно автоматика для prod, для staging можно weekly.
Если хочешь, следующим шагом дам готовый PowerShell скрипт, который:

запускает SQL,
парсит результат,
делает upsert задач в Jira или Linear по этому ключу.

---

Проверка observability пресетов (prod)

Ежедневный quality-check:

psql "host=$DB_HOST port=$DB_PORT dbname=$DB_NAME user=$DB_USER password=$DB_PASSWORD sslmode=require" \
  -f server/src/main/resources/db/checks/catalog_preset_observability_checks.sql \
  -o "catalog_preset_observability_checks_prod_$(date +%F).txt"

Ежемесячный отчет default vs non-default:

psql "host=$DB_HOST port=$DB_PORT dbname=$DB_NAME user=$DB_USER password=$DB_PASSWORD sslmode=require" \
  -f server/src/main/resources/db/checks/catalog_preset_monthly_report.sql \
  -o "catalog_preset_monthly_report_prod_$(date +%F).txt"

Что контролировать:

invalid_required_fields_count = 0
invalid_event_type_count = 0
duplicate_idempotency_count = 0
delayed_ingest_over_6h_count в пределах порога
zero-results guardrail не деградирует

---

Быстрый прогон релизного контура (staging + prod)

```powershell
.\Проверки\run_preset_observability_release.ps1 `
  -StagingConn "host=<staging-host> port=5432 dbname=<db> user=<user> password=<pwd> sslmode=require" `
  -ProdConn "host=<prod-host> port=5432 dbname=<db> user=<user> password=<pwd> sslmode=require"
```

Через секреты окружения (рекомендуется, чтобы не хранить коннекты в файлах):

```powershell
$env:STAGING_DB_CONN="host=<staging-host> port=5432 dbname=<db> user=<user> password=<pwd> sslmode=require"
$env:PROD_DB_CONN="host=<prod-host> port=5432 dbname=<db> user=<user> password=<pwd> sslmode=require"
```

Сначала только staging:

```powershell
.\Проверки\run_preset_observability_release.ps1 -RunStagingOnly
```

После PASS на staging — отдельный запуск только prod:

```powershell
.\Проверки\run_preset_observability_release.ps1 -RunProdOnly
```

Скрипт выполняет:

1) `V13__catalog_preset_observability.sql`
2) `catalog_migration_parity_postcheck.sql`
3) `catalog_preset_observability_checks.sql`
4) `catalog_preset_monthly_report.sql`

---

Очистка smoke-данных после проверки (preprod)

```powershell
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" `
  "host=localhost port=5432 dbname=shoppingassistant_preprod_local user=Boss password=<pwd> sslmode=disable" `
  -f "Проверки/preprod_smoke_cleanup.sql"
```

---

Ротация засвеченного DB-пароля (staging/prod)

1) Сгенерировать новый пароль в secret manager (не в файлах репозитория).
2) Выполнить в БД:

```sql
ALTER ROLE <db_user> WITH PASSWORD '<new-strong-password>';
```

3) Обновить секреты окружений:

- `STAGING_DB_CONN`
- `PROD_DB_CONN`
- (если используется compose/.env) соответствующие `DB_PASSWORD`/`POSTGRES_PASSWORD`.

4) Перезапустить backend/workers, которые держат пул соединений.
5) Выполнить smoke-проверку подключения и один `run_preset_observability_release.ps1` на staging.
6) Только после PASS staging запускать prod.
