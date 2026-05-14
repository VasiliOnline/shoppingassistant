# No-guess and unit policy

Нельзя выводить exact storage/RAM/screen/battery/chipset/refresh_rate только по фото. Можно извлекать точные specs из OCR, barcode, source attributes, title, manual confirmation.

Все единицы приводятся к canonical unit, но исходное значение сохраняется. При конфликте единиц выставляется unit_normalization_status=CONFLICT или AMBIGUOUS.
