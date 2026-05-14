# Boundary: no full model registry in this package

Этот пакет не содержит полный реестр моделей. Он содержит:
- атрибутный контракт identity;
- head aliases для brand/family parsing;
- rules/gates для no-guess identity;
- fixtures для проверки route/identity smoke.

Полные модели телефонов, ноутбуков, камер, консолей и т.д. должны приходить через downstream category packs или runtime candidates/live values. Следующий конкретный пакет после foundation: `TECH_PHONES_IDENTITY_PACK_v1_0_RU`.
