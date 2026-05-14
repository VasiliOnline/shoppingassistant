# Specs policy RU

TECH.SPECS_COMMON нормализует технические характеристики, но не решает публичную категорию и не содержит модельный реестр. Значение spec должно иметь evidence. Unit parser обязан хранить исходную строку, normalized numeric value и статус нормализации.

Правило: если характеристика выглядит точной, но evidence слабое, она становится candidate, а не verified value.
