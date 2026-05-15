# Networking type delta policy

Этот patch добавляет только runtime-нужные head values, aliases и fixtures для слабых типов.
Он не расширяет дерево категорий и не добавляет пресеты/коллекции.

Главная граница: Ethernet/LAN/RJ45 аксессуары считаются network-specific и остаются в TECH.NETWORKING; USB/HDMI/power cables остаются вне ветки.
