# Boundary: no presets / collections

Этот пакет не проектирует пользовательские полки, пресеты, коллекции или SEO-посадочные страницы.

Он содержит только runtime-нужные identity data для поиска и нормализации популярных моделей телефонов.

Правильно:

```text
iPhone 13 -> model identity candidate/resolution
Galaxy S24 Ultra 512 -> model + storage hint
Redmi Note 14 Pro -> brand/family/model candidate
```

Неправильно для этого пакета:

```text
Shop iPhone
Лучшие смартфоны до 300€
Разлоченные телефоны рядом
Флагманы 2026
```

Это будет отдельная тема после базового покрытия веток TECH.
