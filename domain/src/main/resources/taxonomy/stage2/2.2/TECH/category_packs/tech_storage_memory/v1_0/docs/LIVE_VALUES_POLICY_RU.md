# Live values policy

Модельный seed в пакете — head/repr набор, не полный глобальный реестр.

Новые модели SSD/HDD/NAS/карт памяти должны попадать в runtime candidates/live values:
1. extraction из title/description/OCR/URL;
2. проверка алиасов и route guards;
3. candidate status;
4. promotion только при повторяемости и отсутствии collisions.

Нельзя выпускать новый пакет на каждую новую модель накопителя.
