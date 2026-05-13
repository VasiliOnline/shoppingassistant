# Catalog Governance Admin Web

Полноценный desktop-first backoffice для governance, refresh pipeline и контроля покрытия каталога.

Папка: [project_files/admin-web](c:/Users/Asus/Desktop/Shoppingassistant/project_files/admin-web)

## Что уже умеет
- `Overview`: состояние активной ветки, быстрые действия и следующий операторский шаг.
- `Branches`: readiness и coverage по веткам.
- `Sources`: official/manual sources, source-level refresh и статус scheduler.
- `Runs`: структурированные refresh executions без копания в сырых логах.
- `Review`: approve / reject / promote кандидатов.
- `Publish`: serving/runtime publish log.
- `Runtime Lab`: published known values, aliases и runtime universe по ветке/бренду/модели.
- `Query Flow`: runtime-backed разбор реального пользовательского запроса с primary diagnosis и быстрыми operator actions.

## Как запускать
Из корня репозитория:

```powershell
cd c:\Users\Asus\Desktop\Shoppingassistant\project_files\admin-web
.\start-admin-web.ps1 -Backend http://127.0.0.1:8080
```

Или напрямую:

```powershell
python c:\Users\Asus\Desktop\Shoppingassistant\project_files\admin-web\serve_admin.py --backend http://127.0.0.1:8080 --port 4173
```

Открой:

```text
http://127.0.0.1:4173
```

## Почему здесь есть proxy-launcher
Админка работает поверх текущего Ktor backend и не требует Node/Vite.

`serve_admin.py`:
- раздаёт статические файлы;
- проксирует `/__admin/api/*` в выбранный backend;
- хранит target backend URL в `.admin-web-config.json`;
- снимает проблему с CORS при локальной и удалённой отладке.

## Быстрый рабочий цикл
1. Выбери ветку в левом меню.
2. В `Overview` посмотри readiness, blockers и latest run.
3. В `Sources` прогони проблемный source или `Refresh matched sources`.
4. В `Runs` открой failed run и посмотри summary ошибки.
5. Если появились спорные сущности, зайди в `Review`.
6. В `Runtime Lab` проверь, что published known values/aliases реально дошли до runtime.
7. В `Query Flow` прогони реальный запрос пользователя и смотри primary diagnosis: parser gap, scoped runtime gap, market empty или healthy.

## Что смотреть в первую очередь
- `Refresh failed`
  Открой `Runs` и `Publish`.
- `Ветка не READY`
  Открой `Branches` и посмотри blocking issues.
- `Фасеты выглядят странно`
  Открой `Runtime Lab` и проверь known values/aliases для бренда/модели.
- `Query не распознался`
  Открой `Query Flow`, затем одним кликом переходи в `Runtime Lab` или запускай нужный source.
- `TECH.PHONES` ещё не prod-ready
  В `Branches` смотри `Product master coverage`: там видно must-have facts по группам `Identity / Primary / Secondary / Rich`.
- `Новая модель не видна`
  Проверь `Sources -> Run`, потом `Runs`, потом `Review` и `Publish`.
