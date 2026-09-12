# Примеры аддонов

Рабочие референсы, проверенные вживую на Paper 1.20.4 (реальный клиент,
реальное открытие меню, реальный клик по кнопке — см. CHANGELOG.md 1.3.0).

Чтобы использовать: скопировать папку целиком в
`plugins/NanoForge/addons/<Имя>/` на своём сервере и выполнить `/nano reload`.

## CachesManagerHub
Аддон под плагин **CachesManager**. Демонстрирует:
- `type: call` — рефлекшн-вызов методов `reloadDatabaseOnly()` /
  `reloadMenusOnly()` / `reloadAnimationsOnly()` целевого плагина с выводом
  `{result}` в чат.
- `console` с плейсхолдером `{player}` (`execute as {player} run ...`) —
  как дёрнуть команду от лица игрока, а не консоли.
- Главное меню + отдельное подменю `info` — пример на "разное меню под
  разные задачи одного аддона".
- Условная видимость пункта через `if: permission`.

## FunConstAdmin
Аддон под плагин **FunConst**. Демонстрирует:
- `console`-action с `cooldown` условием (`if: cooldown`), чтобы не спамить
  `/funconst reload`.
- Минимальное однорядное меню.

<!-- by t.me/NanoDev_mc -->
