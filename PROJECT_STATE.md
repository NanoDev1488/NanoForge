# PROJECT_STATE: NanoForge

## Архитектура и структура
- **Проект:** NanoForge (`ru.nanodev:NanoForge`), фреймворк для создания аддонов и мини-плагинов на Bukkit/Spigot/Paper (1.16–1.21.x).
- **Стек:** Java 8 (байткод) / Java 17 (компиляция и CI), Maven, Mockito + AssertJ.
- **Модель аддонов:** YAML (`addon.yml`) -> динамическая регистрация в Bukkit `CommandMap`, `HandlerList` и виртуальный `PluginBase` в `SimplePluginManager`.
- **Хранилище:** `storage.yml` (персистентные переменные и кулдауны), `backups/` (автобэкапы).

## Текущая версия: 1.7.0
### Реализовано в 1.7.0:
1. **Виртуальные плагины:** Аддоны регистрируются как `NanoForge-<AddonName>` в `SimplePluginManager` (`VirtualAddonPlugin` + `VirtualPluginRegistrar`). Появляются в `/plugins` и доступны через `Bukkit.getPluginManager().getPlugin(...)`.
2. **Тумблер в config.yml:** `register-addons-as-plugins: true` (можно отключить регистрацию).
3. **Фоновые задачи (`tasks:`):** Запуск действий по таймеру с `interval` и `delay` в тиках, автоматическая отмена при выключении/перезагрузке аддона.
4. **Команды:** Поддержка `aliases: [...]` и `tab_complete: [...]` в `commands.<name>`.
5. **Новые условия `if:`:** `gamemode`, `sneak: true/false`, `level_at_least`, `food_at_least`, `health_at_least`, `papi_compare: { placeholder, op, value }`.
6. **Новые действия `actions`:** `potion_effect`, `heal`, `feed`, `lightning`.
7. **Рефлексия:** Поддержка цепочек методов через точку в `ReflectionBridge` (`chain` / `method.call`).
8. **Источники списков GUI:** `source: addons` и `source: plugins` в меню.
9. **Фиксы:** Исправлен баг с плейсхолдерами `{maxplayers}` / `{MAXPLAYERS}` и `{online}` / `{online_players}`.

## Запланировано на v1.8.0:
- YAML-to-JAR компилятор (`addon.yml` -> полноценный `.jar` автономного плагина).
- Базы данных: поддержка SQL (MySQL/PostgreSQL) и SQLite для аддонов.
- Расширенная работа с NBT и кастомными сущностями.

## Правила сборки и окружения
- Windows 10, только cmd (`cmd /c ...`). PowerShell ЗАПРЕЩЁН.
- Сеть git: `git -c http.proxy= -c https.proxy= ...`
- CI: `.github/workflows/ci.yml` и `release.yml`.
