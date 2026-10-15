# PROJECT_STATE: NanoForge

## Архитектура и структура
- **Проект:** NanoForge (`ru.nanodev:NanoForge`), фреймворк для создания аддонов и мини-плагинов на Bukkit/Spigot/Paper (1.16–1.21.x).
- **Стек:** Java 8 (байткод) / Java 17 (компиляция и CI), Maven, Mockito + AssertJ.
- **Модель аддонов:** YAML (`addon.yml`) -> динамическая регистрация в Bukkit `CommandMap`, `HandlerList` и виртуальный `PluginBase` в `SimplePluginManager`.
- **Хранилище:** `storage.yml` (персистентные переменные и кулдауны), `backups/` (автобэкапы).

## Текущая версия: 1.8.0
### Реализовано в 1.8.0:
1. **YAML-to-JAR компилятор (`/nano compile <аддон> [версия]`):** Автономная компиляция аддона/мини-плагина в полноценный `.jar` с манифестом, `plugin.yml` и встроенным Bootstrapper. Автоматически добавляет `depend: [<плагин>]` в `plugin.yml` при сборке аддона.
2. **Динамический загрузчик библиотек (`LibraryManager`):** Секция `libraries:` в `addon.yml` с координатами Maven Central, скачивание в `plugins/NanoForge/libraries/` и инжект в ClassLoader.
3. **Поддержка баз данных (`DatabaseManager`):** Секция `database:` (SQLite и MySQL) и действия `type: db_execute` / `type: db_query` с параметризацией и плейсхолдерами.
4. **Виртуальные подстановки плагинов:** При отсутствии стороннего целевого плагина на сервере NanoForge активирует встроенный виртуальный экземпляр этого плагина, предотвращая падение аддона.
5. **CI на push:** Включение автоматического запуска тестов и сборки GitHub Actions на каждый `push` в ветки master и main.

## Запланировано на v1.9.0:
- Расширенная работа с NBT и кастомными сущностями/предметами.
- Webhook-сервер для приёма внешних HTTP-запросов в аддоны.

## Правила сборки и окружения
- Windows 10, только cmd (`cmd /c ...`). PowerShell ЗАПРЕЩЁН.
- Сеть git: `git -c http.proxy= -c https.proxy= ...`
- CI: `.github/workflows/ci.yml` и `release.yml`.
