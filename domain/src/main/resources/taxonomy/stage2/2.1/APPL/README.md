# Stage 2.1 — L0 APPL (пакеты наполнения)

Содержимое пакета:
- GG_Taxonomy_Stage_2_1_L0_APPL_Packages_v1_1_RU.docx — основная спецификация (agent-ready: DoD + правила + приложения).
- browse_nodes.appl.tsv — дерево BrowseNode (root→groups→leaf-links).
- aliases.appl.tsv — seed-алиасы + блокирующие/шумовые.
- routing_rules.appl.yaml — константы скоринга + дизамбигуация + tie-breakers.
- queries_golden.appl.tsv — golden-set (ожидаемый target для каждого запроса).
- coverage.appl.json — пороги и список обязательных leaf для coverage-гейта.

Ожидаемые проверки:
1) Все node_id из coverage.appl.json присутствуют в browse_nodes.appl.tsv.
2) Для каждого обязательного leaf: >= N seed-алиасов и >= M golden-запросов (см. coverage.appl.json).
3) Прогоны конфликтов/омонимов: правила из routing_rules.appl.yaml.
